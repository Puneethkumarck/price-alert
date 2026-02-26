# Troubleshooting Guide — Price Alert System

Issues encountered during development and their root-cause fixes.

---

## 1. WebSocket TEXT_PARTIAL_WRITING Error

**Symptom:**
```
Error generating tick for AAPL: The remote endpoint was in state [TEXT_PARTIAL_WRITING]
which is an invalid state for called method
```

**Root cause:** 50 virtual threads (one per symbol) call `session.sendMessage()` concurrently on the same WebSocket session. The Jakarta WebSocket spec forbids concurrent writes on a single session.

**Fix:** Synchronize writes per session in `SimulatorWebSocketHandler`:
```java
private void sendSynchronized(WebSocketSession session, TextMessage message) {
    synchronized (session) {
        if (session.isOpen()) {
            session.sendMessage(message);
        }
    }
}
```

**File:** `market-feed-simulator/.../websocket/SimulatorWebSocketHandler.java`

---

## 2. Kafka TimeoutException — Expiring Records

**Symptom:**
```
TimeoutException: Expiring 12 record(s) for market-ticks-12:842527 ms has passed since batch creation
```

**Root cause:** The tick-ingestor produces ~500 ticks/sec through the outbox. The default Kafka producer buffer (32MB) fills up, and records sit in the internal buffer for 14+ minutes before expiring. The outbox poller (batch 100, 500ms interval) could only process ~200 records/sec.

**Fix:** Tuned Kafka producer and outbox throughput in `tick-ingestor/application.yml`:
```yaml
spring.kafka.producer:
  batch-size: 32768          # 32KB batch (was default 16KB)
  properties:
    linger.ms: 20            # Wait 20ms to fill batches
    buffer.memory: 67108864  # 64MB buffer (was 32MB)
    delivery.timeout.ms: 60000

namastack.outbox:
  poll-interval: 200         # Poll 5x/sec (was 500ms)
  batch-size: 500            # 500 records/poll (was 100)
```

---

## 3. Outbox Handler Async Failure Swallowing

**Symptom:** Outbox records marked as COMPLETED even when Kafka send fails. No retries triggered.

**Root cause:** The `whenComplete()` callback on `KafkaTemplate.send()` runs asynchronously. Throwing from it doesn't propagate back to the outbox framework — the handler method already returned successfully.

**Fix:** Changed all 3 outbox handlers from async to blocking:
```java
// BEFORE (broken — async exception not propagated)
kafkaTemplate.send(topic, key, payload)
    .whenComplete((result, ex) -> {
        if (ex != null) throw new RuntimeException(ex); // Never reaches outbox
    });

// AFTER (correct — blocks until Kafka confirms or fails)
kafkaTemplate.send(topic, key, payload)
    .get(10, TimeUnit.SECONDS); // Blocks, exception propagates to outbox retry
```

**Files:**
- `alert-api/.../kafka/AlertChangeOutboxHandler.java`
- `evaluator/.../kafka/AlertTriggerOutboxHandler.java`
- `tick-ingestor/.../kafka/TickOutboxHandler.java`

---

## 4. Cross-Service Outbox Handler Conflict

**Symptom:**
```
No handler with id TickOutboxHandler#handle(...) — Record marked as FAILED permanently
```

All services share the same PostgreSQL database. The alert-api outbox poller picks up tick-ingestor's outbox records and can't find the `TickOutboxHandler` class (which only exists in tick-ingestor).

**Root cause:** The namastack-outbox JPA module uses hardcoded table name `outbox_record`. All services write to and poll from the same table.

**Fix:** Switched from `namastack-outbox-starter-jpa` to `namastack-outbox-starter-jdbc` which supports `table-prefix`. Each service gets isolated outbox tables:

```yaml
# alert-api
namastack.outbox.jdbc.table-prefix: "alertapi_"
# Tables: alertapi_outbox_record, alertapi_outbox_instance, alertapi_outbox_partition

# evaluator
namastack.outbox.jdbc.table-prefix: "evaluator_"

# tick-ingestor
namastack.outbox.jdbc.table-prefix: "ingestor_"
```

Flyway migration V4 creates 9 tables (3 per service).

---

## 5. Failed to Construct Kafka Producer

**Symptom:**
```
Failed to publish MarketTick for AAPL: Failed to construct kafka producer
```
50 records fail immediately on startup, then the outbox poller stops processing entirely.

**Root cause:** Kafka producer configuration error:
```
ConfigException: delivery.timeout.ms should be equal to or larger than linger.ms + request.timeout.ms
```

