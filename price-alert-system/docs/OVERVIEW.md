# Price Alert System

A real-time price alert system for US equities. Users create alerts (e.g., "notify me when AAPL goes above $150"), the system continuously evaluates live market ticks against those alerts, and delivers notifications when thresholds are crossed.

---

## Architecture

6 Spring Boot microservices in a Gradle multi-module project, communicating via Kafka with PostgreSQL for persistence and a transactional outbox for reliable event delivery.

```
                          ┌─────────────┐
                          │  alert-api  │  ← REST API (CRUD, notifications, JWT auth)
                          │   :8080     │
                          └──────┬──────┘
                                 │ alert-changes (Kafka, via outbox)
                                 ▼
┌──────────────┐    ┌─────────────────┐    ┌──────────────────────┐
│  simulator   │───▶│  tick-ingestor  │───▶│     evaluator        │
│  :8085 (WS)  │    │  :8081          │    │  :8082               │
└──────────────┘    └─────────────────┘    │  in-memory TreeMap   │
                     market-ticks (Kafka)   │  index per symbol    │
                                           └──────────┬───────────┘
                                                      │ alert-triggers (Kafka, via outbox)
                                                      ▼
                                           ┌──────────────────────┐
                                           │notification-persister │
                                           │  :8083               │
                                           │  4-layer dedup       │
                                           └──────────────────────┘
```

### Services

| Service | Port | Responsibility |
|---|---|---|
| **alert-api** | 8080 | REST CRUD for alerts and notifications. JWT authentication. Daily reset scheduler. Publishes alert lifecycle events via outbox. |
| **market-feed-simulator** | 8085 | Generates random-walk price ticks for 50 US equities via WebSocket. |
| **tick-ingestor** | 8081 | Connects to simulator WebSocket, publishes ticks to Kafka via outbox. |
| **evaluator** | 8082 | Consumes alert-changes (indexes alerts in memory) and market-ticks (evaluates against thresholds). Produces alert-triggers via outbox. |
| **notification-persister** | 8083 | Consumes alert-triggers, persists notifications and trigger logs with 4-layer idempotent deduplication. |
| **common** | — | Shared module: event DTOs (AlertChange, AlertTrigger, MarketTick), ULID generator, Kafka topic constants, Jackson config. |

### Infrastructure

| Component | Version | Purpose |
|---|---|---|
| Apache Kafka | 3.9.0 (KRaft) | Event streaming between services |
| PostgreSQL | 17 | Persistent storage for alerts, notifications, trigger logs, outbox |
| Redis | 7 | Rate limiting / caching (available, not heavily used in MVP) |

---

## Data Flow — Happy Path

### 1. Alert Creation

```
User ──POST /api/v1/alerts──▶ alert-api
                                  │
                    @Transactional │ (AlertCommandHandler)
                                  │
                    ┌──────────────┼──────────────┐
                    ▼              ▼               │
              INSERT alert   outbox.schedule()     │
              (alerts table)  (alertapi_outbox)    │
                    └──────────────┴──────────────┘
                                  │ COMMIT (atomic)
                                  ▼
                          Outbox poller picks up
                                  │
                                  ▼
                    AlertChangeOutboxHandler
                    sends to alert-changes topic
```

The alert INSERT and outbox record are written in the **same database transaction**. If the DB commits, the event is guaranteed to be published. If it rolls back, neither the alert nor the event exist.

### 2. Alert Indexing

```
alert-changes topic ──▶ evaluator (AlertChangeConsumer)
                              │
                              ▼
                    AlertIndexManager.addAlert()
                              │
                              ▼
                    SymbolAlertIndex (TreeMap)
                    AAPL: [ABOVE $150]
```

The evaluator maintains an in-memory index using `TreeMap<BigDecimal, List<AlertEntry>>` per symbol, partitioned by direction (ABOVE/BELOW/CROSS). On startup, it warm-loads all ACTIVE alerts from the database.

### 3. Market Tick Processing

```
simulator ──WebSocket──▶ tick-ingestor
                              │
                    outbox.schedule(tick, "AAPL")
                              │
                    Outbox poller ──▶ market-ticks topic
                              │
                              ▼
                    evaluator (MarketTickConsumer)
                              │
                    evaluate("AAPL", $184.48)
                              │
                    SymbolAlertIndex.evaluate()
                    ABOVE $150 ≤ $184.48 → FIRES!
                              │
                    ┌─────────┴─────────┐
                    ▼                   ▼
          outbox.schedule()     markTriggeredToday()
          (evaluator_outbox)    (alerts.status = TRIGGERED_TODAY)
```

### 4. Notification Delivery

