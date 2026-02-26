# Scaling Journey — Road to 1M Alerts

How the Price Alert System was tuned from a working MVP to handling 1M concurrent active
alerts with zero OOM kills, zero Kafka consumer lag, and ≤ 2ms GC pauses.

---

## Overview

The system was built and tested incrementally across 6 load levels. Each level exposed a
new bottleneck that was diagnosed, fixed, and validated before moving to the next level.

| Level | Alerts | Outcome | Primary issue found |
|-------|--------|---------|---------------------|
| L1    | 10K    | ✅ Pass | Metrics collection broken (localhost ports not exposed) |
| L2    | 100K   | ✅ Pass | Clean baseline — no issues |
| L3    | 500K   | ✅ Pass | Notification-persister first shows stress |
| L4a   | 800K   | ❌ Crash | `ConcurrentModificationException` in warm-up |
| L4b   | 800K   | ❌ Crash | `OutOfMemoryError` during JDBC warm-up |
| L4c   | 800K   | ✅ Pass | First clean 800K run — notification backlog high |
| L4d   | 800K   | ✅ Pass | Notification concurrency fix validated |
| L5a   | 1M     | ✅ Pass | Design target reached — outbox drain lag |
| L5b   | 1M     | ✅ Pass | Full tuning applied — peak 7,663 triggers/sec |

---

## The Fix Timeline

### Fix 1 — Metrics collection: `curl localhost:PORT` fails for internal services

**Discovered at:** L1 (10K)

**Symptom:** All heap metrics showed `N/A` for evaluator, notification-persister, and
tick-ingestor. Only alert-api returned real values.

**Root cause:** Only `alert-api` has a host port mapping (`8080→8080`). The other services
are container-internal only — `curl localhost:8082` from the host returns nothing.

**Fix:**
```bash
# Before
curl -sf "http://localhost:${port}/actuator/metrics/..."

# After
docker exec "$svc" wget -qO- "http://localhost:${port}/actuator/metrics/..."
```

**Learning:** When deploying services without host port mappings, always use
`docker exec <container> wget` or reach them via their Docker network IP. Never assume
`localhost:PORT` is reachable from the host just because the service is running.

---

### Fix 2 — `terraform clean` leaves DB intact

**Discovered at:** Between test runs

**Symptom:** `./scripts/terraform.sh clean` printed "pgdata volume removed" but the
volume still existed after the command.

**Root cause:** `terraform destroy` hits `prevent_destroy = true` on `docker_volume.pgdata`
and aborts mid-plan. The postgres container may survive (still referencing the volume),
causing `docker volume rm` to fail silently.

**Fix:** Explicitly remove the postgres container first to release Docker's volume reference,
then run `terraform destroy`, then `docker volume rm`:
```bash
docker rm -f postgres
terraform destroy -auto-approve || true
docker volume rm price-alert-pgdata
```

**Learning:** `prevent_destroy` in Terraform blocks the entire destroy plan — it does not
just skip that one resource. When you need to override it, remove the dependent container
manually first. Never rely on `|| true` to mask Terraform errors silently.

---

### Fix 3 — Warm-up health check always reports DOWN

**Discovered at:** L1 (10K) — Phase 2 timed out at 300s

**Symptom:** `wait_for_warmup` loop showed `health=DOWN` throughout even though the
evaluator was running and warm-up had completed. The script timed out and proceeded anyway.

**Root cause:** Same as Fix 1 — `curl localhost:8082/actuator/health` was called from the
host but evaluator has no host port mapping.

**Fix:** `docker exec evaluator wget -qO- http://localhost:8082/actuator/health`

**Learning:** Health check scripts that curl internal services must always go through
`docker exec`. A service that is internally healthy but has no host port mapping will
appear DOWN to any host-level health check.

---

### Fix 4 — `ConcurrentModificationException` in WarmUpService at 800K+

**Discovered at:** L4a (800K) — first crash

**Symptom:**
```
java.util.ConcurrentModificationException
  at java.util.TreeMap.computeIfAbsent(...)
  at SymbolAlertIndex.addAlert(SymbolAlertIndex.java:21)
  at WarmUpService.lambda$warmUp$0(WarmUpService.java:38)
```
Evaluator crashed during warm-up, restarted repeatedly.

**Root cause:** Spring Kafka listener containers start with `autoStartup=true` by default.
At 800K alerts the warm-up takes ~2 seconds — long enough for Kafka consumers to start,
join the consumer group, and receive `AlertChange` events. Both the warm-up `main` thread
and Kafka consumer threads then call `TreeMap.computeIfAbsent()` on the same
`SymbolAlertIndex` simultaneously. `TreeMap` is not thread-safe → crash.

