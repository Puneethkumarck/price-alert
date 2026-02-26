# Price Alert System

A real-time price alert system for US equities. Users create alerts (e.g., "notify me when AAPL goes above $150"), the system continuously evaluates live market ticks against those alerts, and delivers notifications when thresholds are crossed.

Built with hexagonal architecture, transactional outbox pattern, 4-layer deduplication, full observability via Grafana/Prometheus/Loki/Tempo, and production-ready performance tuning (ZGC, batch Kafka consumers, Redis rate limiting, lock-free evaluation index, multi-node Kafka, PostgreSQL read replica routing).

---

## Architecture

6 Spring Boot microservices + 9 infrastructure services (up to 17 Docker containers), communicating via a 3-broker Kafka cluster with PostgreSQL for persistence and a transactional outbox for reliable event delivery. The evaluator can run as 2 instances (partitions split by Kafka consumer group rebalancing).

```
┌──────────┐    ┌───────────────┐    ┌────────────────┐    ┌──────────────────┐
│   User   │───▶│   alert-api   │───▶│   evaluator    │───▶│ notif-persister  │
│          │    │   :8080       │    │   :8082        │    │   :8083          │
└──────────┘    └───────┬───────┘    └───────┬────────┘    └────────┬─────────┘
                        │                    │                      │
                   alertapi_            evaluator_              notifications
                   outbox               outbox                  trigger_log
                        │                    │                      │
                        ▼                    ▼                      │
                  alert-changes        alert-triggers               │
                   (Kafka)              (Kafka)                     │
                        ▲                    ▲                      │
                        │                    │                      │
┌──────────┐    ┌───────────────┐            │                      │
│simulator │───▶│ tick-ingestor │────────────┘                      │
│  :8085   │    │   :8081       │                                   │
└──────────┘    └───────┬───────┘       ┌───────────────────────────┘
                   ingestor_            │
                   outbox               ▼
                        │          PostgreSQL :5432
                        ▼          (shared DB, per-service outbox tables)
                  market-ticks
                   (Kafka)


                    ┌─────────────────────────────────────┐
                    │         Monitoring Stack             │
                    │                                     │
                    │  Prometheus :9090 ◄── scrapes /actuator/prometheus
                    │  Grafana    :3000 ◄── 3 dashboards + 3 datasources
                    │  Loki       :3100 ◄── Promtail ships Docker logs
                    │  Tempo      :3200 ◄── OpenTelemetry OTLP traces
                    └─────────────────────────────────────┘
```

### Application Services

| Service | Port | Responsibility |
|---|---|---|
| **alert-api** | 8080 | REST CRUD for alerts and notifications. JWT authentication + blacklist (Redis). Per-user rate limiting (10 creates/min via Redis). Daily reset scheduler. Publishes alert lifecycle events via outbox. Custom metrics: `alerts.created/updated/deleted`. Read queries routed to PostgreSQL replica via `AbstractRoutingDataSource`. |
| **market-feed-simulator** | 8085 | Generates random-walk price ticks for 50 US equities via WebSocket. Synchronized per-session writes to prevent concurrent WebSocket errors. |
| **tick-ingestor** | 8081 | Connects to simulator WebSocket, publishes ticks to Kafka via outbox. Tuned for high throughput: 500 records/batch, 200ms poll, 64MB producer buffer. |
| **evaluator** | 8082 | Consumes alert-changes (indexes alerts in memory) and market-ticks (evaluates against thresholds). Lock-free `SymbolAlertIndex` — each Kafka partition owns a disjoint symbol set so no locking is needed. Batch consumer (up to 90% less transaction overhead). Warm-up is paginated to prevent OOM. Can run as 2 instances. Produces alert-triggers via outbox. Custom metrics: `evaluator.ticks.processed/alerts.triggered` + index gauges. |
| **notification-persister** | 8083 | Consumes alert-triggers, persists notifications and trigger logs with 4-layer idempotent deduplication. Custom metrics: `notifications.persisted/deduplicated`. |
| **common** | — | Shared module: event DTOs (AlertChange, AlertTrigger, MarketTick), ULID generator, Kafka topic constants, Jackson config. |