```
alert-triggers topic ──▶ notification-persister
                              │
                    NotificationPersistenceService.persist()
                              │
                    ┌─────────┴─────────┐
                    ▼                   ▼
          INSERT notification    INSERT trigger_log
          ON CONFLICT DO NOTHING ON CONFLICT DO NOTHING
          (Layer 3: idempotency) (Layer 4: alert+date)
```

### 5. User Retrieves Notification

```
User ──GET /api/v1/notifications──▶ alert-api
                                        │
                                        ▼
                              Paginated response
                              sorted by createdAt DESC
                              {symbol: "AAPL", triggerPrice: 184.48, ...}
```

---

## Transactional Outbox Pattern

Every event published to Kafka goes through the transactional outbox (namastack-outbox JDBC). This guarantees at-least-once delivery with no lost events.

### How It Works

1. Domain state change + outbox record written in the **same DB transaction**
2. Outbox poller (scheduled task) reads pending records and calls the `@OutboxHandler`
3. Handler publishes to Kafka
4. Record marked `COMPLETED` on success, retried on failure

### Per-Service Isolation

Each service has its own outbox tables to prevent cross-service handler conflicts:

| Service | Table Prefix | Tables |
|---|---|---|
| alert-api | `alertapi_` | `alertapi_outbox_record`, `alertapi_outbox_instance`, `alertapi_outbox_partition` |
| evaluator | `evaluator_` | `evaluator_outbox_record`, `evaluator_outbox_instance`, `evaluator_outbox_partition` |
| tick-ingestor | `ingestor_` | `ingestor_outbox_record`, `ingestor_outbox_instance`, `ingestor_outbox_partition` |

### Retry Policy

All services use exponential backoff:

| Service | Poll Interval | Batch Size | Max Retries | Initial Delay | Max Delay |
|---|---|---|---|---|---|
| alert-api | 2000ms | 20 | 5 | 1000ms | 60s |
| evaluator | 1000ms | 50 | 3 | 500ms | 30s |
| tick-ingestor | 500ms | 100 | 3 | 500ms | 10s |

---

## 4-Layer Deduplication

The system uses at-least-once Kafka semantics. Duplicate events are handled by 4 layers:

| Layer | Where | Mechanism | What It Prevents |
|---|---|---|---|
| **L1** | Evaluator (in-memory) | Alert removed from index after firing | Same alert evaluated twice for same tick |
| **L2** | Evaluator → DB | `UPDATE alerts SET status = 'TRIGGERED_TODAY' WHERE status = 'ACTIVE'` | Conditional update returns 0 rows if already triggered |
| **L3** | notification-persister | `INSERT INTO notifications ... ON CONFLICT (idempotency_key) DO NOTHING` | Duplicate notification for same alert+trading_date |
| **L4** | notification-persister | `INSERT INTO alert_trigger_log ... ON CONFLICT (alert_id, trading_date) DO NOTHING` | Duplicate trigger log entry |

---

## Hexagonal Architecture

Each service follows the hexagonal (ports & adapters) pattern with 3 layers:

```
┌──────────────┐    ┌─────────────┐    ┌────────────────┐
│ APPLICATION  │───▶│   DOMAIN    │◀───│ INFRASTRUCTURE │
│              │    │             │    │                │
│ Controllers  │    │ Services    │    │ DB Adapters    │
│ Handlers     │    │ Port IFs    │    │ Kafka Adapters │
│ Jobs/Config  │    │ Records     │    │ JPA Entities   │
│ Security     │    │ Exceptions  │    │ MapStruct Maps │
└──────────────┘    └─────────────┘    └────────────────┘
                          │
                    ZERO outward
                    dependencies
```

### Layer Rules (enforced by ArchUnit)

| Layer | May Depend On | Must Not Depend On |
|---|---|---|
| **Domain** | Nothing (except `@Component`/`@Service` for DI) | Application, Infrastructure, Spring Data, JPA, Kafka |
| **Application** | Domain, Infrastructure | — |
| **Infrastructure** | Domain, Application | — |

### Port/Adapter Pairs

| Domain Port (Interface) | Infrastructure Adapter |
|---|---|
| `AlertRepository` | `AlertRepositoryAdapter` → `AlertJpaRepository` |
| `AlertEventPublisher` | `AlertChangePublisher` → `Outbox.schedule()` |
| `NotificationPort` | `NotificationRepositoryAdapter` → `NotificationJpaRepository` |
| `AlertTriggerLogPort` | `AlertTriggerLogRepositoryAdapter` → `AlertTriggerLogJpaRepository` |
| `NotificationRepository` | `NotificationRepositoryAdapter` → `NotificationJpaRepository` |

---

## Kafka Topics

| Topic | Partitions | Retention | Key | Producer | Consumer |
|---|---|---|---|---|---|
| `market-ticks` | 16 | 4 hours | symbol | tick-ingestor | evaluator |
| `alert-changes` | 8 | 24 hours | symbol | alert-api | evaluator |
| `alert-triggers` | 8 | 7 days | userId | evaluator | notification-persister |