This race did not appear below 800K because warm-up completed in < 1.5s — too fast for
Kafka consumers to receive their first messages.

**Fix:**
```java
// KafkaConsumerConfig.java — both factories
factory.setAutoStartup(false);

// WarmUpService.java — start consumers only after index is fully built
@EventListener(ApplicationReadyEvent.class)
public void warmUp() {
    // ... stream all alerts into index ...
    log.info("Warm-up complete: loaded {} alerts", count.get());

    // Index fully built — now safe to start Kafka consumers
    kafkaListenerEndpointRegistry.start();
}
```

**Learning:** Never rely on `ApplicationReadyEvent` ordering to separate warm-up from Kafka
consumer startup. Spring fires `ApplicationReadyEvent` and starts Kafka containers
concurrently. If a service builds in-memory state from DB and also consumes a Kafka
changelog for the same data, always use `autoStartup=false` and start consumers explicitly
after the load is complete.

---

### Fix 5 — `OutOfMemoryError` during JDBC warm-up at 800K

**Discovered at:** L4b (800K) — second crash after Fix 4 was applied

**Symptom:**
```
java.lang.OutOfMemoryError: Java heap space
  at org.postgresql.core.Encoding.decode(...)
  at org.postgresql.jdbc.PgResultSet.getString(...)
```
Evaluator crashed during warm-up JDBC streaming, not during index build.

**Root cause:** Evaluator was running at `-Xmx512m`. At 800K rows, the PostgreSQL JDBC
driver decodes all 6 string fields per row into heap. 800K × 6 strings + the growing
`AlertEntry` index exhausted 512m.

**Fix:** Raised evaluator `-Xmx` from `512m` to `1024m` in Terraform:
```hcl
"JAVA_TOOL_OPTIONS=-Xms256m -Xmx1024m -XX:+UseZGC -XX:+ZGenerational"
```

**Learning:** Size heap based on the peak in-memory data structure, not the service's
idle footprint. At 1M alerts each `AlertEntry` holds 5 fields (~200–300 bytes on heap)
= ~300 MB for the index alone. Add JDBC decode buffers, Kafka consumer buffers, and JVM
overhead → budget at least 1GB for a service holding 500K–1M domain objects.

**Corollary:** ZGC reports `jvm.memory.max` as **2× `-Xmx`** in the actuator — it
pre-maps a virtual address space of 2× the configured limit. This is virtual memory, not
physical RAM. Always verify against the configured `-Xmx` and OS-level container memory
(`docker stats`), not the actuator metric.

---

### Fix 6 — Notification-persister single-threaded consumer

**Discovered at:** L3 (500K) — first signs; confirmed at L4c (800K)

**Symptom:** Notification-persister CPU at 29.76% at 500K, 15.11% at 800K, yet a backlog
of 93K–158K notifications accumulated by test end. Trigger rate (2,349/s) far exceeded
notification rate (~200/s).

**Root cause:** `alertTriggerListenerContainerFactory` had no `setConcurrency()` call —
defaulting to **1 consumer thread** for 8 `alert-triggers` partitions. One thread
processing one `AlertTrigger` at a time: deserialise → DB insert → ack → repeat = ~200/s
maximum throughput.

**Fix:**
```java
// KafkaConsumerConfig.java — notification-persister
factory.setConcurrency(8);  // one thread per alert-triggers partition
```

**Result:** Sustained notification throughput improved from ~200/s to ~600/s (3×). Always
set `setConcurrency()` to match partition count — leaving it at default 1 wastes all
partitions beyond the first.

**Learning:** Spring Kafka `ConcurrentKafkaListenerContainerFactory` defaults to 1 thread
regardless of partition count. The concurrency must be set explicitly. Rule of thumb:
`concurrency = min(partition_count, desired_parallelism)`. Never leave it at default for
any high-throughput consumer.

---

### Fix 7 — Second notification-persister instance

**Discovered at:** L4d (800K) and L5a (1M)

**Symptom:** After Fix 6, notification-persister CPU hit 29.76% at 500K and climbed toward
48% at 1M with 963/s throughput. A single instance with 8 threads was approaching its DB
connection pool limit (max-pool-size=10).

**Fix:** Added `notification-persister-2` to Terraform — same image, same config, same
consumer group (`notification-persister-group`). Kafka automatically distributes the 8
`alert-triggers` partitions across both instances (4 each).

