# Performance & Scalability Plan

## 1. Should You Use Redis in the Evaluation Engine?

**No. Redis would make the evaluator slower.**

The evaluation engine uses an in-process `TreeMap` per symbol. When a market tick arrives the lookup
is a single in-memory operation:

```
TreeMap.headMap($184.48)  →  result in ~1 µs   (in-process memory)
Redis ZRANGEBYSCORE       →  result in ~500 µs  (serialise + TCP round-trip + deserialise)
```

At 500 ticks/second that TCP overhead adds **250 ms of extra latency per second** — a bottleneck,
not an improvement. Redis only wins when the alternative is a slow DB query. Here the alternative
is a local memory read, which no network-attached store can beat.

### Where Redis *does* add real value in this system

Redis is already running and wired into `alert-api` (dependency declared, host configured), but
currently unused. Two features justify it:

| Feature | Why Redis | Implementation sketch |
|---|---|---|
| **Per-user rate limiting** on `POST /api/v1/alerts` | Shared expiring counter across API replicas | `INCR rate:alerts:{userId}` + `EXPIRE 60` |
| **JWT token revocation / blacklist** | Tokens are currently irrevocable until expiry | `SET blacklist:{jti} 1 EX {ttl}` checked in `JwtAuthenticationFilter` |

These are implemented in `alert-api` only and have zero effect on evaluator throughput.

---

## 2. Current Performance Baseline

### Theoretical throughput

| Stage | Rate | Config driver |
|---|---|---|
| Simulator → tick-ingestor | ~500 ticks/s | 50 symbols × 100 ms interval |
| tick-ingestor → Kafka | ~500 msgs/s | outbox: 200 ms poll, batch 500 |
| Kafka → evaluator | ~500 msgs/s | `market-ticks`: 16 partitions |
| Evaluator evaluation | ~500 evals/s | in-memory TreeMap |
| Evaluator → Kafka (triggers) | burst only | outbox: 1 000 ms poll, batch 50 |
| Kafka → notification-persister | burst only | `alert-triggers`: 8 partitions |

### Known gaps in the current configuration

| Gap | Location | Impact |
|---|---|---|
| No explicit Kafka consumer concurrency | evaluator, notifier | Under-utilises multi-core machines |
| No JVM heap limits in containers | all services | GC pauses unpredictable under load |
| Sampling rate 100% | all services | Tempo trace volume grows linearly with load |
| Single-node Kafka (KRaft) | docker-compose | No horizontal Kafka scaling |
| Single PostgreSQL instance | docker-compose | Connection pool contention at scale |
| No HikariCP tuning | all services | Default pool size (10) may be too small or too large |
| `@Async` uses default Spring thread pool | evaluator | core=1, unbounded queue — can pile up under burst |

---

## 3. Performance Improvement Plan

Improvements are grouped into three tiers by effort and impact.

---

### Tier 1 — Quick wins (config changes only, no code)

#### 1.1 Set Kafka consumer concurrency explicitly

**Where:** `evaluator/src/main/java/.../application/config/KafkaConsumerConfig.java`

Concurrency should equal the number of partitions the consumer group is assigned. With 16 partitions
for `market-ticks` and 8 for `alert-changes`, the optimal values are:

```java
// marketTickListenerContainerFactory
factory.setConcurrency(16);   // match market-ticks partition count

// alertChangeListenerContainerFactory
factory.setConcurrency(8);    // match alert-changes partition count
```

**Why it matters:** Without this, Spring Kafka uses a single thread per container regardless of
partition count. On a 4-core machine, 16 partitions are all consumed by one thread.
Setting concurrency to 16 allows up to 16 threads to consume in parallel — one per partition.

**Constraint:** Concurrency cannot exceed partition count. Extra threads sit idle.

---

#### 1.2 Tune the `@Async` thread pool in evaluator

**Where:** `evaluator/src/main/java/.../application/` — add a new `AsyncConfig.java`

The default Spring async executor has `corePoolSize=1` and an unbounded queue. Under a trigger
burst, `markTriggeredToday()` calls pile up in the queue instead of being executed in parallel.

```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("async-db-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

---

#### 1.3 Set explicit JVM heap limits in Docker

**Where:** `Dockerfile` or `docker-compose.yml` environment variables

Without explicit limits, the JVM uses container memory / 4 as initial heap, leading to frequent
GC when the evaluator's in-memory index grows large (thousands of alerts).

```yaml
# docker-compose.yml — evaluator service
environment:
  JAVA_TOOL_OPTIONS: "-Xms256m -Xmx512m -XX:+UseZGC -XX:+ZGenerational"
