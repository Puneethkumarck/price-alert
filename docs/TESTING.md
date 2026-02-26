# Testing Guide — Price Alert System

## Quick Start

```bash
# Build, start, and verify the full stack
./scripts/launch.sh up

# Run E2E happy path test
./scripts/launch.sh test

# Stop everything
./scripts/launch.sh down
```

---

## 1. Unit Tests (44 evaluator + 8 alert-api)

Fast, no containers needed. Run from project root:

```bash
# All unit tests
./gradlew :evaluator:test :alert-api:test --tests "*Test" -x integrationTest

# Evaluator domain logic only
./gradlew :evaluator:test

# AlertService BDDMockito tests only
./gradlew :alert-api:test --tests "com.pricealert.alertapi.domain.alert.AlertServiceTest"
```

**What's tested:**
- `SymbolAlertIndexTest` — TreeMap-based ABOVE/BELOW/CROSS evaluation (28 tests)
- `EvaluationEngineTest` — Trigger generation, NY timezone trading dates (8 tests)
- `AlertIndexManagerTest` — ConcurrentHashMap thread-safety (8 tests)
- `AlertServiceTest` — CRUD, ownership checks, event publishing (8 tests)

---

## 2. Architecture Tests (3 tests)

Verifies domain layer has zero outward dependencies (hexagonal compliance):

```bash
./gradlew :alert-api:test --tests "com.pricealert.alertapi.ArchitectureTest"
./gradlew :evaluator:test --tests "com.pricealert.evaluator.ArchitectureTest"
./gradlew :notification-persister:test --tests "com.pricealert.notifier.ArchitectureTest"
```

Uses ArchUnit 1.3.2. Enforces:
- Domain layer may not access Application or Infrastructure layers
- No `@Transactional`, `jakarta.persistence`, or Spring Data imports in domain

---

## 3. Integration Tests (50 tests, requires Docker)

Uses Testcontainers (PostgreSQL 17 + Kafka KRaft). Starts containers automatically:

```bash
# All integration tests
./gradlew :alert-api:test --no-build-cache --rerun

# By category
./gradlew :alert-api:test --tests "com.pricealert.alertapi.AlertControllerIntegrationTest"      # 25 CRUD tests
./gradlew :alert-api:test --tests "com.pricealert.alertapi.EndToEndHappyPathIntegrationTest"    # 8 E2E tests
./gradlew :alert-api:test --tests "com.pricealert.alertapi.DeduplicationIntegrationTest"        # 13 dedup tests
./gradlew :alert-api:test --tests "com.pricealert.alertapi.DailyResetSchedulerIntegrationTest"  # 4 scheduler tests
```

**Test infrastructure:**
- `BaseIntegrationTest` — Singleton Testcontainers (PG17 + Kafka 7.7.1 KRaft)
- `JwtTestUtil` — HMAC-SHA256 token generator for authenticated requests
- Outbox configured with `alertapi_` table prefix and 500ms poll interval in tests

---

## 4. Full Stack E2E (Docker Compose)

### Start the Stack

```bash
./scripts/launch.sh up
```

This:
1. Runs `./gradlew clean build` (skip with `--skip-build`)
2. Builds Docker images for all 5 services
3. Starts infrastructure (Kafka, PostgreSQL, Redis)
4. Starts application services (alert-api, simulator, tick-ingestor, evaluator, notification-persister)
5. Waits for all health checks to pass
6. Prints endpoints

### Run E2E Test

```bash
./scripts/launch.sh test
```

The test verifies the complete happy path:

| Step | Action | Verification |
|------|--------|-------------|
| 1 | Health check | `GET /actuator/health` returns UP |
| 2 | Create alert | `POST /api/v1/alerts` → AAPL ABOVE $150, status=ACTIVE |
| 3 | Outbox publish | `alertapi_outbox_record` status=COMPLETED |
| 4 | DB persistence | Alert exists in `alerts` table |
| 5 | Wait for trigger | Poll alert status until TRIGGERED_TODAY (up to 60s) |
| 6 | Notification | `notifications` table has row for this alert |
| 7 | API verification | `GET /api/v1/notifications` returns the trigger |
| 8 | Outbox isolation | 9 outbox tables (3 per service) verified |

### Manual Testing

Generate a JWT token:
```bash
# Included in launch.sh, or manually:
SECRET="price-alert-dev-secret-key-change-in-production"
HEADER=$(echo -n '{"alg":"HS256","typ":"JWT"}' | base64 | tr -d '=' | tr '/+' '_-')
PAYLOAD=$(echo -n '{"sub":"user_001","iat":1700000000,"exp":9999999999}' | base64 | tr -d '=' | tr '/+' '_-')
SIG=$(echo -n "${HEADER}.${PAYLOAD}" | openssl dgst -sha256 -hmac "${SECRET}" -binary | base64 | tr -d '=' | tr '/+' '_-')
TOKEN="${HEADER}.${PAYLOAD}.${SIG}"
```