The config had:
- `delivery.timeout.ms: 30000`
- `linger.ms: 20`
- `request.timeout.ms: 30000` (default)

`30000 < 20 + 30000 = 30020` — Kafka rejects the configuration.

**Fix:** Increased `delivery.timeout.ms` to `60000` in `tick-ingestor/application.yml`.

**Why the outbox poller stopped after initial failures:** The namastack outbox permanently fails records after `max-retries` (3) attempts. After the first 50 records all permanently fail, the poller has no more retryable records in the current batch window. New records accumulate as NEW but the poller threads don't recover because the Kafka producer construction failure is cached.

---

## 6. ArchUnit Unsupported Class File Version 69

**Symptom:**
```
IllegalArgumentException: Unsupported class file major version 69
```

**Root cause:** ArchUnit 1.3.0 bundles a repackaged ASM library that doesn't support Java 25 class files (version 69). The external `org.ow2.asm` dependency override doesn't help because ArchUnit uses its own shaded copy (`com.tngtech.archunit.thirdparty.org.objectweb.asm`).

**Fix:** Upgraded to ArchUnit 1.3.2 which bundles ASM with Java 25 support:
```kotlin
testImplementation("com.tngtech.archunit:archunit-junit5:1.3.2")
```

---

## 7. JdbcTemplate Cannot Infer SQL Type for java.time.Instant

**Symptom:**
```
PSQLException: Can't infer the SQL type to use for an instance of java.time.Instant
```

**Root cause:** PostgreSQL JDBC driver's `setObject()` doesn't auto-detect `java.time.Instant` or `java.time.LocalDate`.

**Fix:** Convert types explicitly:
```java
Timestamp.from(Instant.now())    // instead of Instant.now()
java.sql.Date.valueOf(localDate) // instead of LocalDate
```

---

## 8. JPA First-Level Cache Stale After JdbcTemplate Updates

**Symptom:** `alertJpaRepository.findById()` returns old data after `jdbcTemplate.update()` modified the same row.

**Root cause:** JPA's persistence context (first-level cache) doesn't know about changes made via JdbcTemplate, which bypasses JPA.

**Fix:** Clear the entity manager cache before reading:
```java
entityManager.clear(); // Evict stale entities
var fresh = alertJpaRepository.findById(id); // Now fetches from DB
```

---

## 9. Kafka Test Consumer Picks Up Events From Other Tests

**Symptom:** Test assertions fail because `records.iterator().next()` returns a record from a previously-run test, not the current one.

**Root cause:** Kafka topics persist events across test methods. Consumers with `earliest` offset see all events from all tests.

**Fix:** Either drain existing events before the test action, or filter consumed records by expected content:
```java
// Drain pre-existing events
pollForRecords(kafkaConsumer, 3);

// Filter by specific alert ID
var matching = StreamSupport.stream(allRecords.spliterator(), false)
    .filter(r -> r.value().contains("\"alert_id\":\"" + alertId + "\""))
    .findFirst()
    .orElseThrow();
```

---

## 10. @Modifying JPA Queries Need Active Transaction in Tests

**Symptom:**
```
InvalidDataAccessApiUsageException: No active transaction for update or delete query
```

**Root cause:** JPA `@Modifying` queries require an active transaction context. Direct calls from test methods (outside `@Transactional`) have no transaction.

**Fix:** Use `JdbcTemplate.update()` instead of the JPA repository for test updates. JdbcTemplate auto-commits outside JPA transaction management:
```java
jdbcTemplate.update(
    "UPDATE alerts SET status = 'TRIGGERED_TODAY' WHERE id = ? AND status = 'ACTIVE'",
    alertId);
```

---

## 11. Prometheus Cannot Scrape Evaluator/Notification-Persister

**Symptom:** Prometheus shows `evaluator` and `notification-persister` targets as `down`.

**Root cause:** These services had no web server — they only had `spring-boot-starter-data-jpa` + `spring-boot-starter-kafka`. Actuator HTTP endpoints require a web context.

**Fix:** Added `spring-boot-starter-web` to both modules:
```kotlin
implementation("org.springframework.boot:spring-boot-starter-web")
```

---

## 12. Outbox Schema Initialization Conflict with Table Prefix

**Symptom:**
```
Cannot use automatic schema initialization together with custom table prefix or schema name
```

**Root cause:** namastack-outbox's auto-create (`schema-initialization.enabled: true`) only works with default table names. When using `table-prefix`, you must manage schema yourself.

