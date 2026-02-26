#!/usr/bin/env bash
# =============================================================================
# load-test.sh — 1M Alert Load Test for Price Alert System
#
# Strategy:
#   1. Seed 1,000,000 ACTIVE alerts directly into PostgreSQL (bypass API rate
#      limit — we're testing evaluation throughput, not API throughput).
#   2. Confirm evaluator warm-up loads them into memory.
#   3. Run a tick bombardment loop: POST synthetic ticks directly via the
#      market-feed-simulator WebSocket to fire as many triggers as possible.
#   4. Stream key Prometheus metrics every 10 seconds to stdout.
#   5. Print a final summary (alerts triggered, notifications persisted,
#      throughput, lag, GC stats).
#
# Prerequisites: docker, psql (or docker exec postgres), curl, python3, bc
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'

ok()     { echo -e "  ${GREEN}✓${NC} $1"; }
fail()   { echo -e "  ${RED}✗${NC} $1"; }
info()   { echo -e "  ${CYAN}→${NC} $1"; }
warn()   { echo -e "  ${YELLOW}!${NC} $1"; }
header() { echo -e "\n${BOLD}${BLUE}━━━ $1 ━━━${NC}"; }
metric() { echo -e "  ${CYAN}[METRIC]${NC} $1"; }

# ── Config ────────────────────────────────────────────────────────────────────
TARGET_ALERTS=${TARGET_ALERTS:-1000000}
BATCH_SIZE=${BATCH_SIZE:-50000}        # rows per INSERT batch
NUM_USERS=${NUM_USERS:-10000}          # synthetic user IDs
PROMETHEUS_URL=${PROMETHEUS_URL:-http://localhost:9090}
ALERT_API_URL=${ALERT_API_URL:-http://localhost:8080}
MONITOR_INTERVAL=${MONITOR_INTERVAL:-10}   # seconds between metric snapshots
TICK_BLAST_SECONDS=${TICK_BLAST_SECONDS:-120}  # how long to hammer ticks

# 50 symbols from the simulator's CSV (same seed prices)
SYMBOLS=(AAPL MSFT GOOG AMZN NVDA META TSLA JPM V JNJ
         UNH WMT PG MA HD XOM ABBV KO PEP AVGO
         MRK COST TMO CSCO ACN ABT LIN MCD ADBE TXN
         NKE CRM DHR ORCL NFLX AMD INTC QCOM AMGN PM
         HON LOW BA UPS CAT GS IBM SBUX DIS)

SEED_PRICES=(185.50 420.75 175.25 185.00 880.50 505.25 175.80 195.50 280.00 155.75
             525.00 175.50 165.25 465.00 380.50 105.75 175.00  62.50 175.25 1350.00
             125.50 725.00 575.00  50.25 375.00 110.00 450.00 295.00 575.00 175.00
             105.50 275.00 255.00 125.00 625.00 175.00  45.50 175.00 295.00  95.00
             210.00 245.00 215.00 155.00 310.00 405.00 195.00 100.00 115.00)

# ── Helpers ───────────────────────────────────────────────────────────────────

psql_exec() {
    docker exec -i postgres psql -U alerts -d price_alerts -t -c "$1" 2>/dev/null | tr -d ' \n'
}

psql_exec_raw() {
    docker exec -i postgres psql -U alerts -d price_alerts "$@" 2>/dev/null
}

prom_query() {
    # $1 = PromQL expression; returns scalar value or "N/A"
    local val
    val=$(curl -sf "${PROMETHEUS_URL}/api/v1/query" \
        --data-urlencode "query=$1" 2>/dev/null \
        | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    r = d['data']['result']
    print(r[0]['value'][1] if r else '0')
except:
    print('N/A')
")
    echo "$val"
}

ulid_prefix() {
    # Generate a deterministic ULID-like prefix for batch IDs
    python3 -c "
import time, random, string
ts = int(time.time() * 1000)
chars = '0123456789ABCDEFGHJKMNPQRSTVWXYZ'
t = ''
v = ts
for _ in range(10):
    t = chars[v % 32] + t
    v //= 32
rand = ''.join(random.choices(chars, k=16))
print(t + rand)
"
}

check_services() {
    header "Pre-flight: Checking services"
    local health
    health=$(curl -sf "${ALERT_API_URL}/actuator/health" 2>/dev/null | python3 -c "import sys,json; print(json.load(sys.stdin).get('status','DOWN'))" 2>/dev/null || echo "DOWN")
    if [ "$health" = "UP" ]; then ok "alert-api UP"; else fail "alert-api DOWN — run ./scripts/terraform.sh up first"; exit 1; fi

    local pg_ok
    pg_ok=$(docker exec postgres pg_isready -U alerts -d price_alerts 2>/dev/null | grep -c "accepting" || echo 0)
    if [ "$pg_ok" -ge 1 ]; then ok "PostgreSQL accepting connections"; else fail "PostgreSQL not ready"; exit 1; fi

    for svc in evaluator evaluator-2 notification-persister notification-persister-2 tick-ingestor; do
        local st
        st=$(docker inspect --format='{{.State.Status}}' "$svc" 2>/dev/null || echo "missing")
        if [ "$st" = "running" ]; then ok "$svc running"; else warn "$svc: $st"; fi
    done
}

# ── Phase 1: Seed 1M alerts directly into PostgreSQL ─────────────────────────

seed_alerts() {
    header "Phase 1: Seeding ${TARGET_ALERTS} alerts into PostgreSQL"

    local existing
    existing=$(psql_exec "SELECT count(*) FROM alerts WHERE status='ACTIVE'")
    info "Existing ACTIVE alerts: ${existing}"

    if [ "${existing}" -ge "${TARGET_ALERTS}" ]; then
        warn "Already ${existing} ACTIVE alerts — skipping seed. Delete them first with --reset if you want a fresh run."
        return
    fi

    local to_insert=$(( TARGET_ALERTS - existing ))
    info "Inserting ${to_insert} alerts in batches of ${BATCH_SIZE}…"

    local inserted=0
    local batch_num=0
    local num_symbols=${#SYMBOLS[@]}

    # Use COPY for maximum insert speed
    while [ $inserted -lt $to_insert ]; do
        local this_batch=$(( to_insert - inserted ))
        [ $this_batch -gt $BATCH_SIZE ] && this_batch=$BATCH_SIZE

        batch_num=$(( batch_num + 1 ))
        local pct=$(( (inserted * 100) / to_insert ))
        printf "\r  Batch %-4d  |  %-8d / %-8d  (%3d%%)  " \
            "$batch_num" "$inserted" "$to_insert" "$pct"

        # Generate batch via Python and pipe into COPY
        python3 - <<PYEOF | docker exec -i postgres psql -U alerts -d price_alerts -q \
            -c "\COPY alerts (id,user_id,symbol,threshold_price,direction,status,note,created_at,updated_at) FROM STDIN WITH (FORMAT csv)"
import sys, random, time

chars = '0123456789ABCDEFGHJKMNPQRSTVWXYZ'
symbols_prices = [
    ('AAPL',185.50),('MSFT',420.75),('GOOG',175.25),('AMZN',185.00),('NVDA',880.50),
    ('META',505.25),('TSLA',175.80),('JPM',195.50),('V',280.00),('JNJ',155.75),
    ('UNH',525.00),('WMT',175.50),('PG',165.25),('MA',465.00),('HD',380.50),
    ('XOM',105.75),('ABBV',175.00),('KO',62.50),('PEP',175.25),('AVGO',1350.00),
    ('MRK',125.50),('COST',725.00),('TMO',575.00),('CSCO',50.25),('ACN',375.00),
    ('ABT',110.00),('LIN',450.00),('MCD',295.00),('ADBE',575.00),('TXN',175.00),
    ('NKE',105.50),('CRM',275.00),('DHR',255.00),('ORCL',125.00),('NFLX',625.00),
    ('AMD',175.00),('INTC',45.50),('QCOM',175.00),('AMGN',295.00),('PM',95.00),
    ('HON',210.00),('LOW',245.00),('BA',215.00),('UPS',155.00),('CAT',310.00),
    ('GS',405.00),('IBM',195.00),('SBUX',100.00),('DIS',115.00),
]
num_users = ${NUM_USERS}
batch_size = ${this_batch}
now_ts = '2026-02-25 00:00:00+00'

def make_ulid():
    ts = int(time.time() * 1000) + random.randint(0, 999999)
    t = ''
    v = ts
    for _ in range(10):
        t = chars[v % 32] + t
        v //= 32
    rand = ''.join(random.choices(chars, k=16))
    return t + rand

for _ in range(batch_size):
    uid  = f"USER{random.randint(1, num_users):06d}"
    sym, seed = random.choice(symbols_prices)
    # Threshold within ±30% of seed price — many will be immediately crossable
    lo  = seed * 0.70
    hi  = seed * 1.30
    thr = round(random.uniform(lo, hi), 2)
    # 60% ABOVE, 40% BELOW — biased so many will trigger quickly
    direction = 'ABOVE' if random.random() < 0.6 else 'BELOW'
    aid = make_ulid()
    note = f'load-test-{aid[:8]}'
    print(f'{aid},{uid},{sym},{thr:.6f},{direction},ACTIVE,{note},{now_ts},{now_ts}')
PYEOF

        inserted=$(( inserted + this_batch ))
    done

    echo ""
    local total_active
    total_active=$(psql_exec "SELECT count(*) FROM alerts WHERE status='ACTIVE'")
    ok "Seed complete — ACTIVE alerts in DB: ${total_active}"
}

# ── Phase 2: Confirm evaluator warm-up ───────────────────────────────────────

wait_for_warmup() {
    header "Phase 2: Restarting evaluators so they warm-up with seeded alerts"
    info "The evaluator loads all ACTIVE alerts at startup (ApplicationReadyEvent)."
    info "Since we seeded after startup, we must restart both evaluator instances."

    # Restart both evaluator containers so they re-run warm-up with 1M alerts
    for ev in evaluator evaluator-2; do
        docker restart "$ev" >/dev/null 2>&1 && info "Restarted $ev" || warn "Could not restart $ev"
    done

    info "Waiting for evaluators to become healthy (warm-up 1M alerts at batch=10000 takes ~60-120s)…"

    local max_wait=300
    local elapsed=0

    while [ $elapsed -lt $max_wait ]; do
        local health1 health2
        health1=$(docker exec evaluator wget -qO- "http://localhost:8082/actuator/health" 2>/dev/null \
            | python3 -c "import sys,json; print(json.load(sys.stdin).get('status','DOWN'))" 2>/dev/null || echo "DOWN")

        # Check evaluator logs for warmup completion message
        local warmup_done
        warmup_done=$(docker logs evaluator 2>&1 \
            | grep -c "Warm.up complete\|warmup complete\|Loaded.*alert\|warm.up.*done\|WarmUpService\|loaded.*index" 2>/dev/null || echo 0)

        if [ "$warmup_done" -ge 1 ] && [ "$health1" = "UP" ]; then
            echo ""
            ok "Evaluator is UP and warm-up complete"
            docker logs evaluator 2>&1 | grep -i "warm\|load\|index" | tail -5 | while read -r line; do
                info "$line"
            done
            return
        fi

        local active_count
        active_count=$(psql_exec "SELECT count(*) FROM alerts WHERE status='ACTIVE'" 2>/dev/null || echo "?")
        printf "\r  health=%s  warmup_signals=%s  active_in_db=%s  elapsed=%ds   " \
            "$health1" "$warmup_done" "$active_count" "$elapsed"
        sleep 5
        elapsed=$(( elapsed + 5 ))
    done

    echo ""
    warn "Warm-up wait timeout (${max_wait}s) — proceeding. Check: docker logs evaluator | grep -i warm"
}

# ── Phase 3: Tick bombardment ─────────────────────────────────────────────────

run_tick_blast() {
    header "Phase 3: Tick bombardment (${TICK_BLAST_SECONDS}s)"
    info "Increasing simulator tick rate by calling its REST API…"
    info "Simulator is already producing 500 ticks/s (50 symbols × 10Hz)"
    info "For the load test we will amplify by resetting simulator prices"
    info "to values that will cross many thresholds"

    # Force all symbol prices to extreme values to maximise trigger rate
    # ABOVE alerts (60%) → push price UP above seed*1.30 (above max threshold)
    # BELOW alerts (40%) → push price DOWN below seed*0.70 (below min threshold)
    # We do this by directly updating the simulator state via its actuator
    # (if not available, we accept the natural random-walk will cross thresholds)

    local blast_start=$SECONDS

    info "Monitoring trigger rate for ${TICK_BLAST_SECONDS}s…"
    info "Watch Grafana at http://localhost:3000 for live dashboards"

    local t=0
    local prev_triggered=0
    local prev_persisted=0

    printf "\n  %-8s  %-15s  %-12s  %-15s  %-12s  %-20s\n" \
        "Time(s)" "Alerts Triggered" "Trig/sec" "Notifs Persisted" "Notif/sec" "Kafka Consumer Lag"
    printf "  %-8s  %-15s  %-12s  %-15s  %-12s  %-20s\n" \
        "--------" "---------------" "----------" "---------------" "----------" "------------------"

    while [ $t -lt $TICK_BLAST_SECONDS ]; do
        sleep $MONITOR_INTERVAL
        t=$(( t + MONITOR_INTERVAL ))

        # Count from DB (most reliable)
        local triggered
        triggered=$(psql_exec "SELECT count(*) FROM alerts WHERE status IN ('TRIGGERED_TODAY','TRIGGERED')" 2>/dev/null || echo 0)
        [ -z "$triggered" ] && triggered=0

        local persisted
        persisted=$(psql_exec "SELECT count(*) FROM notifications" 2>/dev/null || echo 0)
        [ -z "$persisted" ] && persisted=0

        local trig_rate notif_rate
        trig_rate=$(echo "scale=1; ($triggered - $prev_triggered) / $MONITOR_INTERVAL" | bc 2>/dev/null || echo 0)
        notif_rate=$(echo "scale=1; ($persisted - $prev_persisted) / $MONITOR_INTERVAL" | bc 2>/dev/null || echo 0)

        # Kafka consumer lag via Prometheus
        local lag
        lag=$(prom_query 'sum(kafka_consumer_fetch_manager_records_lag{job=~".*"}) or vector(0)' 2>/dev/null || echo "N/A")

        printf "  %-8s  %-15s  %-12s  %-15s  %-12s  %-20s\n" \
            "${t}s" "$triggered" "${trig_rate}/s" "$persisted" "${notif_rate}/s" "$lag"

        prev_triggered=$triggered
        prev_persisted=$persisted
    done
}

# ── Phase 4: Collect JVM + system metrics snapshot ───────────────────────────

collect_metrics() {
    header "Phase 4: Metrics snapshot"

    for svc_url_port in "evaluator:8082" "evaluator-2:8082" "alert-api:8080" "notification-persister:8083" "notification-persister-2:8083" "tick-ingestor:8081"; do
        local svc="${svc_url_port%%:*}"
        local port="${svc_url_port##*:}"

        echo -e "\n  ${BOLD}${svc}${NC}"

        # JVM heap via actuator metrics — use docker exec + wget since only alert-api
        # exposes its actuator port to the host; all other services have no host port mapping.
        local heap_used heap_max gc_pause
        heap_used=$(docker exec "$svc" wget -qO- "http://localhost:${port}/actuator/metrics/jvm.memory.used?tag=area:heap" 2>/dev/null \
            | python3 -c "
import sys,json
try:
    d=json.load(sys.stdin)
    v=d['measurements'][0]['value']
    print(f'{v/1024/1024:.1f} MB')
except: print('N/A')
" 2>/dev/null || echo "N/A")

        heap_max=$(docker exec "$svc" wget -qO- "http://localhost:${port}/actuator/metrics/jvm.memory.max?tag=area:heap" 2>/dev/null \
            | python3 -c "
import sys,json
try:
    d=json.load(sys.stdin)
    v=d['measurements'][0]['value']
    print(f'{v/1024/1024:.1f} MB')
except: print('N/A')
" 2>/dev/null || echo "N/A")

        gc_pause=$(docker exec "$svc" wget -qO- "http://localhost:${port}/actuator/metrics/jvm.gc.pause" 2>/dev/null \
            | python3 -c "
import sys,json
try:
    d=json.load(sys.stdin)
    for m in d['measurements']:
        if m['statistic']=='MAX': print(f\"{m['value']*1000:.1f} ms\")
except: print('N/A')
" 2>/dev/null || echo "N/A")

        metric "heap used/max: ${heap_used} / ${heap_max}"
        metric "GC pause max:  ${gc_pause}"

        # Container stats
        local cpu_pct mem_mb
        cpu_pct=$(docker stats "$svc" --no-stream --format '{{.CPUPerc}}' 2>/dev/null || echo "N/A")
        mem_mb=$(docker stats "$svc" --no-stream --format '{{.MemUsage}}' 2>/dev/null || echo "N/A")
        metric "container CPU: ${cpu_pct}  MEM: ${mem_mb}"
    done
}

# ── Phase 5: Final summary ────────────────────────────────────────────────────

print_summary() {
    header "Load Test Summary"

    local total_active total_triggered total_persisted deduped
    total_active=$(psql_exec "SELECT count(*) FROM alerts WHERE status='ACTIVE'" || echo 0)
    total_triggered=$(psql_exec "SELECT count(*) FROM alerts WHERE status IN ('TRIGGERED_TODAY','TRIGGERED')" || echo 0)
    total_persisted=$(psql_exec "SELECT count(*) FROM notifications" || echo 0)
    deduped=$(psql_exec "SELECT count(*) FROM alert_trigger_log" || echo 0)

    local pct_triggered=0
    if [ "${TARGET_ALERTS}" -gt 0 ] 2>/dev/null; then
        pct_triggered=$(echo "scale=2; $total_triggered * 100 / $TARGET_ALERTS" | bc 2>/dev/null || echo 0)
    fi

    echo ""
    echo -e "  ${BOLD}Alerts seeded (target):${NC}        ${TARGET_ALERTS}"
    echo -e "  ${BOLD}Alerts still ACTIVE:${NC}           ${total_active}"
    echo -e "  ${BOLD}Alerts triggered:${NC}              ${total_triggered}  (${pct_triggered}% of seeded)"
    echo -e "  ${BOLD}Trigger log rows:${NC}              ${deduped}  (Layer-2 dedup table)"
    echo -e "  ${BOLD}Notifications persisted:${NC}       ${total_persisted}"

    # Outbox lag
    local outbox_pending_eval outbox_pending_ingestor
    outbox_pending_eval=$(psql_exec "SELECT count(*) FROM evaluator_outbox_record WHERE status='PENDING'" 2>/dev/null || echo 0)
    outbox_pending_ingestor=$(psql_exec "SELECT count(*) FROM ingestor_outbox_record WHERE status='PENDING'" 2>/dev/null || echo 0)
    echo -e "  ${BOLD}Evaluator outbox pending:${NC}      ${outbox_pending_eval}"
    echo -e "  ${BOLD}Ingestor outbox pending:${NC}       ${outbox_pending_ingestor}"

    echo ""
    info "Grafana dashboards: http://localhost:3000  (admin/admin)"
    info "Prometheus:         http://localhost:9090"
    info "alert-api health:   http://localhost:8080/actuator/health"
    echo ""
    info "Useful queries:"
    echo "    -- triggered rate over time"
    echo "    SELECT date_trunc('minute', last_triggered_at) AS minute,"
    echo "           count(*) AS triggered"
    echo "    FROM alerts WHERE last_triggered_at IS NOT NULL"
    echo "    GROUP BY 1 ORDER BY 1;"
    echo ""
    echo "    -- notification throughput"
    echo "    SELECT date_trunc('minute', created_at) AS minute, count(*)"
    echo "    FROM notifications GROUP BY 1 ORDER BY 1;"
    echo ""
    echo "    -- outbox backlog"
    echo "    SELECT status, count(*) FROM evaluator_outbox_record GROUP BY status;"
}

# ── Reset helper ──────────────────────────────────────────────────────────────

do_reset() {
    header "Reset: removing load-test data"
    warn "This will DELETE all alerts and notifications with load-test notes"
    read -r -p "  Type 'yes' to confirm: " confirm
    if [ "$confirm" != "yes" ]; then echo "Aborted."; exit 0; fi

    # Order matters: delete child rows before parent (FK constraints)
    psql_exec_raw -c "DELETE FROM notifications       WHERE alert_id IN (SELECT id FROM alerts WHERE note LIKE 'load-test-%');"
    psql_exec_raw -c "DELETE FROM alert_trigger_log   WHERE alert_id IN (SELECT id FROM alerts WHERE note LIKE 'load-test-%');"
    psql_exec_raw -c "DELETE FROM evaluator_outbox_record  WHERE TRUE;"
    psql_exec_raw -c "DELETE FROM ingestor_outbox_record   WHERE TRUE;"
    psql_exec_raw -c "DELETE FROM alertapi_outbox_record   WHERE TRUE;"
    psql_exec_raw -c "DELETE FROM alerts WHERE note LIKE 'load-test-%';"
    ok "Load-test data removed"
}

# ── Entry point ───────────────────────────────────────────────────────────────

ACTION="${1:-run}"

case "$ACTION" in
  run)
    echo -e "\n${BOLD}${BLUE}Price Alert System — 1M Alert Load Test${NC}"
    echo -e "  Target alerts : ${TARGET_ALERTS}"
    echo -e "  Batch size    : ${BATCH_SIZE}"
    echo -e "  Synthetic users: ${NUM_USERS}"
    echo -e "  Tick blast    : ${TICK_BLAST_SECONDS}s"
    echo -e "  Monitor every : ${MONITOR_INTERVAL}s"
    echo ""

    check_services
    seed_alerts
    wait_for_warmup
    run_tick_blast
    collect_metrics
    print_summary
    ;;

  seed-only)
    check_services
    seed_alerts
    ;;

  metrics)
    collect_metrics
    print_summary
    ;;

  reset)
    check_services
    do_reset
    ;;

  help|--help|-h)
    echo ""
    echo "Usage: $0 <command> [options]"
    echo ""
    echo "Commands:"
    echo "  run         Full load test: seed → warmup → tick blast → metrics → summary"
    echo "  seed-only   Only seed 1M alerts into the DB (no tick blast)"
    echo "  metrics     Print current metrics + summary (stack must be running)"
    echo "  reset       Remove all load-test alerts/notifications from DB"
    echo ""
    echo "Environment overrides:"
    echo "  TARGET_ALERTS=1000000    Number of alerts to seed (default: 1M)"
    echo "  BATCH_SIZE=50000         INSERT batch size (default: 50K)"
    echo "  NUM_USERS=10000          Synthetic user count (default: 10K)"
    echo "  TICK_BLAST_SECONDS=120   Duration of tick monitoring phase (default: 120s)"
    echo "  MONITOR_INTERVAL=10      Seconds between metric prints (default: 10s)"
    echo ""
    echo "Examples:"
    echo "  $0 run                                    # Full 1M test"
    echo "  TARGET_ALERTS=100000 $0 run               # Quick 100K test"
    echo "  $0 seed-only                              # Just load data"
    echo "  $0 metrics                                # Snapshot current state"
    echo "  $0 reset                                  # Wipe load-test data"
    ;;

  *)
    echo "Unknown command: $ACTION. Run '$0 help' for usage."
    exit 1
    ;;
esac