### Infrastructure Services

| Component | Version | Port | Purpose |
|---|---|---|---|
| Apache Kafka | 3.9.0 (KRaft) | 9092–9094 | 3-broker cluster. 3 topics: market-ticks (16 partitions, RF=3), alert-changes (8, RF=3), alert-triggers (8, RF=3). Controller quorum across all 3 brokers. |
| PostgreSQL | 17 | 5432 | Alerts, notifications, trigger logs, 9 outbox tables, Flyway migrations. Primary for writes; replica for read-only queries (warm-up, notifications). |
| Redis | 7 | 6379 | Per-user rate limiting (alert creation: 10/min via `INCR`/`EXPIRE`). JWT token blacklist for logout (`SET blacklist:{jti} EX ttl`). |
| Prometheus | latest | 9090 | Metrics collection — scrapes all 5 services at `/actuator/prometheus` every 15s |
| Grafana | latest | 3000 | Dashboards — 3 pre-built dashboards, 3 auto-provisioned datasources (admin/admin) |
| Loki + Promtail | latest | 3100 | Centralized log aggregation — structured JSON logs from all Docker containers |
| Tempo | latest | 3200 | Distributed tracing — OpenTelemetry OTLP receiver, linked from Loki log trace IDs |

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
                    .get(10s) blocking send
                    to alert-changes topic
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
                    @Transactional
                    outbox.schedule(tick, "AAPL")
                              │
                    Outbox poller (200ms, batch 500)
                    ──▶ market-ticks topic
                              │
                              ▼
                    evaluator (MarketTickConsumer)
                              │
                    @Transactional
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

Every event published to Kafka goes through the transactional outbox (namastack-outbox 1.0.0 JDBC). This guarantees at-least-once delivery with no lost events.

### How It Works

1. Domain state change + outbox record written in the **same DB transaction**
2. Outbox poller (scheduled task) reads pending records and calls the `@OutboxHandler`
3. Handler publishes to Kafka using blocking `.get(10s)` — failures propagate to outbox retry
4. Record marked `COMPLETED` on success, retried with exponential backoff on failure

### Per-Service Isolation

Each service has its own outbox tables (via JDBC `table-prefix`) to prevent cross-service handler conflicts:

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
| tick-ingestor | 200ms | 500 | 3 | 500ms | 10s |

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
│ Metrics      │    │             │    │ Outbox Handlers│
└──────────────┘    └─────────────┘    └────────────────┘
                          │
                    ZERO outward
                    dependencies
