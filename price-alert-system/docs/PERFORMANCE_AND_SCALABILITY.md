# Performance & Scalability

All P1, P2, and P3 improvements from the original plan are implemented. This document describes what was done, why, and where to find it.

---

## 1. Why Not Redis for the Evaluation Engine?

The evaluation engine uses an in-process `TreeMap` per symbol. When a market tick arrives the lookup is a single in-memory operation:

```
TreeMap.headMap($184.48)  →  result in ~1 µs   (in-process memory)
Redis ZRANGEBYSCORE       →  result in ~500 µs  (serialise + TCP round-trip + deserialise)
```

At 500 ticks/second that TCP overhead adds **250 ms of extra latency per second** — a bottleneck, not an improvement. Redis only wins when the alternative is a slow DB query. Here the alternative is a local memory read, which no network-attached store can beat.

### Where Redis adds real value

Redis is wired into `alert-api` via `spring-boot-starter-data-redis`. Two features use it:

| Feature | Mechanism | File |
|---|---|---|
| Per-user rate limiting on `POST /api/v1/alerts` | `INCR rate:alerts:{userId}` + `EXPIRE 60` — 10 creates/min, HTTP 429 on breach | `AlertCommandHandler` |
| JWT token revocation | `SET blacklist:{jti} 1 EX {ttl}` checked on every authenticated request | `JwtAuthenticationFilter`, `AuthController` |

Both are in `alert-api` only and have zero effect on evaluator throughput.

---

## 2. Theoretical Throughput

| Stage | Rate | Config driver |
|---|---|---|
| Simulator → tick-ingestor | ~500 ticks/s | 50 symbols × 100 ms interval |
| tick-ingestor → Kafka | ~500 msgs/s | outbox: 200 ms poll, batch 500 |
| Kafka → evaluator | ~500 msgs/s | `market-ticks`: 16 partitions, 2 consumer instances |
| Evaluator evaluation | ~500 evals/s | lock-free in-memory TreeMap, 16 threads |
| Evaluator → Kafka (triggers) | burst only | outbox: 1 000 ms poll, batch 50 |
| Kafka → notification-persister | burst only | `alert-triggers`: 8 partitions, RF=3 |

---

## 3. P1 — Config Improvements

### 1.1 Kafka consumer concurrency

**File:** `evaluator/src/main/java/.../application/config/KafkaConsumerConfig.java`

```java
// market-ticks: 16 threads — one per partition
factory.setConcurrency(16);
factory.setBatchListener(true);
factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);

// alert-changes: 8 threads — one per partition
factory.setConcurrency(8);
```

Without explicit concurrency, Spring Kafka uses a single thread per container regardless of partition count. Setting concurrency to 16 allows up to 16 threads to consume in parallel.

### 1.2 Custom `@Async` thread pool

**File:** `evaluator/src/main/java/.../application/config/AsyncConfig.java`

Replaced Spring's default executor (`core=1`, unbounded queue) with:

```java
executor.setCorePoolSize(4);
executor.setMaxPoolSize(16);
executor.setQueueCapacity(500);
executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
```

Under a trigger burst, `markTriggeredToday()` calls now execute in parallel (bounded by the HikariCP pool) instead of queuing behind a single thread.

### 1.3 JVM heap + ZGC

**File:** `docker-compose.yml` and `infra/terraform/modules/applications/main.tf`

```yaml
JAVA_TOOL_OPTIONS: "-Xms256m -Xmx512m -XX:+UseZGC -XX:+ZGenerational"
```

ZGC (Java 21+) has sub-millisecond pause times — critical for a latency-sensitive evaluation loop. Heap sizes per service:

| Service | -Xms | -Xmx |
|---|---|---|
| evaluator | 256m | 512m |
| alert-api | 128m | 256m |
| tick-ingestor | 128m | 256m |
| notification-persister | 128m | 256m |
| market-feed-simulator | 64m | 128m |

### 1.4 HikariCP connection pool

**Files:** all `application.yml` files (primary datasource via `spring.datasource.hikari.*`)

| Service | max pool | min idle | Rationale |
|---|---|---|---|
| alert-api | 20 | 5 | Concurrent REST requests |
| evaluator | 10 | 2 | Async status updates |
| tick-ingestor | 10 | 2 | Outbox writes |
| notification-persister | 10 | 2 | Notification inserts |

