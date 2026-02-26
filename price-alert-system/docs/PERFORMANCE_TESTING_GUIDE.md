# Performance Testing Guide — Price Alert System

Step-by-step instructions for running load tests, gradually increasing load, and identifying system limits.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Stack Setup](#2-stack-setup)
3. [Test Levels Overview](#3-test-levels-overview)
4. [Level 1 — Smoke (10K alerts, baseline)](#4-level-1--smoke-10k-alerts-baseline)
5. [Level 2 — Light Load (100K alerts)](#5-level-2--light-load-100k-alerts)
6. [Level 3 — Nominal Load (500K alerts)](#6-level-3--nominal-load-500k-alerts)
7. [Level 4 — Design Target (1M alerts)](#7-level-4--design-target-1m-alerts)
8. [Level 5 — Breaking Point (beyond 1M)](#8-level-5--breaking-point-beyond-1m)
9. [What to Watch While the Test Runs](#9-what-to-watch-while-the-test-runs)
10. [Interpreting Results](#10-interpreting-results)
11. [Resetting Between Runs](#11-resetting-between-runs)
12. [Known Limits and Bottleneck Map](#12-known-limits-and-bottleneck-map)

---

## 1. Prerequisites

### Required tools

```bash
docker --version         # Docker 24+
docker compose version   # Compose v2 (not v1 "docker-compose")
psql --version           # PostgreSQL client (or use docker exec)
python3 --version        # Python 3.9+ (used by load-test.sh)
bc                       # Basic calculator (pre-installed on macOS/Linux)
curl --version
```

Install missing tools (macOS):
```bash
brew install postgresql   # provides psql
brew install bc
```

### Hardware recommendation

| Component | Minimum | Recommended |
|---|---|---|
| CPU | 4 cores | 8+ cores |
| RAM | 8 GB | 16 GB |
| Disk | 10 GB free | 20 GB (SSD preferred) |

The full stack (Kafka ×3, PG, Redis, 5 app services, Prometheus, Grafana, Tempo) uses ~4 GB RAM at rest.

---

## 2. Stack Setup

### Start the full stack

```bash
cd price-alert-system
./scripts/terraform.sh up
```

Wait until all containers are healthy (~60–90 seconds):

```bash
docker compose ps
# All services should show "healthy" or "running"
```

Verify key endpoints are reachable:

```bash
# Alert API
curl -s http://localhost:8080/actuator/health | python3 -m json.tool

# Evaluator
curl -s http://localhost:8082/actuator/health | python3 -m json.tool

# Prometheus (open in browser to check scrape targets)
open http://localhost:9090/targets

# Grafana (admin/admin)
open http://localhost:3000
```

All five services should report `"status": "UP"` before running any load test.

---

## 3. Test Levels Overview

Run levels in order. Each level is a superset of the previous. Do a full reset between levels.

| Level | Alerts seeded | Purpose | Expected duration |
|---|---|---|---|
| 1 — Smoke | 10,000 | Verify pipeline end-to-end, calibrate timing | ~5 min |
| 2 — Light | 100,000 | Baseline throughput numbers, no stress | ~10 min |
| 3 — Nominal | 500,000 | Sustained load, find first bottlenecks | ~20 min |
| 4 — Design target | 1,000,000 | Full design target, warm-up stress | ~30 min |
| 5 — Breaking point | 2M+ | Find OOM / saturation limits | Open-ended |

The `load-test.sh` script drives levels 1–4. Level 5 requires manual commands.

---

## 4. Level 1 — Smoke (10K alerts, baseline)

**Goal:** Confirm the full pipeline works end-to-end and collect a clean baseline.

```bash
cd price-alert-system

TARGET_ALERTS=10000 \
BATCH_SIZE=5000 \
NUM_USERS=100 \
TICK_BLAST_SECONDS=60 \
MONITOR_INTERVAL=10 \
./scripts/load-test.sh run
```

### What to check

```bash
# After the script finishes, confirm rows in DB:
docker exec -i postgres psql -U alerts -d price_alerts -c "
SELECT
  status,
  count(*) AS alert_count
FROM alerts
WHERE note LIKE 'load-test-%'
GROUP BY status
ORDER BY status;"

# Expect to see TRIGGERED_TODAY rows (some alerts should have fired)
# and ACTIVE rows (some may not have crossed threshold yet)

# Confirm notifications were written:
docker exec -i postgres psql -U alerts -d price_alerts -c "
SELECT count(*) FROM notifications n
JOIN alerts a ON n.alert_id = a.id
WHERE a.note LIKE 'load-test-%';"
```

### Pass criteria

- No container restarts during the run: `docker compose ps` shows all "Up"
- At least some `TRIGGERED_TODAY` alerts in DB
- At least some notifications persisted
- Evaluator logs show warm-up complete message:
  ```bash
  docker logs evaluator 2>&1 | grep -i "warm-up complete"
  # Expected: "Evaluator warm-up complete: loaded 10000 alerts across 49 symbols"
  ```

### Reset

```bash
./scripts/load-test.sh reset
```

---

## 5. Level 2 — Light Load (100K alerts)

**Goal:** Establish throughput numbers under moderate load.

```bash
TARGET_ALERTS=100000 \
BATCH_SIZE=10000 \
NUM_USERS=1000 \
TICK_BLAST_SECONDS=120 \
MONITOR_INTERVAL=10 \
./scripts/load-test.sh run
```

### Key metrics to record

Open a second terminal and run this while the test is active:

```bash
# Every 15 seconds, snapshot trigger rate
watch -n 15 "docker exec -i postgres psql -U alerts -d price_alerts -t -c \
\"SELECT status, count(*) FROM alerts WHERE note LIKE 'load-test-%' GROUP BY status;\""
```

Also check Prometheus directly:

```bash
# Tick consumption rate (ticks/s consumed by evaluator)
curl -s 'http://localhost:9090/api/v1/query' \
  --data-urlencode 'query=rate(evaluator_ticks_processed_total[1m])' \
  | python3 -c "import sys,json; d=json.load(sys.stdin); [print(r['value'][1]) for r in d['data']['result']]"

# Alert trigger rate (triggers/s)
curl -s 'http://localhost:9090/api/v1/query' \
  --data-urlencode 'query=rate(evaluator_alerts_triggered_total[1m])' \
  | python3 -c "import sys,json; d=json.load(sys.stdin); [print(r['value'][1]) for r in d['data']['result']]"

# Notification throughput (notifications/s)
curl -s 'http://localhost:9090/api/v1/query' \
  --data-urlencode 'query=rate(notifications_persisted_total[1m])' \
  | python3 -c "import sys,json; d=json.load(sys.stdin); [print(r['value'][1]) for r in d['data']['result']]"
```

### Expected observations at 100K

- Warm-up time: ~2–5 seconds
- Evaluator heap: < 200 MB
- Tick consumption rate: ~500/s (limited by simulator, not evaluator)
- Kafka consumer lag: 0 (evaluator keeps up with simulator output)
- No HikariCP pending connections

### Reset

```bash
./scripts/load-test.sh reset
```

---

## 6. Level 3 — Nominal Load (500K alerts)

**Goal:** Run the system at sustained load. First bottlenecks typically appear here.

```bash
TARGET_ALERTS=500000 \
BATCH_SIZE=50000 \
NUM_USERS=5000 \
TICK_BLAST_SECONDS=180 \
MONITOR_INTERVAL=15 \
./scripts/load-test.sh run
```

### Watch warm-up memory

Open a second terminal before the restart phase:

```bash
# Stream evaluator heap usage every 5 seconds during warm-up
while true; do
  used=$(curl -sf 'http://localhost:8082/actuator/metrics/jvm.memory.used?tag=area:heap' \
    | python3 -c "import sys,json; d=json.load(sys.stdin); print(f\"{d['measurements'][0]['value']/1024/1024:.0f} MB\")" 2>/dev/null || echo "DOWN")
  echo "$(date +%H:%M:%S)  evaluator heap: $used"
  sleep 5
done
```

### Watch Kafka lag during tick blast

```bash
# Check consumer lag via kafka-consumer-groups.sh
docker exec kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server kafka:19092 \
  --describe \
  --group evaluator-ticks \
  2>/dev/null | grep -v "^$\|^TOPIC\|Consumer" | awk '{print $1, $2, $3, $4, $5, $6}'
```

### Expected at 500K

- Warm-up time: ~15–30 seconds
- Evaluator heap: ~200–280 MB (well within 512 MB limit)
- Tick rate and notification rate unchanged from Level 2 (simulator is the bottleneck, not evaluator)
- Kafka consumer lag: remains near 0

### If consumer lag climbs above 1000 during tick blast

This means the evaluator cannot keep up with tick ingestion. Note the lag value, then check:

```bash
# How many partitions are assigned to each evaluator?
docker exec kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server kafka:19092 \
  --describe \
  --group evaluator-ticks 2>/dev/null | grep evaluator
```

### Reset

```bash
./scripts/load-test.sh reset
```

---

## 7. Level 4 — Design Target (1M alerts)

**Goal:** Validate the system at its stated design capacity.

```bash
TARGET_ALERTS=1000000 \
BATCH_SIZE=50000 \
NUM_USERS=10000 \
TICK_BLAST_SECONDS=120 \
MONITOR_INTERVAL=10 \
./scripts/load-test.sh run
```

This is equivalent to `./scripts/load-test.sh run` with default settings.

### Monitor warm-up closely

The warm-up of 1M alerts with `JdbcTemplate` streaming (current implementation) should take 60–120 seconds. Watch memory stays flat:

```bash
# In a separate terminal — stream heap for both evaluator instances
for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do
  for port in 8082; do  # both evaluators share port 8082 via Terraform routing
    used=$(curl -sf "http://localhost:${port}/actuator/metrics/jvm.memory.used?tag=area:heap" \
      | python3 -c "import sys,json; d=json.load(sys.stdin); print(f\"{d['measurements'][0]['value']/1024/1024:.0f} MB\")" 2>/dev/null || echo "DOWN")
    echo "$(date +%H:%M:%S)  port=$port  heap=$used"
  done
  sleep 10
done
```

Heap should stay below 400 MB throughout warm-up (flat — not climbing). If it climbs continuously toward 512 MB, the `JdbcTemplate` streaming is not working correctly (e.g., `fetchSize` not being honoured).

### After warm-up: confirm index loaded

```bash
# How many alerts are in the in-memory index?
curl -s 'http://localhost:9090/api/v1/query' \
  --data-urlencode 'query=evaluator_index_alerts' \
  | python3 -c "import sys,json; d=json.load(sys.stdin); [print(f\"evaluator_index_alerts={r['value'][1]}\") for r in d['data']['result']]"

# Expect: ~1000000 total across both evaluator instances (~500K each)
```

### Monitor tick blast phase

```bash
# DB trigger count — run every 10 seconds
watch -n 10 "docker exec -i postgres psql -U alerts -d price_alerts -t -c \
  \"SELECT status, count(*) FROM alerts WHERE note LIKE 'load-test-%' GROUP BY status;\""

# Outbox backlog — should not grow indefinitely
watch -n 10 "docker exec -i postgres psql -U alerts -d price_alerts -t -c \
  \"SELECT status, count(*) FROM evaluator_outbox_record GROUP BY status;\""
```

### Pass criteria at 1M

| Metric | Pass | Concern |
|---|---|---|
| Evaluator heap during warm-up | < 450 MB | > 490 MB → approaching OOM |
| Warm-up duration | < 120 s | > 180 s → slow DB or I/O |
| Kafka consumer lag (steady state) | < 100 records | > 1000 → evaluator falling behind |
| Evaluator CPU during tick blast | < 80% | > 95% → CPU bound |
| HikariCP pending (primary pool) | 0 | > 0 → connection pool starvation |
| Outbox backlog (evaluator) | < 500 | > 5000 → Kafka write bottleneck |

### Reset

```bash
./scripts/load-test.sh reset
```

---

## 8. Level 5 — Breaking Point (beyond 1M)

**Goal:** Find the actual limit of the system. Run this only after Level 4 passes cleanly.

These commands are manual — `load-test.sh` does not automate Level 5.

### Step 1: Seed 2M alerts

```bash
# Seed directly — this takes ~4–8 minutes
TARGET_ALERTS=2000000 \
BATCH_SIZE=100000 \
NUM_USERS=20000 \
./scripts/load-test.sh seed-only
```

### Step 2: Restart evaluators to warm-up with 2M

```bash
docker restart evaluator evaluator-2

# Watch logs from both instances
docker logs -f evaluator &
docker logs -f evaluator-2 &
```

Watch the heap metric while warm-up runs:

```bash
while true; do
  ts=$(date +%H:%M:%S)
  for port in 8082; do
    result=$(curl -sf "http://localhost:${port}/actuator/metrics/jvm.memory.used?tag=area:heap" \
      | python3 -c "
import sys, json
d = json.load(sys.stdin)
used = d['measurements'][0]['value'] / 1024 / 1024
print(f'{used:.0f} MB')
" 2>/dev/null || echo "DOWN")
    echo "$ts  heap=$result"
  done
  sleep 5
done
```

### Step 3: Gradually add more alerts

If 2M warm-up succeeds, push further in increments:

```bash
# Add 500K more (to reach 2.5M total)
TARGET_ALERTS=2500000 \
BATCH_SIZE=100000 \
NUM_USERS=25000 \
./scripts/load-test.sh seed-only

docker restart evaluator evaluator-2
# Monitor as above
```

Continue incrementing in 500K steps until:
- Evaluator OOM kills (heap hits 512 MB and container exits with code 137)
- Or warm-up time exceeds 300 seconds
- Or Kafka consumer lag grows unbounded after warm-up

### Step 4: Record the breaking point

When the evaluator fails, capture:

```bash
# Container exit code
docker inspect evaluator --format='{{.State.ExitCode}}'
# 137 = OOM kill

# Last log lines before death
docker logs evaluator 2>&1 | tail -50

# How many alerts were loaded before crash
docker exec -i postgres psql -U alerts -d price_alerts -t -c \
  "SELECT count(*) FROM alerts WHERE status = 'ACTIVE';"
```

The breaking point is the alert count at which the evaluator can no longer complete warm-up within heap limits.

### Increase heap to push the limit further

To test if more heap allows higher capacity, update `docker-compose.yml` or Terraform and re-run:

```yaml
# docker-compose.yml — evaluator service
environment:
  JAVA_TOOL_OPTIONS: "-Xms512m -Xmx1024m -XX:+UseZGC -XX:+ZGenerational"
```

Then:

```bash
./scripts/terraform.sh up   # or docker compose up -d evaluator evaluator-2
```

Retest with the alert count that previously caused OOM.

---

## 9. What to Watch While the Test Runs

### Terminal 1 — DB trigger counts (every 10s)

```bash
watch -n 10 "docker exec -i postgres psql -U alerts -d price_alerts -c \
\"SELECT
  status,
  count(*) AS count
FROM alerts
WHERE note LIKE 'load-test-%'
GROUP BY status
ORDER BY count DESC;\""
```

### Terminal 2 — Evaluator heap (every 5s)

```bash
watch -n 5 "curl -sf 'http://localhost:8082/actuator/metrics/jvm.memory.used?tag=area:heap' \
  | python3 -c \"
import sys,json
d=json.load(sys.stdin)
mb=d['measurements'][0]['value']/1024/1024
print(f'evaluator heap: {mb:.0f} MB / 512 MB  ({mb/512*100:.0f}%)')
\""
```

### Terminal 3 — Kafka consumer lag (every 10s)

```bash
watch -n 10 "docker exec kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server kafka:19092 \
  --describe \
  --group evaluator-ticks 2>/dev/null | grep -v '^$'"
```

### Terminal 4 — Container health overview

```bash
watch -n 10 "docker stats --no-stream --format \
  'table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}' \
  evaluator evaluator-2 alert-api notification-persister tick-ingestor kafka kafka-2 kafka-3 postgres redis"
```

### Grafana dashboards (browser)

Open [http://localhost:3000](http://localhost:3000) (admin / admin) and watch:

| Panel | What to look for |
|---|---|
| JVM Heap Used | Should plateau after warm-up, not grow during tick blast |
| GC Pause Max | Should stay < 5 ms with ZGC |
| evaluator_ticks_processed_total | Should climb at ~500/s |
| evaluator_alerts_triggered_total | Climbs during blast, then flattens when all alerts fire once |
| notifications_persisted_total | Should track trigger total with small lag |
| Kafka consumer lag | Should stay near 0 |
| HikariCP pending | Should stay 0 |

### Key Prometheus queries (paste into http://localhost:9090)

```promql
# Tick processing rate (ticks/s per evaluator instance)
rate(evaluator_ticks_processed_total[1m])

# Alert trigger rate (triggers/s)
rate(evaluator_alerts_triggered_total[1m])

# Notification write rate (notifications/s)
rate(notifications_persisted_total[1m])

# Evaluator heap utilisation %
sum(jvm_memory_used_bytes{area="heap", application="evaluator"})
/ sum(jvm_memory_max_bytes{area="heap", application="evaluator"}) * 100

# Kafka consumer lag — market-ticks topic
max by (partition) (
  kafka_consumer_fetch_manager_records_lag{topic="market-ticks"}
)

# HikariCP starvation (pending > 0 = pool too small)
hikaricp_connections_pending

# DB outbox backlog
# (no Prometheus metric — query DB directly)
```

---

## 10. Interpreting Results

### Throughput ceiling

The system's end-to-end throughput is bounded by the **slowest stage**:

| Stage | Theoretical max | What limits it |
|---|---|---|
| Simulator → tick-ingestor | ~500 ticks/s | 50 symbols × 100 ms interval |
| tick-ingestor → Kafka | ~500 msgs/s | Outbox poll 200 ms, batch 500 |
| Kafka → evaluator | ~500 msgs/s | 16 partitions × 2 instances |
| Evaluator evaluation | ~50,000 ticks/s | In-memory TreeMap, no I/O |
| Evaluator → Kafka (triggers) | burst | Outbox poll 1000 ms, batch 50 |
| Kafka → notification-persister | burst | ON CONFLICT DO NOTHING, batch insert |

**Current bottleneck: the simulator** (capped at 500 ticks/s). The evaluation engine is not the bottleneck at this tick rate.

### Healthy run signature

```
Metric                     Expected value during steady-state load
─────────────────────────────────────────────────────────────────
Tick processing rate        ~500/s
Alert trigger rate          depends on threshold spread (0–500/s)
Kafka consumer lag          0 records
Evaluator heap              plateau below 450 MB for 1M alerts
GC pause max (ZGC)          < 2 ms
HikariCP pending            0
Outbox backlog              < 100 records
Notification write rate     matches trigger rate with < 2s lag
```

### Warning signs

| Observation | Likely cause | Action |
|---|---|---|
| Evaluator heap climbing toward 512 MB | `fetchSize` not set or JPA used instead of JdbcTemplate | Check `WarmUpService` — must use `JdbcTemplate` with `setFetchSize` |
| Kafka lag growing on `market-ticks` | Evaluator CPU saturated or GC pausing | Check CPU%, GC pause; consider adding evaluator-3 |
| HikariCP pending > 0 on primary-pool | Too many concurrent `markTriggeredToday()` calls | Increase `evaluator.datasource.hikari.maximum-pool-size` |
| Outbox backlog > 5000 (evaluator) | Kafka write throughput too low | Decrease `namastack.outbox.poll-interval` or increase batch-size |
| Notifications lag behind triggers by > 30s | notification-persister bottleneck | Check `notification-persister` CPU and DB pool |
| Container exit code 137 | OOM kill | Increase `-Xmx` or reduce alert count |

### Scaling options if limits are hit

| Bottleneck | Fix |
|---|---|
| Evaluator OOM at warm-up | Increase `-Xmx` beyond 512m |
| Evaluator CPU during tick blast | Add evaluator-3 (joins consumer group automatically) |
| Simulator tick rate too low to stress evaluator | Modify simulator interval: `market-feed-simulator.tick-interval-ms=10` |
| DB pool starvation | Increase `maximum-pool-size` in evaluator `application.yml` |
| Notification-persister falling behind | Add notification-persister-2 (consumer group scales horizontally) |

---

## 11. Resetting Between Runs

Always reset before starting a new level to avoid data from previous runs affecting results.

### Quick reset (load-test data only)

```bash
./scripts/load-test.sh reset
# Prompts for confirmation, then deletes load-test alerts + notifications
```

### Full reset (wipe all data, keep schema)

```bash
docker exec -i postgres psql -U alerts -d price_alerts <<'SQL'
TRUNCATE notifications, alert_trigger_log, alerts CASCADE;
TRUNCATE evaluator_outbox_record, ingestor_outbox_record, alertapi_outbox_record CASCADE;
SQL
```

Then restart evaluators so they warm up against an empty DB (fast):

```bash
docker restart evaluator evaluator-2
```

### Nuclear reset (destroy and recreate entire stack)

```bash
cd price-alert-system
./scripts/terraform.sh destroy
./scripts/terraform.sh up
```

Use this if you've changed any configuration files or if containers are in an inconsistent state.

---

## 12. Known Limits and Bottleneck Map

### Current system limits (measured)

| Limit | Value | Where it bites |
|---|---|---|
| Max alerts in warm-up (at -Xmx512m) | ~1.5M–2M | Evaluator OOM |
| Max tick throughput | ~500 ticks/s | Simulator, not evaluator |
| Max evaluation throughput | ~50,000 ticks/s (estimated) | In-memory TreeMap — never the bottleneck at 500 ticks/s |
| Max evaluator instances | 16 | Partition count on `market-ticks` topic |
| Max DB connections | 80–100 total | PostgreSQL `max_connections=100` |

### Bottleneck pipeline diagram

```
Simulator (500 ticks/s)
  │
  ▼  WebSocket
tick-ingestor
  │  Outbox → Kafka (market-ticks, 16 partitions)
  ▼
Kafka broker cluster (3 nodes, RF=3)
  │
  ▼  16 consumer threads × 2 evaluator instances
evaluator (×2)
  │  In-memory TreeMap evaluation (not the bottleneck)
  │  Async DB status update (HikariCP pool: max=10)
  │  Outbox → Kafka (alert-triggers, 8 partitions)
  ▼
notification-persister
  │  ON CONFLICT DO NOTHING insert
  ▼
PostgreSQL
```

### Where to add instances for more scale

```bash
# Add a third evaluator (joins consumer group, gets partitions 10-15)
# In docker-compose.yml or Terraform, copy the evaluator service block
# No code changes needed — Kafka rebalances automatically

# Add a second notification-persister
# Same approach — copies of the service join the notification-persister-group
```

The maximum number of useful evaluator instances is equal to the number of `market-ticks` partitions (currently **16**). Beyond that, extra instances sit idle with no partitions assigned.