```

### Layer Rules (enforced by ArchUnit 1.3.2)

| Layer | May Depend On | Must Not Depend On |
|---|---|---|
| **Domain** | Nothing (except `@Component`/`@Service` for DI) | Application, Infrastructure, Spring Data, JPA, Kafka, `@Transactional` |
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

## Monitoring & Observability

Accessible at **http://localhost:3000** (Grafana, admin/admin).

### Grafana Dashboards

| Dashboard | Panels |
|---|---|
| **Business Metrics** | Alerts created/updated/deleted rates, evaluator tick throughput, trigger rate, index symbol/alert gauges, notification throughput, dedup rate |
| **JVM Overview** | Heap memory, GC pause time, thread count, CPU usage, HTTP request rate by endpoint, P95 latency, 5xx error rate |
| **Service Logs** | Per-service error log viewer (alert-api, evaluator, tick-ingestor, notification-persister, simulator) with level filter (ERROR/WARN/INFO/DEBUG), free-text search, error rate chart, log volume by level |

### Custom Business Metrics

| Metric | Service | Type |
|---|---|---|
| `alerts.created` | alert-api | Counter |
| `alerts.updated` | alert-api | Counter |
| `alerts.deleted` | alert-api | Counter |
| `evaluator.ticks.processed` | evaluator | Counter |
| `evaluator.alerts.triggered` | evaluator | Counter |
| `evaluator.index.symbols` | evaluator | Gauge |
| `evaluator.index.alerts` | evaluator | Gauge |
| `notifications.persisted` | notification-persister | Counter |
| `notifications.deduplicated` | notification-persister | Counter |

### Observability Stack

| Component | Purpose |
|---|---|
| **Prometheus** | Scrapes `/actuator/prometheus` from all 5 services every 15s. Application label from `spring.application.name`. |
| **Loki + Promtail** | Promtail discovers Docker containers, ships structured JSON logs (logback `docker` profile) to Loki with `service` label. |
| **Tempo** | Receives OTLP traces via HTTP (port 4318). Micrometer tracing bridge with 100% sampling in dev. |
| **Grafana** | Auto-provisions 3 datasources + 3 dashboards on startup. Loki→Tempo trace ID linking for log-to-trace navigation. |

---

## Kafka Topics

| Topic | Partitions | RF | Retention | Key | Producer | Consumer |
|---|---|---|---|---|---|---|
| `market-ticks` | 16 | 3 | 4 hours | symbol | tick-ingestor (outbox) | evaluator (concurrency=16) |
| `alert-changes` | 8 | 3 | 24 hours | symbol | alert-api (outbox) | evaluator (concurrency=8) |
| `alert-triggers` | 8 | 3 | 7 days | userId | evaluator (outbox) | notification-persister |

Symbols are keyed by `symbol` so all ticks and alert-changes for a given symbol land on the same partition → same consumer thread → no lock contention on `SymbolAlertIndex`.

---

## Database Schema

### Flyway Migrations

| Version | Description |
|---|---|
| V1 | `alerts` table + indexes (userId, symbol+status) |
| V2 | `alert_trigger_log` table + dedup unique index (alert_id, trading_date) |
| V3 | `notifications` table + idempotency_key unique constraint + indexes |
| V4 | 9 outbox tables (3 per service: record, instance, partition) with indexes |

### Entity Relationship

```
alerts (1) ───────────── (N) alert_trigger_log
  │                              │
  │ alert_id                     │ alert_id + trading_date (unique)
  │                              │
  └──────────────────── (N) notifications
                               │
                     idempotency_key (unique) = alertId:tradingDate