Total across all services: ≤ 80 connections — within PostgreSQL's default `max_connections=100`. Each service also has a replica pool (max=10 evaluator, max=10 alert-api) — see P3.4.

### 1.5 Tracing sampling rate

**Files:** all `application.yml` files (production profile)

```yaml
---
spring:
  config:
    activate:
      on-profile: production
management:
  tracing:
    sampling:
      probability: 0.01   # 1% in production
```

Default stays `1.0` for local/docker development. Activating the `production` profile reduces Tempo trace volume 100×.

---

## 4. P2 — Code Improvements

### 2.1 Batch Kafka consumer

**File:** `evaluator/src/main/java/.../infrastructure/kafka/MarketTickConsumer.java`

```java
// Before: one tick per transaction
public void onMarketTick(MarketTick tick) { ... }

// After: batch of ticks per transaction
public void onMarketTicks(List<MarketTick> ticks) {
    for (var tick : ticks) {
        var triggers = evaluationEngine.evaluate(tick.symbol(), tick.price(), tick.timestamp());
        triggers.forEach(trigger -> {
            triggerProducer.send(trigger);
            statusUpdater.markTriggeredToday(trigger.alertId());
        });
        ticksProcessedCounter.increment();
    }
}
```

Combined with `setBatchListener(true)` and `AckMode.BATCH` in `KafkaConsumerConfig`, this reduces transaction overhead by up to 90% during high-throughput bursts — one transaction per batch instead of one per tick.

### 2.2 Redis rate limiting

**File:** `alert-api/src/main/java/.../application/service/AlertCommandHandler.java`

```java
private void checkRateLimit(String userId) {
    var key = "rate:alerts:" + userId;
    var count = redisTemplate.opsForValue().increment(key);
    if (count == 1L) {
        redisTemplate.expire(key, RATE_LIMIT_WINDOW);  // 1 minute
    }
    if (count > RATE_LIMIT_MAX) {  // 10
        throw RateLimitExceededException.alertCreationLimit(RATE_LIMIT_MAX);
    }
}
```

`RateLimitExceededException` is handled by `GlobalExceptionHandler` → HTTP 429.

### 2.3 Redis JWT blacklist

**File:** `alert-api/src/main/java/.../application/security/JwtAuthenticationFilter.java`

```java
var jti = claims.jti();
if (jti != null && Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:" + jti))) {
    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token revoked");
    return;
}
```

**File:** `alert-api/src/main/java/.../application/controller/auth/AuthController.java`

`DELETE /api/v1/auth/logout` extracts the `jti` and `exp` claims from the bearer token, then writes:

```
SET blacklist:{jti} 1 EX {remaining_seconds}
```

TTL is set to remaining token validity so the key auto-expires when the token would have expired anyway — no manual cleanup needed.

### 2.4 Paginated warm-up

**File:** `evaluator/src/main/java/.../infrastructure/db/WarmUpService.java`

```java
Page<AlertRow> batch;
do {
    batch = repository.findByStatus(AlertStatus.ACTIVE, PageRequest.of(page++, batchSize));
    batch.forEach(row -> indexManager.addAlert(toEntry(row)));
} while (batch.hasNext());
```

Batch size is configurable (`evaluator.warmup.batch-size: 10000`). Replaces the previous single-query load that caused OOM on large datasets and blocked startup.

---

## 5. P3 — Architecture Improvements

### 3.1 Lock-free partition index

**File:** `evaluator/src/main/java/.../domain/evaluation/SymbolAlertIndex.java`

The `ReentrantReadWriteLock` was removed entirely. This is safe because:

1. Market ticks are keyed by `symbol` → all ticks for a symbol land on the same Kafka partition
2. Alert-changes use the same keying strategy → co-partitioned with ticks
3. `setConcurrency(16)` assigns one consumer thread per partition
4. Result: each `SymbolAlertIndex` is accessed by exactly one thread — no contention is possible

```
Partition 0  (AAPL, GOOG, ...)  →  thread 0  →  its own SymbolAlertIndex (no lock)
Partition 1  (MSFT, AMZN, ...)  →  thread 1  →  its own SymbolAlertIndex (no lock)
...
Partition 15 (TSLA, META, ...)  →  thread 15 →  its own SymbolAlertIndex (no lock)
```