---

## Database Schema

### Flyway Migrations

| Version | Description |
|---|---|
| V1 | `alerts` table + indexes (userId, symbol+status) |
| V2 | `alert_trigger_log` table + dedup unique index (alert_id, trading_date) |
| V3 | `notifications` table + idempotency_key unique constraint + indexes |
| V4 | 9 outbox tables (3 per service: record, instance, partition) |

### Entity Relationship

```
alerts (1) ───────────── (N) alert_trigger_log
  │                              │
  │ alert_id                     │ alert_id + trading_date (unique)
  │                              │
  └──────────────────── (N) notifications
                               │
                     idempotency_key (unique) = alertId:tradingDate
```

---

## REST API

All endpoints require JWT authentication (`Authorization: Bearer <token>`).

### Alerts

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/alerts` | Create alert |
| `GET` | `/api/v1/alerts` | List alerts (paginated, filterable by status/symbol) |
| `GET` | `/api/v1/alerts/{id}` | Get alert by ID |
| `PATCH` | `/api/v1/alerts/{id}` | Update alert (partial) |
| `DELETE` | `/api/v1/alerts/{id}` | Soft-delete alert |

#### Create Alert Request

```json
{
  "symbol": "AAPL",
  "thresholdPrice": 150.00,
  "direction": "ABOVE",
  "note": "optional note"
}
```

#### Alert Response

```json
{
  "id": "01KJ7V5959PRFXFNS39KBCBGK6",
  "userId": "user_001",
  "symbol": "AAPL",
  "thresholdPrice": 150.0,
  "direction": "ABOVE",
  "status": "ACTIVE",
  "note": "optional note",
  "createdAt": "2026-02-24T12:49:35Z",
  "updatedAt": "2026-02-24T12:49:35Z",
  "lastTriggeredAt": null,
  "lastTriggerPrice": null
}
```

### Notifications

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/notifications` | List notifications (paginated, sorted by createdAt DESC) |

#### Notification Response

```json
{
  "id": "01KJ7WDGYFKR9JH59GQBE068AN",
  "alertTriggerId": "01KJ7WDFJD7ZR12QNX2G8TZ9HV",
  "alertId": "01KJ7V5959PRFXFNS39KBCBGK6",
  "userId": "user_001",
  "symbol": "AAPL",
  "thresholdPrice": 150.0,
  "triggerPrice": 184.48,
  "direction": "ABOVE",
  "note": "optional note",
  "createdAt": "2026-02-24T13:11:34Z",
  "read": false
}
```

### Alert Statuses

| Status | Meaning |
|---|---|
| `ACTIVE` | Alert is live and will be evaluated against market ticks |
| `TRIGGERED_TODAY` | Alert fired today; will be reset to ACTIVE at next market open |
| `DELETED` | Soft-deleted by user |

### Error Response (RFC 7807 Problem Detail)

```json
{
  "status": 400,
  "title": "Bad Request",
  "detail": "Validation failed",
  "code": "VALIDATION_ERROR",
  "errors": ["thresholdPrice: must not be null"]
}
```

---

## Security

- **JWT authentication** — HMAC-SHA256 signed tokens, validated by `JwtAuthenticationFilter`
- **Method-level security** — `@PreAuthorize("isAuthenticated()")` on every controller endpoint
- **Ownership enforcement** — `AlertService` verifies `alert.userId == requestor` on get/update/delete
- **5xx error sanitization** — `GlobalExceptionHandler` never leaks internal details
- **@Validated config** — All `@ConfigurationProperties` have bean validation constraints; invalid config fails at startup

---

## Tech Stack

| Component | Version |
|---|---|
| Java | 25 |
| Spring Boot | 4.0.3 |
| Gradle | 9.0.0 |
| Apache Kafka | 3.9.0 (KRaft mode) |
| PostgreSQL | 17 |
| Redis | 7 |
| Lombok | 1.18.38 |
| MapStruct | 1.6.3 |
| ArchUnit | 1.3.2 |
| namastack-outbox | 1.0.0 (JDBC starter) |
| Testcontainers | 1.21.3 |
| Jackson | 3.x (via Spring Boot 4) |
| Flyway | 11.x |

---

## Tests

| Scope | Count | What's Tested |
|---|---|---|
| Unit (evaluator) | 44 | SymbolAlertIndex (28), EvaluationEngine (8), AlertIndexManager (8) |
| Unit (alert-api) | 8 | AlertService CRUD with BDDMockito |
| Architecture | 3 | Hexagonal layer dependencies via ArchUnit |
| Integration (CRUD) | 25 | REST endpoints, auth, validation, pagination, ownership |
| Integration (E2E) | 8 | Alert create → Kafka event → status lifecycle → notifications |
| Integration (dedup) | 13 | All 4 dedup layers (conditional update, idempotency key, trigger log) |
| Integration (scheduler) | 4 | Daily reset TRIGGERED_TODAY → ACTIVE, advisory lock, multi-user |
| **Total** | **105** | |