alertapi_outbox_record          (alert-api outbox events)
evaluator_outbox_record         (evaluator outbox events)
ingestor_outbox_record          (tick-ingestor outbox events)
```

---

## REST API

All endpoints require JWT authentication (`Authorization: Bearer <token>`) and have `@PreAuthorize("isAuthenticated()")`.

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

### Auth

| Method | Path | Description |
|---|---|---|
| `DELETE` | `/api/v1/auth/logout` | Revoke current token — writes `jti` to Redis blacklist with TTL = remaining token validity. Returns 204. Requires `jti` claim in token. |

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
- **JWT blacklist** — `DELETE /api/v1/auth/logout` writes `blacklist:{jti}` to Redis with TTL = remaining token validity; every request checks the blacklist before authenticating
- **Rate limiting** — `POST /api/v1/alerts` enforces 10 creations/minute per user via Redis `INCR`/`EXPIRE`; returns HTTP 429 on breach
- **Method-level security** — `@EnableMethodSecurity` + `@PreAuthorize("isAuthenticated()")` on every endpoint
- **Ownership enforcement** — `AlertService` verifies `alert.userId == requestor` on get/update/delete
- **5xx error sanitization** — `GlobalExceptionHandler` returns "Internal server error", never leaks internals
- **@Validated config** — All `@ConfigurationProperties` have `@Validated` + bean validation constraints (`@NotBlank`, `@Min`); invalid config fails at startup

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
| Micrometer | Prometheus registry + OpenTelemetry tracing bridge |
| Grafana | latest |
| Prometheus | latest |
| Loki + Promtail | latest |
| Tempo | latest |
| Terraform | >= 1.5.0 (kreuzwerker/docker ~> 3.0) |
| Testcontainers | 1.21.3 |
| Jackson | 3.x (via Spring Boot 4) |
| Flyway | 11.x |

---

## Tests

| Scope | Count | What's Tested |
|---|---|---|
| Unit (evaluator) | 44 | SymbolAlertIndex — AddRemove (4), Above (6), Below (4), Cross (7), MixedDirections (2), EdgeCases (5). EvaluationEngine (8). AlertIndexManager (8). |
| Unit (alert-api) | 5 | AlertService — Create (1), Get (3), Update (2), Delete (1), List (1) with BDDMockito |
| Architecture | 3 | Hexagonal layer dependencies via ArchUnit 1.3.2 |
| Integration (CRUD) | 21 | REST endpoints: Create (6), Get (3), List (5), Update (5), Delete (3), Auth (3) |
| Integration (E2E) | 8 | AlertCreationToKafka (2), NotificationRetrieval (3), AlertStatusLifecycle (2), FullHappyPath (1) |
| Integration (dedup) | 13 | Layer2ConditionalStatusUpdate (4), Layer3NotificationIdempotencyKey (4), Layer4TriggerLogDedup (4), CrossLayerDedup (1) |
| Integration (scheduler) | 4 | Daily reset TRIGGERED_TODAY → ACTIVE, no-op, multi-user, idempotent |
| **Total** | **113** | |

Integration tests use Testcontainers (PostgreSQL 17, Kafka KRaft 7.7.1, Redis 7) with singleton containers shared across all test classes. The `BaseIntegrationTest` centralises cleanup in FK-safe order (trigger_log → notifications → alerts) and wires the routing datasource correctly.

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
│       │   ├── controller/         # AlertController, NotificationController, AuthController, GlobalExceptionHandler
│       │   ├── service/            # AlertCommandHandler (rate limiting + @Transactional orchestrator)
│       │   ├── config/             # MetricsConfig, DataSourceConfig (routing datasource → replica)
│       │   ├── security/           # JwtAuthenticationFilter (+ blacklist check), JwtClaims, SecurityConfig, JwtProperties
│       │   └── job/                # DailyResetScheduler
│       ├── domain/
│       │   ├── alert/              # Alert, AlertService, AlertRepository, AlertEventPublisher
│       │   ├── notification/       # Notification, NotificationRepository
│       │   ├── triggerlog/         # AlertTriggerLog
│       │   └── exceptions/         # AlertNotFoundException, AlertNotOwnedException, RateLimitExceededException
│       └── infrastructure/
│           ├── db/                  # JPA entities, repositories, adapters, MapStruct mappers
│           └── kafka/              # AlertChangePublisher, AlertChangeOutboxHandler
│
├── evaluator/                      # Evaluation service
│   └── src/main/java/.../evaluator/
│       ├── application/config/     # KafkaConsumerConfig (concurrency=16/8, batch mode), AsyncConfig, DataSourceConfig, EvaluatorProperties, MetricsConfig
│       ├── domain/evaluation/      # EvaluationEngine, SymbolAlertIndex (lock-free), AlertIndexManager, AlertEntry
│       └── infrastructure/
│           ├── db/                  # AlertStatusUpdater (@Async), WarmUpService (paginated), AlertWarmUpRepository
│           └── kafka/              # MarketTickConsumer (batch List<MarketTick>), AlertChangeConsumer, AlertTriggerProducer, AlertTriggerOutboxHandler
│
├── notification-persister/         # Notification service
│   └── src/main/java/.../notifier/
│       ├── application/config/     # KafkaConsumerConfig, MetricsConfig
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
│       ├── application/            # WebSocketConfig, SimulatorProperties, SimulatorWebSocketHandler
│       ├── domain/                 # SymbolRegistry
│       └── infrastructure/         # TickGenerator, HeartbeatScheduler
│
├── monitoring/                     # Observability stack configs
│   ├── prometheus.yml              # Scrape config for all 5 services
│   ├── loki.yml                    # Loki local storage config
│   ├── promtail.yml                # Docker log discovery → Loki
│   ├── tempo.yml                   # OTLP receiver config
│   └── grafana/
│       ├── provisioning/
│       │   ├── datasources/        # Prometheus + Loki + Tempo auto-provisioned
│       │   └── dashboards/         # Dashboard file discovery
│       └── dashboards/
│           ├── business-metrics.json
│           ├── jvm-overview.json
│           └── service-logs.json
│
├── infra/
│   └── terraform/                  # Terraform IaC (terraform-provider-docker)
│       ├── terraform.tf            # kreuzwerker/docker ~> 3.0, backend "local"
│       ├── providers.tf            # Docker socket provider
│       ├── main.tf                 # Root module — orchestrates 4 child modules
│       ├── variables.tf            # 10 variables (jwt_secret, db_password, grafana_admin_password sensitive)
│       ├── outputs.tf              # Service URLs after apply
│       ├── environments/
│       │   └── local.tfvars        # Non-sensitive values (image tags, db names, source path)
│       ├── secrets.auto.tfvars.example  # Template for sensitive values (git-ignored in practice)
│       └── modules/
│           ├── network/            # docker_network (bridge, 172.20.0.0/16)
│           ├── infrastructure/     # kafka, kafka-init (one-shot), postgres + pgdata volume, redis
│           ├── applications/       # 5 docker_image builds + 5 docker_container resources
│           └── monitoring/         # prometheus, loki, promtail, tempo, grafana
│
├── scripts/
│   ├── launch.sh                   # Docker Compose: build, start, test, stop
│   └── terraform.sh                # Terraform: up, down, plan, status, test, clean
├── docs/
│   ├── OVERVIEW.md                 # This file
│   ├── EVALUATION_ENGINE.md        # Deep-dive: how the core evaluation engine works with examples
│   ├── PERFORMANCE_AND_SCALABILITY.md  # Redis analysis, performance improvements, scaling plan
│   ├── TESTING.md                  # Complete testing guide
│   ├── TROUBLESHOOTING.md          # 12 issues with root cause analysis
│   ├── dataflow.html               # Interactive animated 17-step visualization
│   └── Price_Alert_System.postman_collection.json  # 14 Postman requests
├── docker-compose.yml              # Full stack (up to 17 containers: 3 Kafka brokers + 2 evaluator instances)
├── Dockerfile                      # Multi-module build (docker profile for JSON logging)
├── build.gradle.kts                # Root build config
└── settings.gradle.kts             # Module declarations
```