#### Create Alert
```bash
curl -X POST http://localhost:8080/api/v1/alerts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"symbol":"AAPL","thresholdPrice":150.00,"direction":"ABOVE","note":"test"}'
```

#### List Alerts
```bash
curl http://localhost:8080/api/v1/alerts \
  -H "Authorization: Bearer $TOKEN"
```

#### Get Alert by ID
```bash
curl http://localhost:8080/api/v1/alerts/{alertId} \
  -H "Authorization: Bearer $TOKEN"
```

#### Update Alert
```bash
curl -X PATCH http://localhost:8080/api/v1/alerts/{alertId} \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"thresholdPrice":200.00}'
```

#### Delete Alert (soft-delete)
```bash
curl -X DELETE http://localhost:8080/api/v1/alerts/{alertId} \
  -H "Authorization: Bearer $TOKEN"
```

#### List Notifications
```bash
curl http://localhost:8080/api/v1/notifications \
  -H "Authorization: Bearer $TOKEN"
```

---

## 5. Database Inspection

Connect to PostgreSQL while stack is running:

```bash
docker compose exec postgres psql -U alerts -d price_alerts
```

Useful queries:

```sql
-- All alerts
SELECT id, symbol, status, direction, threshold_price FROM alerts;

-- Notifications with trigger prices
SELECT alert_id, symbol, trigger_price, direction, created_at FROM notifications ORDER BY created_at DESC;

-- Trigger log
SELECT alert_id, symbol, trigger_price, trading_date FROM alert_trigger_log ORDER BY triggered_at DESC;

-- Outbox status per service
SELECT 'alert-api' AS service, status, count(*) FROM alertapi_outbox_record GROUP BY status
UNION ALL
SELECT 'evaluator', status, count(*) FROM evaluator_outbox_record GROUP BY status
UNION ALL
SELECT 'ingestor', status, count(*) FROM ingestor_outbox_record GROUP BY status;

-- Flyway migration history
SELECT version, description, installed_on FROM flyway_schema_history ORDER BY installed_rank;
```

---

## 6. Kafka Inspection

```bash
# List topics
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

# Consume alert-changes events
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic alert-changes --from-beginning --max-messages 5

# Consume alert-triggers events
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic alert-triggers --from-beginning --max-messages 5

# Consume market-ticks (high volume!)
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic market-ticks --max-messages 3
```

---

## 7. Service Logs

```bash
# All services
./scripts/launch.sh logs

# Specific service
docker compose logs -f alert-api
docker compose logs -f evaluator
docker compose logs -f notification-persister
docker compose logs -f tick-ingestor

# Filter for triggers
docker compose logs evaluator | grep "fired"
docker compose logs notification-persister | grep "notification.persisted"
```

---

## 8. Deduplication Testing

The system has 4 dedup layers. To test manually:

```sql
-- Layer 2: Conditional status update (already TRIGGERED_TODAY → skip)
UPDATE alerts SET status = 'TRIGGERED_TODAY' WHERE id = '{alertId}' AND status = 'ACTIVE';
-- Returns 0 rows if already triggered

-- Layer 3: Notification idempotency key
INSERT INTO notifications (..., idempotency_key) VALUES (..., '{alertId}:2026-02-24')
ON CONFLICT (idempotency_key) DO NOTHING;
-- Second insert silently skipped

-- Layer 4: Trigger log dedup
INSERT INTO alert_trigger_log (..., alert_id, trading_date) VALUES (..., '{alertId}', '2026-02-24')
ON CONFLICT (alert_id, trading_date) DO NOTHING;
-- Second insert silently skipped
```

---

## 9. Cleanup

```bash
# Stop containers, keep volumes
./scripts/launch.sh down

# Stop containers, remove volumes and data
./scripts/launch.sh clean
```

---

## Test Summary

| Scope | Count | Duration | Command |
|-------|-------|----------|---------|
| Unit tests | 52 | ~3s | `./gradlew :evaluator:test :alert-api:test` |
| Architecture | 3 | ~2s | Included in above |
| Integration | 50 | ~15s | `./gradlew :alert-api:test` |
| E2E (Docker) | 8 steps | ~90s | `./scripts/launch.sh test` |
| **Total** | **105 + E2E** | | |

---

## Architecture

See the interactive data flow visualization:
```bash
open docs/dataflow.html
```