```

ZGC (available since Java 21) has sub-millisecond pause times — important for a latency-sensitive
evaluation loop. For Java 25 the generational mode is on by default but explicit flags make it clear.

Recommended heap sizes by service:

| Service | -Xms | -Xmx | Rationale |
|---|---|---|---|
| evaluator | 256m | 512m | Index size grows with alert count |
| alert-api | 128m | 256m | Short-lived request objects |
| tick-ingestor | 128m | 256m | Throughput not heap-bound |
| notification-persister | 128m | 256m | DB writes, no large in-memory state |
| market-feed-simulator | 64m | 128m | Stateless price generator |

---

#### 1.4 Tune HikariCP connection pool

**Where:** all `application.yml` files that connect to PostgreSQL

The default pool size of 10 is based on the rule of thumb for latency-bound workloads. For the
evaluator (burst of async DB writes) and alert-api (many short-lived REST requests), explicit
tuning prevents both connection starvation and over-provisioning.

```yaml
# evaluator/src/main/resources/application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10        # evaluator only does async status updates
      minimum-idle: 2
      connection-timeout: 3000
      idle-timeout: 300000
      max-lifetime: 1200000

# alert-api/src/main/resources/application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20        # REST API handles concurrent requests
      minimum-idle: 5
      connection-timeout: 3000
```

Total connections across all services must stay below PostgreSQL's `max_connections` (default 100).
With 4 services × 10–20 connections = 40–80 connections — within budget.

---

#### 1.5 Reduce tracing sampling rate in production

**Where:** all `application.yml` files

100% sampling is correct for development but generates one trace per market tick in production
(500 spans/second from the evaluator alone).

```yaml
# application.yml (all services) — production profile
management:
  tracing:
    sampling:
      probability: 0.01   # 1% sampling in production
```

Keep 100% (`1.0`) only in the `local` and `docker` profiles.

---

### Tier 2 — Code changes (medium effort, significant impact)

#### 2.1 Batch Kafka consumer in evaluator — process multiple ticks per transaction

**Where:** `evaluator/src/main/java/.../infrastructure/kafka/MarketTickConsumer.java`

Currently the consumer processes one tick at a time (`@KafkaListener` on a single `MarketTick`
object). Each tick that fires an alert results in one outbox DB write + one async DB update —
individually. Batching amortises the transaction overhead.

```java
// Current — one tick per call
@KafkaListener(...)
@Transactional
public void onMarketTick(MarketTick tick) { ... }

// Proposed — batch of ticks per call
@KafkaListener(...)
@Transactional
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

Enable batch mode in `KafkaConsumerConfig`:

```java
factory.setBatchListener(true);
factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
```

This reduces transaction overhead by up to 90% during high-throughput bursts.

---

#### 2.2 Add Redis rate limiting to alert-api

**Where:** `alert-api/src/main/java/.../application/` — new `RateLimitFilter.java` or
`AlertCommandHandler.java`

Redis is already wired. This uses the existing `spring-boot-starter-data-redis` dependency:

```java
// In AlertCommandHandler.createAlert()
String key = "rate:alerts:" + userId;
Long count = redisTemplate.opsForValue().increment(key);
if (count == 1L) {
    redisTemplate.expire(key, Duration.ofMinutes(1));
}
if (count > 10) {
    throw new RateLimitExceededException("Alert creation limit: 10 per minute");
}
```

This protects the DB from alert floods that would bloat the evaluator's in-memory index.

---

#### 2.3 Add Redis JWT blacklist to alert-api

**Where:** `alert-api/src/main/java/.../application/security/JwtAuthenticationFilter.java`

Currently tokens cannot be revoked. Add a blacklist check:

```java
// On successful token parse, check blacklist
String jti = claims.getId();   // requires jti claim in token
if (jti != null && redisTemplate.hasKey("blacklist:" + jti)) {
    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token revoked");
    return;
}
```

Add a `DELETE /api/v1/auth/logout` endpoint that writes to the blacklist with TTL = remaining
token validity.

---

#### 2.4 Warm-up in parallel batches

**Where:** `evaluator/src/main/java/.../infrastructure/db/WarmUpService.java`

The current warm-up loads all ACTIVE alerts in a single query. For large datasets (millions of
alerts) this causes a long startup delay and a large heap spike.

```java
@EventListener(ApplicationReadyEvent.class)
@Transactional(readOnly = true)
public void warmUp() {
    int page = 0;
    List<AlertWarmUpRow> batch;
    do {
        batch = repository.findByStatus(AlertStatus.ACTIVE,
                PageRequest.of(page++, properties.warmup().batchSize()));
        batch.forEach(row -> indexManager.addAlert(toEntry(row)));
    } while (batch.size() == properties.warmup().batchSize());

    log.info("Warm-up complete: {} alerts across {} symbols",
            indexManager.totalAlerts(), indexManager.symbolCount());
}
```

The batch size is already configurable (`evaluator.warmup.batch-size: 10000`). Pagination prevents
OOM on large datasets and allows the service to begin accepting Kafka events sooner.