---

## Quick Start

### Docker Compose (default)

```bash
# Build and start the full stack (13 containers)
./scripts/launch.sh up --skip-tests

# Run E2E happy path test (8 steps)
./scripts/launch.sh test

# Open monitoring
open http://localhost:3000            # Grafana (admin/admin)

# View interactive data flow animation
open docs/dataflow.html

# Import Postman collection
# File → Import → docs/Price_Alert_System.postman_collection.json

# Run all 113 unit + integration tests
./gradlew :alert-api:test :evaluator:test

# Check service status
./scripts/launch.sh status

# Tail all logs
./scripts/launch.sh logs

# Stop everything
./scripts/launch.sh down

# Stop + wipe all data
./scripts/launch.sh clean
```

### Terraform (alternative IaC)

```bash
# 1. Set up secrets (one-time per machine)
cp infra/terraform/secrets.auto.tfvars.example infra/terraform/secrets.auto.tfvars
# Edit secrets.auto.tfvars — fill in jwt_secret, db_password, grafana_admin_password

# 2. Build JARs + init + apply in one command
./scripts/terraform.sh up

# Skip Gradle build if JARs already exist
./scripts/terraform.sh up --skip-build

# Dry-run: see what Terraform would create
./scripts/terraform.sh plan

# Show running containers and service URLs
./scripts/terraform.sh status

# Run the E2E happy path test
./scripts/terraform.sh test

# Destroy (PostgreSQL pgdata volume is protected)
./scripts/terraform.sh down

# Destroy everything including pgdata volume
./scripts/terraform.sh clean
```