```hcl
resource "docker_container" "notification_persister_2" {
  name  = "notification-persister-2"
  image = docker_image.notification_persister.image_id
  env   = [ ... same as notification_persister ... ]
}
```

**Result:** Combined throughput reached 963/s at t=120s and still climbing. Both instances
active (24% + 10% CPU).

**Learning:** Consumer group scaling is free in Kafka — add instances up to partition count
with zero code changes. The constraint is always partition count: `alert-triggers` has 8
partitions → maximum useful instances = 8. Plan partition counts at topic creation time
based on expected consumer parallelism, not current load.

---

### Fix 8 — Evaluator outbox poll interval too conservative

**Discovered at:** L5a (1M) — notification pipeline lag of 108K at test end

**Symptom:** Even with two notification-persister instances, the notification backlog at
t=120s was 108K. Post-test the outbox had 122K `NEW` records still queued. The persisters
were not the bottleneck — the evaluator outbox drain rate was.

**Root cause:** Evaluator outbox was configured at `poll-interval: 1000ms, batch-size: 50`
= **50 records/sec per instance = 100/sec total**. Peak trigger rates of 4,000–7,000/s
overwhelm this by 40–70×. Compare with `tick-ingestor` outbox: `poll-interval: 200ms,
batch-size: 500` = 2,500/sec.

**Fix:**
```yaml
# evaluator/src/main/resources/application.yml
namastack.outbox:
  poll-interval: 200    # was 1000
  batch-size: 500       # was 50
```

**Result at L5b (1M):**
- Peak trigger rate: **7,663/s** (up from 4,361/s) — faster outbox drain means triggers
  previously queued are published to Kafka within the same second
- Notifications at t=120s: **77,655** (up from 26,664, +191%)
- Evaluator CPU: 144% (1.45 cores) — expected, 5× more polling
- Kafka consumer lag: **0** throughout

**Learning:** The outbox poll interval and batch size are throughput multipliers, not just
latency knobs. `poll-interval × batch-size` determines maximum drain rate. Always set
outbox config to match the expected event rate of the service, not a safe conservative
default. For burst-heavy services (evaluators, tick processors), `poll-interval=200ms` and
`batch-size=500` is a good starting point.

---

## Configuration Before vs After

### Evaluator

| Setting | MVP default | Final |
|---------|------------|-------|
| `-Xmx` | 512m | **1024m** |
| Kafka `autoStartup` | true | **false** |
| Outbox `poll-interval` | 1000ms | **200ms** |
| Outbox `batch-size` | 50 | **500** |
| Kafka consumer `concurrency` (market-ticks) | 16 | 16 (unchanged) |

### Notification-persister

| Setting | MVP default | Final |
|---------|------------|-------|
| `-Xmx` | 256m | **512m** |
| Kafka `concurrency` | 1 (default) | **8** |
| Instances | 1 | **2** |

### Infrastructure

| Setting | MVP default | Final |
|---------|------------|-------|
| `SPRING_PROFILES_ACTIVE` | not set | **docker** (all services) |
| Kafka broker port (kafka-2 external) | 9093 | **9095** (conflict fix) |

---

## Key Learnings for Future Projects

### 1. Never use JPA for bulk warm-up loads

JPA's `StatefulPersistenceContext` identity map accumulates every entity in memory for the
duration of the transaction. At 1M rows this causes OOM regardless of `entityManager.clear()`
timing.

**Rule:** Use `JdbcTemplate.query()` with `setFetchSize()` for any read-only bulk load.
Rows map to value objects and are immediately GC-eligible. Heap usage stays constant
regardless of row count.

```java
jdbcTemplate.setFetchSize(10000);  // server-side cursor batching
jdbcTemplate.query(SQL, rs -> {
    index.add(mapRow(rs));  // immediately GC-eligible
});
```

### 2. Always use `autoStartup=false` when warm-up precedes Kafka consumption

If a service loads state from DB at startup and also consumes a Kafka changelog for the
same data, the warm-up and Kafka consumer startup race. The warm-up writes to non-thread-safe
structures (TreeMap, HashMap); Kafka consumer threads write to the same structures.

**Rule:** Set `factory.setAutoStartup(false)` on all container factories. Start them
explicitly with `kafkaListenerEndpointRegistry.start()` as the last line of the warm-up
method.

### 3. Always set Kafka consumer concurrency explicitly

Spring Kafka defaults to 1 consumer thread regardless of partition count. A topic with 8
partitions and `concurrency=1` wastes 7 partitions — only 1/8th of available throughput.