Integration tests use Testcontainers (PostgreSQL 17 + Kafka KRaft 7.7.1) with singleton containers shared across all test classes.

---

## Project Structure

```
price-alert-system/
├── common/                         # Shared module
│   └── src/main/java/.../common/
│       ├── event/                  # AlertChange, AlertTrigger, MarketTick, Direction, AlertStatus
│       ├── id/                     # UlidGenerator
│       ├── json/                   # JacksonConfig
│       └── kafka/                  # KafkaTopics constants
│
├── alert-api/                      # REST API service
│   └── src/main/java/.../alertapi/
│       ├── application/
│       │   ├── controller/         # AlertController, NotificationController, GlobalExceptionHandler
│       │   ├── service/            # AlertCommandHandler (@Transactional orchestrator)
│       │   ├── security/           # JwtAuthenticationFilter, SecurityConfig, JwtProperties
│       │   └── job/                # DailyResetScheduler
│       ├── domain/
│       │   ├── alert/              # Alert, AlertService, AlertRepository, AlertEventPublisher
│       │   ├── notification/       # Notification, NotificationRepository
│       │   ├── triggerlog/         # AlertTriggerLog
│       │   └── exceptions/         # AlertNotFoundException, AlertNotOwnedException
│       └── infrastructure/
│           ├── db/                  # JPA entities, repositories, adapters, MapStruct mappers
│           └── kafka/              # AlertChangePublisher, AlertChangeOutboxHandler
│
├── evaluator/                      # Evaluation service
│   └── src/main/java/.../evaluator/
│       ├── application/config/     # KafkaConsumerConfig, EvaluatorProperties
│       ├── domain/evaluation/      # EvaluationEngine, SymbolAlertIndex, AlertIndexManager, AlertEntry
│       └── infrastructure/
│           ├── db/                  # AlertStatusUpdater, WarmUpService, AlertWarmUpRepository
│           └── kafka/              # MarketTickConsumer, AlertChangeConsumer, AlertTriggerProducer, AlertTriggerOutboxHandler
│
├── notification-persister/         # Notification service
│   └── src/main/java/.../notifier/
│       ├── application/config/     # KafkaConsumerConfig
│       ├── domain/persistence/     # NotificationPersistenceService, NotificationPort, AlertTriggerLogPort
│       └── infrastructure/
│           ├── db/                  # JPA entities, repositories, adapters
│           └── kafka/              # AlertTriggerConsumer
│
├── tick-ingestor/                  # Market data ingestion
│   └── src/main/java/.../ingestor/
│       ├── application/config/     # IngestorProperties
│       └── infrastructure/
│           ├── websocket/          # SimulatorWebSocketClient
│           └── kafka/              # TickKafkaProducer, TickOutboxHandler
│
├── market-feed-simulator/          # Price tick generator
│   └── src/main/java/.../simulator/
│       ├── application/            # WebSocketConfig, SimulatorProperties
│       ├── domain/                 # SymbolRegistry
│       └── infrastructure/         # TickGenerator, HeartbeatScheduler
│
├── scripts/
│   └── launch.sh                   # Build, start, test, stop
├── docs/
│   ├── OVERVIEW.md                 # This file
│   ├── TESTING.md                  # Testing guide
│   └── dataflow.html               # Interactive animated visualization
├── docker-compose.yml              # Full stack (8 containers)
├── Dockerfile                      # Multi-module build
├── build.gradle.kts                # Root build config
└── settings.gradle.kts             # Module declarations
```

---

## Quick Start

```bash
# Build and start the full stack
./scripts/launch.sh up

# Run E2E happy path test (8 steps)
./scripts/launch.sh test

# View interactive data flow animation
open docs/dataflow.html

# Run all 105 unit + integration tests
./gradlew test

# Stop everything
./scripts/launch.sh down
```

---

## Commit History

```
b5b6c8a feat: add launch script and comprehensive testing guide
643d3a7 fix: redesign dataflow visualization and fix particle radius bug
dce4304 docs: add interactive happy path data flow visualization
265a0ad fix: isolate outbox tables per service using JDBC table-prefix
438cb3c feat: implement transactional outbox pattern using namastack-outbox
9b11cce fix: enforce playbook hexagonal architecture and testing standards
464be1f feat: complete Phase 1 MVP with all modules, Docker Compose, and 94 passing tests
d1b0c87 feat: scaffold project with playbook-compliant hexagonal architecture
```