The Terraform stack provisions the same 13 containers as Docker Compose via the `kreuzwerker/docker` provider. Module dependency order: `network → infrastructure → applications → monitoring`.

---

## Performance & Scalability

See `docs/PERFORMANCE_AND_SCALABILITY.md` for the full analysis. Summary of what is implemented:

### P1 — Config (no code change)
| Change | Where | Effect |
|---|---|---|
| Kafka consumer concurrency | `KafkaConsumerConfig` | `market-ticks` 16 threads, `alert-changes` 8 threads — one per partition |
| Custom `@Async` thread pool | `AsyncConfig` | `core=4, max=16, queue=500` — prevents pile-up under trigger bursts |
| JVM heap + ZGC | `docker-compose.yml` / Terraform | Sub-millisecond GC pauses; per-service heap bounds |
| HikariCP pool tuning | all `application.yml` | `alert-api max=20`, others `max=10`; total ≤ 80 of PostgreSQL's 100 connections |
| Tracing sampling | all `application.yml` | `1.0` in dev, `0.01` in `production` profile (100× Tempo storage reduction) |

### P2 — Code changes
| Change | Where | Effect |
|---|---|---|
| Batch Kafka consumer | `MarketTickConsumer` | `List<MarketTick>` per transaction; `setBatchListener(true)` + `AckMode.BATCH` — up to 90% less transaction overhead |
| Redis rate limiting | `AlertCommandHandler` | `rate:alerts:{userId}` INCR/EXPIRE — 10 creates/min, HTTP 429 on breach |
| Redis JWT blacklist | `JwtAuthenticationFilter` + `AuthController` | `DELETE /api/v1/auth/logout` writes `blacklist:{jti}` EX remaining TTL |
| Paginated warm-up | `WarmUpService` | Pages of 10 000 — prevents OOM on millions of alerts at startup |

### P3 — Architecture
| Change | Where | Effect |
|---|---|---|
| Lock-free partition index | `SymbolAlertIndex` | `ReentrantReadWriteLock` removed — safe because symbol keying ensures one thread per symbol |
| Multiple evaluator instances | `docker-compose.yml` + Terraform | `evaluator` + `evaluator-2` in same consumer group; Kafka assigns disjoint partitions |
| 3-broker Kafka cluster | `docker-compose.yml` + Terraform | RF=3, min ISR=2, controller quorum across 3 nodes — no single point of failure |
| PostgreSQL read replica routing | `DataSourceConfig` | `AbstractRoutingDataSource` routes `@Transactional(readOnly=true)` to replica; `postgres-replica` in production profile |

---

## Commit History

```
06cada3 perf: implement P3 improvements + refactor tests to flat class hierarchy
4ddc94f perf: implement P2 improvements with playbook compliance
eb1557f perf: implement P1 performance improvements
29c2f11 feat: add Terraform IaC, evaluation engine docs, and performance plan
277f579 docs: update OVERVIEW.md to reflect monitoring stack and recent fixes
b5d8d23 fix: tick-ingestor Kafka config + add troubleshooting guide
29fed1d feat: add production monitoring with Grafana, Prometheus, Loki, and Tempo
3cc1237 fix: WebSocket concurrency, Kafka producer tuning, and outbox handlers
b5b6c8a feat: add launch script and comprehensive testing guide
643d3a7 fix: redesign dataflow visualization and fix particle radius bug
dce4304 docs: add interactive happy path data flow visualization
265a0ad fix: isolate outbox tables per service using JDBC table-prefix
438cb3c feat: implement transactional outbox pattern using namastack-outbox
9b11cce fix: enforce playbook hexagonal architecture and testing standards
464be1f feat: complete Phase 1 MVP with all modules, Docker Compose, and 94 passing tests
d1b0c87 feat: scaffold project with playbook-compliant hexagonal architecture
```