**Rule:** Always call `factory.setConcurrency(n)` where `n = partition_count` for
throughput-sensitive consumers. Document the partition count next to the concurrency setting
so they stay in sync.

### 4. Size heap against peak in-memory data, not idle footprint

A service that holds 1M domain objects needs heap sized for those objects, not for its
idle state. Idle heap usage is misleading — it does not reflect warm load.

**Rule:** Estimate heap as: `object_count × avg_object_size × 2 (GC headroom) + 256m base`.
For 500K `AlertEntry` objects (~250 bytes each): 500K × 250B × 2 = 250MB + 256MB base =
~500MB minimum. Round up to the next power of 2 → 512m. For 1M objects → 1024m.

### 5. ZGC reports 2× `-Xmx` in actuator — use OS stats for truth

ZGC pre-maps virtual address space at 2× the configured `-Xmx`. The actuator
`jvm.memory.max` metric reflects this virtual reservation, not physical RAM limit.

**Rule:** Always verify heap against `docker stats` container MEM and the configured
`-Xmx`, not the actuator metric. When documenting heap utilisation, calculate against
the configured `-Xmx`.

### 6. Tune outbox poll interval to match event rate

The outbox `poll-interval × batch-size` is the throughput ceiling for downstream pipeline
stages. A conservative default (1000ms / 50 = 50/sec) will cause growing backlogs under
any significant burst load.

**Rule:** Set outbox config based on expected burst rate:
- Low-throughput services (alert-api): 2000ms / 20 = 10/sec — fine
- Medium-throughput (evaluator): 200ms / 500 = 2,500/sec
- High-throughput (tick-ingestor): 200ms / 500 = 2,500/sec

### 7. Internal Docker services need `docker exec` for health checks

Services without host port mappings cannot be reached via `localhost:PORT` from the host.
Health check scripts, monitoring scripts, and load-test scripts must use
`docker exec <container> wget/curl` to reach internal actuator endpoints.

**Rule:** At project setup, identify which services expose host ports and which are
internal-only. Write all health check and monitoring commands using `docker exec` for
internal services from day one.

### 8. Plan Kafka partition counts for peak consumer parallelism

The partition count is the hard ceiling on consumer parallelism — no amount of scaling
beyond partition count adds throughput. This cannot be changed after topic creation without
downtime and rebalancing.

**Rule:** At topic creation, set `partitions = max_expected_consumer_instances × threads_per_instance`.
For `alert-triggers` with 2 persister instances × 8 threads = 16 partitions would be
ideal. The current 8 partitions limits total useful consumer threads to 8 across all
instances combined.

### 9. `prevent_destroy` in Terraform blocks the entire plan — not just the resource

When `prevent_destroy = true` is set on a resource, Terraform aborts the entire `destroy`
plan if that resource is in the dependency graph. Downstream resources (containers using
the volume) may or may not have been destroyed by the time Terraform aborts.

**Rule:** When you need to override `prevent_destroy`, remove the dependent resources
manually first (`docker rm -f <container>`), then run `terraform destroy`, then remove
the protected resource directly (`docker volume rm`).

### 10. Layer-2 dedup rate rises with concurrency and scale

The conditional UPDATE dedup layer (`UPDATE ... WHERE status = 'ACTIVE'`) blocks duplicate
trigger events from becoming notifications. At higher alert counts and higher consumer
concurrency, more race conditions occur — both evaluator instances fire triggers for the
same alert before the status update propagates.

**Rule:** Expect Layer-2 dedup rate to be 60–80% at 1M alerts with 2 evaluator instances.
This is correct and by design — the dedup layers exist precisely to handle this. Do not
try to prevent the races; let the dedup architecture absorb them. Monitor Layer 3+4 dedup
rate (ON CONFLICT DO NOTHING) — if it rises above 1%, investigate upstream.

---

## The Numbers That Matter

Final system capability at 1M alerts (L5b):

| Metric | Value |
|--------|-------|
| Alerts in memory (total, 2 instances) | 1,000,000 |
| Warm-up time (JDBC streaming) | 2.56 seconds |
| Warm-up throughput | ~390,000 alerts/sec |
| Peak trigger rate | 7,663 triggers/sec |
| Kafka consumer lag | 0 (evaluator keeps up at all load levels) |
| Max GC pause (ZGC) | 2 ms |
| OOM kills (all levels) | 0 |
| Total crashes encountered and fixed | 2 (CME + OOM at 800K) |
| Total fixes applied | 8 |