**Fix:** Disable auto-create and use Flyway:
```yaml
namastack.outbox.jdbc:
  table-prefix: "alertapi_"
  schema-initialization:
    enabled: false
```

Flyway migration V4 creates all 9 prefixed tables.

---

## 13. Evaluator OOM Kill When Loading 1M Alerts on Warm-Up

**Symptom:** Evaluator process killed by OOM on startup when the `alerts` table has ~1M ACTIVE rows:
```
java.lang.OutOfMemoryError: Java heap space
  at org.hibernate.engine.internal.StatefulPersistenceContext.addEntity(...)
```
Or silent OOM kill at container level (`exit code 137`) at `-Xmx512m`.

**Root cause:** The JPA-based warm-up opened a single long-running @Transactional(readOnly = true) session. Each findByStatus(ACTIVE, PageRequest) call loaded a full page of AlertRow JPA entities into Hibernate's
StatefulPersistenceContext identity map. Even with entityManager.clear() after each page, the entities from the current page are still in managed state during batch.forEach(...) — so at 10,000 rows/page × ~400
bytes/entity the identity map peaks at ~4MB per page, and the accumulated GC pressure across 100 pages (1M rows) causes OOM at -Xmx512m.

**Fix** — switched to JdbcTemplate.query() with fetchSize:
- JdbcTemplate uses a JDBC cursor (server-side paging via PostgreSQL's setFetchSize) — rows are fetched from the DB in batches but never accumulate in a JVM-side identity map
- Each row is mapped from ResultSet → AlertEntry record → immediately added to the index and GC-eligible
- No EntityManager, no @Transactional, no JPA persistence context at all on the warm-up path
- Heap usage stays constant at ~1 batch worth of AlertEntry records regardless of total row count

**Root cause:** `WarmUpService` used Spring Data JPA pagination (`repository.findByStatus(ACTIVE, PageRequest)`). Each page call loads `AlertRow` JPA entities into Hibernate's `StatefulPersistenceContext` identity map. Even with `entityManager.clear()` called after each page, the entities for the **current page** accumulate in managed state during `batch.forEach(...)` before `clear()` runs. At 10,000 rows/page × ~400 bytes/entity the identity map peaks per page, and across 100 pages (1M rows) the cumulative GC pressure causes OOM at `-Xmx512m`. Additionally, `@Transactional(readOnly = true)` on an `@EventListener(ApplicationReadyEvent.class)` method holds a single long-running Hibernate session open for the entire load.

**Fix:** Replaced JPA pagination with `JdbcTemplate.query()` + `setFetchSize()`. JDBC cursor streaming bypasses the persistence context entirely — rows are mapped directly from `ResultSet` to `AlertEntry` records and become immediately GC-eligible:

```java
// BEFORE (OOM at 1M rows)
@EventListener(ApplicationReadyEvent.class)
@Transactional(readOnly = true)
public void warmUp() {
    Page<AlertRow> batch;
    do {
        batch = repository.findByStatus(ACTIVE, PageRequest.of(page++, batchSize));
        batch.forEach(row -> indexManager.addAlert(...)); // row held in identity map
        entityManager.clear();                            // too late — already OOM
    } while (batch.hasNext());
}

// AFTER (constant heap regardless of row count)
@EventListener(ApplicationReadyEvent.class)
public void warmUp() {
    jdbcTemplate.setFetchSize(properties.warmup().batchSize()); // JDBC cursor batching
    jdbcTemplate.query(
        "SELECT id, user_id, symbol, threshold_price, direction, note FROM alerts WHERE status = 'ACTIVE'",
        rs -> {
            indexManager.addAlert(AlertEntry.builder()
                .alertId(rs.getString("id"))
                // ... map fields directly from ResultSet
                .build());
            // AlertEntry is immediately GC-eligible — no identity map
        });
}
```

**Why `setFetchSize` matters:** PostgreSQL JDBC driver defaults to fetching the entire result set in one round-trip (`fetchSize=0`). Setting it to e.g. `10000` enables server-side cursor batching so only one batch of raw JDBC rows is in memory at a time.

**Key takeaway:** Never use JPA/Spring Data for bulk read-only loads with unbounded row counts. Use `JdbcTemplate` with `fetchSize` for streaming. Drop `@Transactional`, `EntityManager`, and the JPA repository from the warm-up path.

**File:** `evaluator/.../infrastructure/db/WarmUpService.java`

---

## 14. ConcurrentModificationException in WarmUpService at 800K+ Alerts

**Symptom:** Evaluator crashes during warm-up at startup with `Application run failed`:
```
java.util.ConcurrentModificationException: null
  at java.util.TreeMap.callMappingFunctionWithCheck(...)
  at java.util.TreeMap.computeIfAbsent(...)
  at com.pricealert.evaluator.domain.evaluation.SymbolAlertIndex.addAlert(SymbolAlertIndex.java:21)
  at com.pricealert.evaluator.domain.evaluation.AlertIndexManager.addAlert(AlertIndexManager.java:21)
  at com.pricealert.evaluator.infrastructure.db.WarmUpService.lambda$warmUp$0(WarmUpService.java:38)
```

Container shows `Status=running` and `OOMKilled=false` — it is not an OOM. The evaluator
restarts and the load-test warmup health check times out after 300 seconds showing
`health=DOWN warmup_signals=4`.

**Root cause:** Two threads race on the same `SymbolAlertIndex` `TreeMap` simultaneously:

1. **Warm-up thread (`main`)** — `WarmUpService.warmUp()` streams rows from DB and calls
   `SymbolAlertIndex.addAlert()` → `TreeMap.computeIfAbsent()` on `aboveAlerts` / `belowAlerts`
2. **Kafka consumer thread** — `alertChangeListenerContainerFactory` and
   `marketTickListenerContainerFactory` use `autoStartup=true` (Spring default). Kafka
   listeners start concurrently with `ApplicationReadyEvent` processing. When the first
   `AlertChange` (CREATED) event arrives it also calls `addAlert()` on the same `TreeMap`
   from a different thread.

`TreeMap` is not thread-safe. `computeIfAbsent` internally iterates the tree; a concurrent
structural modification throws `ConcurrentModificationException`.

This only surfaces at 800K+ alerts because warm-up takes ~2.5 seconds at that scale — long
enough for Kafka consumers to start, join their consumer group, and receive their first
messages before warm-up finishes. At 500K (warm-up ~1.5s) the race window is narrower and
was not observed.

**Why the `SymbolAlertIndex` is not protected by a lock:** Lock-free design is intentional
for the runtime evaluation path (P3 improvement) — each Kafka partition is assigned to
exactly one consumer thread, so no two threads ever access the same `SymbolAlertIndex`
simultaneously at runtime. The race only exists during the warm-up phase when the main
thread and Kafka consumer threads overlap.

**Fix:** Set `autoStartup=false` on both Kafka listener container factories so no consumer
thread starts until warm-up explicitly starts them:

```java
// KafkaConsumerConfig.java
factory.setAutoStartup(false);   // added to both marketTick and alertChange factories
```

Then start all listeners at the end of `WarmUpService.warmUp()`, after the index is fully
built:

```java
// WarmUpService.java
@EventListener(ApplicationReadyEvent.class)
public void warmUp() {
    // ... stream all alerts into index ...
    log.info("Evaluator warm-up complete: loaded {} alerts across {} symbols", ...);

    // Index fully built — now safe to start Kafka consumers
    kafkaListenerEndpointRegistry.start();
}
```

**Files:**
- `evaluator/.../application/config/KafkaConsumerConfig.java` — add `factory.setAutoStartup(false)`
- `evaluator/.../infrastructure/db/WarmUpService.java` — inject `KafkaListenerEndpointRegistry`, call `.start()` after warm-up

**Prevention:** Whenever a service builds in-memory state from DB at startup and then
consumes a Kafka changelog for the same data, always start Kafka listeners **after** the
initial load is complete. Use `autoStartup=false` + explicit `registry.start()` rather than
relying on the `ApplicationReadyEvent` ordering, which does not guarantee warm-up completes
before Kafka container auto-start.

---

## Quick Diagnostic Commands

```bash
# Check all service statuses
docker compose ps

# Check outbox health per service
docker compose exec postgres psql -U alerts -d price_alerts -c "
SELECT 'alert-api' AS svc, status, count(*) FROM alertapi_outbox_record GROUP BY status
UNION ALL
SELECT 'evaluator', status, count(*) FROM evaluator_outbox_record GROUP BY status
UNION ALL
SELECT 'ingestor', status, count(*) FROM ingestor_outbox_record GROUP BY status;"

# Check for errors in a specific service
docker compose logs evaluator | grep ERROR | tail -10

# Check Kafka topic lag
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --all-groups

# Check Prometheus scrape targets
curl -s http://localhost:9090/api/v1/targets | python3 -m json.tool

# Full reset (wipe all data)
./scripts/launch.sh clean
```