---

### Tier 3 — Architecture changes (high effort, enables horizontal scale)

#### 3.1 Partition the in-memory index by consumer thread (eliminate lock contention)

**Current situation:** One `SymbolAlertIndex` per symbol with a `ReentrantReadWriteLock`. All 16
consumer threads contend on the write lock for the same high-volume symbols (AAPL, MSFT, TSLA).

**Proposed approach:** Each Kafka partition owns a disjoint set of symbols. Symbols are assigned
to partitions by `hash(symbol) % partitionCount`. Each consumer thread owns its partitions and
their indices — **no lock needed** because threads never share a symbol.

```
Partition 0  (AAPL, GOOG, ...)  →  consumer thread 0  →  its own SymbolAlertIndex
Partition 1  (MSFT, AMZN, ...)  →  consumer thread 1  →  its own SymbolAlertIndex
...
Partition 15 (TSLA, META, ...)  →  consumer thread 15 →  its own SymbolAlertIndex
```

This requires tick producers to key messages by `symbol` (already the case) and alert-changes
to be co-partitioned with ticks (same keying strategy — already the case).

**Impact:** Eliminates lock contention entirely. Linear scalability with partition count.

---

#### 3.2 Scale evaluator horizontally (multiple instances)

With partition-per-thread isolation (3.1), running multiple evaluator instances is safe — Kafka's
consumer group rebalancing assigns disjoint partitions to each instance.

```
evaluator-instance-1: partitions 0–7   (symbols: AAPL, GOOG, MSFT, ...)
evaluator-instance-2: partitions 8–15  (symbols: TSLA, META, AMZN, ...)
```

No shared state is needed between instances because each owns a disjoint symbol set.

**Constraint:** Number of useful instances ≤ partition count (16). Beyond that, instances sit idle.

---

#### 3.3 Multi-node Kafka cluster

The current KRaft setup is single-broker. For production:

- Minimum 3 brokers for fault tolerance
- Replication factor 3 for all topics
- Separate controller quorum from brokers

This unblocks increasing partition counts beyond what a single broker can handle, and provides
durability guarantees (current RF=1 means one broker failure = data loss).

---

#### 3.4 PostgreSQL read replica for warm-up and notification queries

The warm-up (`WarmUpService`) and notification list (`GET /api/v1/notifications`) are read-heavy
and do not need the primary. Routing these to a read replica offloads the primary for writes.

```yaml
# evaluator — read replica for warm-up
spring:
  datasource:
    url: jdbc:postgresql://postgres-replica:5432/price_alerts

# alert-api — read replica for GET /notifications
# Requires Spring's @Transactional(readOnly=true) routing to replica datasource
```

---

## 4. Prioritised Roadmap

| Priority | Change | Effort | Expected Gain |
|---|---|---|---|
| **P1** | 1.1 — Set consumer concurrency | 5 min | Full CPU utilisation for evaluation |
| **P1** | 1.2 — Tune async thread pool | 15 min | No burst queue pile-up |
| **P1** | 1.3 — JVM heap + ZGC | 10 min | Predictable GC pauses < 1 ms |
| **P2** | 1.4 — HikariCP pool sizing | 15 min | No connection starvation or waste |
| **P2** | 1.5 — Tracing sampling rate | 5 min | Reduce Tempo storage 100× in prod |
| **P2** | 2.1 — Batch Kafka consumer | 2–4 h | Up to 90% less transaction overhead |
| **P2** | 2.2 — Redis rate limiting | 2–4 h | Protect API + evaluator index from floods |
| **P2** | 2.3 — Redis JWT blacklist | 2–4 h | Token revocation capability |
| **P3** | 2.4 — Paginated warm-up | 1–2 h | Handles millions of alerts at startup |
| **P3** | 3.1 — Lock-free partition index | 1–2 days | Linear horizontal scale for evaluation |
| **P3** | 3.2 — Multiple evaluator instances | 1 day | Horizontal scale (requires 3.1) |
| **P3** | 3.3 — Multi-node Kafka | 1 day | Production durability + partition headroom |
| **P3** | 3.4 — PG read replica | 1 day | Offload reads from primary |

---

## 5. What NOT to Do

| Suggestion | Why to avoid |
|---|---|
| Redis for the evaluation index | Adds 500–5000× latency per tick vs in-memory TreeMap |
| Database per-tick alert lookup | ~1–5 ms per query × 500 ticks/s = DB saturated immediately |
| Increase `market-ticks` partitions beyond evaluator thread count | Idle partitions, no gain |
| 100% trace sampling in production | One span per tick = 500 spans/s = Tempo storage explosion |
| Disable outbox pattern for "speed" | Loses at-least-once delivery guarantee — silent data loss |