The warm-up path (single-threaded, before consumers start) still writes through `AlertIndexManager`'s `ConcurrentHashMap` which provides the necessary safety for that phase.

### 3.2 Multiple evaluator instances

**Files:** `docker-compose.yml`, `infra/terraform/modules/applications/main.tf`

`evaluator` and `evaluator-2` both join the `evaluator-ticks` consumer group. Kafka's cooperative sticky assignor distributes partitions:

```
evaluator:   partitions 0–7   (symbols: AAPL, GOOG, MSFT, ...)
evaluator-2: partitions 8–15  (symbols: TSLA, META, AMZN, ...)
```

No shared state is needed between instances — each owns a disjoint symbol set. Maximum useful instances = partition count (16).

### 3.3 3-broker Kafka cluster

**Files:** `docker-compose.yml`, `infra/terraform/modules/infrastructure/main.tf`

| Broker | Internal | External |
|---|---|---|
| kafka | kafka:19092 | localhost:9092 |
| kafka-2 | kafka-2:19092 | localhost:9093 |
| kafka-3 | kafka-3:19092 | localhost:9094 |

Controller quorum: `1@kafka:9093,2@kafka-2:9093,3@kafka-3:9093`

All topics created with `replication-factor=3`, `min.insync.replicas=2`. Single-broker failure no longer causes data loss or unavailability. All application services use the full bootstrap list:

```
kafka:19092,kafka-2:19092,kafka-3:19092
```

### 3.4 PostgreSQL read replica routing

**Files:** `alert-api/src/main/java/.../application/config/DataSourceConfig.java`, `evaluator/.../config/DataSourceConfig.java`

`AbstractRoutingDataSource` inspects the current transaction's read-only flag and routes accordingly:

```java
protected Object determineCurrentLookupKey() {
    return TransactionSynchronizationManager.isCurrentTransactionReadOnly()
            ? "replica"
            : "primary";
}
```

Operations that benefit:
- `WarmUpService.warmUp()` — `@Transactional(readOnly=true)` → replica
- `NotificationController.listNotifications()` → `NotificationRepositoryAdapter.findByUserId()` — `@Transactional(readOnly=true)` → replica
- All writes (alert creation, status updates, notification inserts) → primary

In local/docker the replica URL defaults to the primary (no actual replica running). In production, set `spring.datasource.replica.hikari.jdbc-url=jdbc:postgresql://postgres-replica:5432/price_alerts` or override via the `production` profile.

---

## 6. Roadmap Status

| Priority | Change | Status |
|---|---|---|
| **P1** | 1.1 — Kafka consumer concurrency | ✅ Done |
| **P1** | 1.2 — Async thread pool | ✅ Done |
| **P1** | 1.3 — JVM heap + ZGC | ✅ Done |
| **P2** | 1.4 — HikariCP pool sizing | ✅ Done |
| **P2** | 1.5 — Tracing sampling rate | ✅ Done |
| **P2** | 2.1 — Batch Kafka consumer | ✅ Done |
| **P2** | 2.2 — Redis rate limiting | ✅ Done |
| **P2** | 2.3 — Redis JWT blacklist | ✅ Done |
| **P3** | 2.4 — Paginated warm-up | ✅ Done |
| **P3** | 3.1 — Lock-free partition index | ✅ Done |
| **P3** | 3.2 — Multiple evaluator instances | ✅ Done |
| **P3** | 3.3 — Multi-node Kafka | ✅ Done |
| **P3** | 3.4 — PG read replica routing | ✅ Done |

---

## 7. What NOT to Do

| Suggestion | Why to avoid |
|---|---|
| Redis for the evaluation index | Adds 500–5000× latency per tick vs in-memory TreeMap |
| Database per-tick alert lookup | ~1–5 ms per query × 500 ticks/s = DB saturated immediately |
| Increase `market-ticks` partitions beyond evaluator thread count | Idle partitions, no gain |
| 100% trace sampling in production | One span per tick = 500 spans/s = Tempo storage explosion |
| Disable outbox pattern for "speed" | Loses at-least-once delivery guarantee — silent data loss |
| Add locking back to `SymbolAlertIndex` | Defeats the purpose of symbol-keyed partitioning |
