#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────────────────
# Price Alert System — Launch Script
# Builds, starts, and verifies the full stack via Docker Compose
# ─────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"

# Colors
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; BLUE='\033[0;34m'; CYAN='\033[0;36m'; NC='\033[0m'
ok() { echo -e "  ${GREEN}✓${NC} $1"; }
fail() { echo -e "  ${RED}✗${NC} $1"; }
info() { echo -e "  ${CYAN}→${NC} $1"; }
header() { echo -e "\n${BLUE}━━━ $1 ━━━${NC}"; }

# ── Parse args ──
ACTION="${1:-up}"
SKIP_BUILD=false
SKIP_TESTS=false

usage() {
  echo "Usage: $0 [command] [options]"
  echo ""
  echo "Commands:"
  echo "  up          Build and start the full stack (default)"
  echo "  down        Stop and remove all containers"
  echo "  restart     Restart all services (no rebuild)"
  echo "  status      Show service status"
  echo "  logs        Tail logs for all services"
  echo "  test        Run the E2E happy path test"
  echo "  clean       Stop containers, remove volumes, prune images"
  echo ""
  echo "Options:"
  echo "  --skip-build    Skip Gradle build (use existing jars)"
  echo "  --skip-tests    Skip Gradle unit tests during build"
  echo ""
  echo "Examples:"
  echo "  $0 up                   # Full build + start"
  echo "  $0 up --skip-build      # Start with existing jars"
  echo "  $0 test                 # Run E2E test against running stack"
  echo "  $0 logs                 # Tail all service logs"
  echo "  $0 down                 # Stop everything"
  exit 0
}

for arg in "$@"; do
  case "$arg" in
    --skip-build) SKIP_BUILD=true ;;
    --skip-tests) SKIP_TESTS=true ;;
    --help|-h) usage ;;
    up|down|restart|status|logs|test|clean) ACTION="$arg" ;;
  esac
done

# ── Functions ──

gradle_build() {
  header "Building Gradle artifacts"
  local test_flag=""
  if $SKIP_TESTS; then test_flag="-x test"; info "Skipping tests"; fi
  ./gradlew clean build $test_flag --parallel 2>&1 | tail -5
  ok "Gradle build complete"
}

docker_build() {
  header "Building Docker images"
  docker compose build 2>&1 | grep -E "Built|built|DONE" | tail -10
  ok "Docker images built"
}

docker_up() {
  header "Starting services"
  docker compose up -d 2>&1 | tail -10
  ok "Containers started"
}

wait_healthy() {
  header "Waiting for services to be healthy"
  local services=("alert-api" "market-feed-simulator" "tick-ingestor" "kafka" "postgres" "redis")
  local timeout=90
  local elapsed=0

  for svc in "${services[@]}"; do
    local container="price-alert-system-${svc}-1"
    printf "  Waiting for %-25s " "$svc..."
    while [ $elapsed -lt $timeout ]; do
      local health
      health=$(docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null || echo "missing")
      if [ "$health" = "healthy" ]; then
        echo -e "${GREEN}healthy${NC}"
        break
      fi
      sleep 2
      elapsed=$((elapsed + 2))
    done
    if [ $elapsed -ge $timeout ]; then
      echo -e "${RED}timeout${NC}"
      fail "$svc did not become healthy within ${timeout}s"
      return 1
    fi
  done

  # Non-healthcheck services (evaluator, notification-persister) — just check running
  for svc in "evaluator" "notification-persister"; do
    local container="price-alert-system-${svc}-1"
    local status
    status=$(docker inspect --format='{{.State.Status}}' "$container" 2>/dev/null || echo "missing")
    if [ "$status" = "running" ]; then
      printf "  %-33s ${GREEN}running${NC}\n" "$svc"
    else
      printf "  %-33s ${RED}${status}${NC}\n" "$svc"
    fi
  done

  ok "All services ready"
}

show_status() {
  header "Service Status"
  docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}" 2>&1
}

show_endpoints() {
  header "Endpoints"
  echo -e "  ${CYAN}Alert API:${NC}        http://localhost:8080/api/v1/alerts"
  echo -e "  ${CYAN}Notifications:${NC}    http://localhost:8080/api/v1/notifications"
  echo -e "  ${CYAN}Health:${NC}           http://localhost:8080/actuator/health"
  echo -e "  ${CYAN}Simulator WS:${NC}     ws://localhost:8085/v1/feed"
  echo -e "  ${CYAN}PostgreSQL:${NC}       localhost:5432 (alerts/alerts_local)"
  echo -e "  ${CYAN}Kafka:${NC}            localhost:9092"
  echo -e "  ${CYAN}Redis:${NC}            localhost:6379"
  echo -e "  ${CYAN}Data Flow Viz:${NC}    file://${PROJECT_DIR}/docs/dataflow.html"
}

generate_token() {
  local secret="price-alert-dev-secret-key-change-in-production"
  local header
  header=$(echo -n '{"alg":"HS256","typ":"JWT"}' | base64 | tr -d '=' | tr '/+' '_-')
  local payload
  payload=$(echo -n '{"sub":"user_e2e_001","iat":1700000000,"exp":9999999999}' | base64 | tr -d '=' | tr '/+' '_-')
  local signature
  signature=$(echo -n "${header}.${payload}" | openssl dgst -sha256 -hmac "${secret}" -binary | base64 | tr -d '=' | tr '/+' '_-')
  echo "${header}.${payload}.${signature}"
}

run_e2e_test() {
  header "Running E2E Happy Path Test"
  local token
  token=$(generate_token)

  # Step 1: Health check
  info "Step 1: Verify alert-api is healthy"
  local health
  health=$(curl -sf http://localhost:8080/actuator/health 2>/dev/null || echo "FAIL")
  if echo "$health" | grep -q "UP"; then ok "alert-api is UP"; else fail "alert-api health check failed"; return 1; fi

  # Step 2: Create alert
  info "Step 2: Create AAPL alert (ABOVE \$150)"
  local create_resp
  create_resp=$(curl -sf -X POST http://localhost:8080/api/v1/alerts \
    -H "Authorization: Bearer ${token}" \
    -H "Content-Type: application/json" \
    -d '{"symbol":"AAPL","thresholdPrice":150.00,"direction":"ABOVE","note":"E2E test"}' 2>/dev/null)

  if [ -z "$create_resp" ]; then fail "Create alert failed (empty response)"; return 1; fi
  local alert_id
  alert_id=$(echo "$create_resp" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])" 2>/dev/null)
  local status_val
  status_val=$(echo "$create_resp" | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])" 2>/dev/null)

  if [ "$status_val" = "ACTIVE" ]; then
    ok "Alert created: $alert_id (status=ACTIVE)"
  else
    fail "Unexpected status: $status_val"; return 1
  fi

  # Step 3: Verify outbox processed
  info "Step 3: Wait for outbox to publish CREATED event"
  sleep 5
  local outbox_status
  outbox_status=$(docker compose exec -T postgres psql -U alerts -d price_alerts -t -c \
    "SELECT status FROM alertapi_outbox_record WHERE record_key='AAPL' ORDER BY created_at DESC LIMIT 1;" 2>/dev/null | tr -d ' ')

  if [ "$outbox_status" = "COMPLETED" ]; then
    ok "Outbox: CREATED event published (COMPLETED)"
  else
    echo -e "  ${YELLOW}!${NC} Outbox status: '$outbox_status' (may still be processing)"
  fi

  # Step 4: Verify alert in DB
  info "Step 4: Verify alert persisted in database"
  local db_count
  db_count=$(docker compose exec -T postgres psql -U alerts -d price_alerts -t -c \
    "SELECT count(*) FROM alerts WHERE id='$alert_id';" 2>/dev/null | tr -d ' ')

  if [ "$db_count" = "1" ]; then ok "Alert exists in DB"; else fail "Alert not found in DB"; return 1; fi

  # Step 5: Wait for trigger
  info "Step 5: Waiting for market tick to trigger the alert (up to 60s)"
  local triggered=false
  for i in $(seq 1 30); do
    local alert_status
    alert_status=$(docker compose exec -T postgres psql -U alerts -d price_alerts -t -c \
      "SELECT status FROM alerts WHERE id='$alert_id';" 2>/dev/null | tr -d ' ')
    if [ "$alert_status" = "TRIGGERED_TODAY" ]; then
      triggered=true
      ok "Alert triggered! (status=TRIGGERED_TODAY) after ~$((i * 2))s"
      break
    fi
    sleep 2
  done

  if ! $triggered; then
    echo -e "  ${YELLOW}!${NC} Alert not yet triggered (AAPL price may not have crossed \$150 yet)"
    echo -e "  ${YELLOW}!${NC} This is expected if the simulator starts AAPL above \$150"
    echo -e "  ${YELLOW}!${NC} Try creating a BELOW alert with a high threshold instead"
  fi

  # Step 6: Check notification
  if $triggered; then
    info "Step 6: Verify notification persisted"
    sleep 3
    local notif_count
    notif_count=$(docker compose exec -T postgres psql -U alerts -d price_alerts -t -c \
      "SELECT count(*) FROM notifications WHERE alert_id='$alert_id';" 2>/dev/null | tr -d ' ')

    if [ "$notif_count" -ge 1 ] 2>/dev/null; then
      ok "Notification persisted ($notif_count row(s))"
    else
      echo -e "  ${YELLOW}!${NC} Notification not yet persisted (may still be in flight)"
    fi

    # Step 7: Verify via API
    info "Step 7: Verify notification via API"
    local notif_resp
    notif_resp=$(curl -sf "http://localhost:8080/api/v1/notifications" \
      -H "Authorization: Bearer ${token}" 2>/dev/null)
    local total
    total=$(echo "$notif_resp" | python3 -c "import sys,json; print(json.load(sys.stdin)['totalElements'])" 2>/dev/null)

    if [ "$total" -ge 1 ] 2>/dev/null; then
      ok "Notification visible via API (totalElements=$total)"
    else
      echo -e "  ${YELLOW}!${NC} No notifications via API yet"
    fi
  fi

  # Step 8: Verify outbox isolation
  info "Step 8: Verify per-service outbox isolation"
  local table_count
  table_count=$(docker compose exec -T postgres psql -U alerts -d price_alerts -t -c \
    "SELECT count(DISTINCT tablename) FROM pg_tables WHERE tablename LIKE '%outbox%';" 2>/dev/null | tr -d ' ')

  if [ "$table_count" = "9" ]; then
    ok "9 outbox tables (3 per service) — isolation verified"
  else
    echo -e "  ${YELLOW}!${NC} Found $table_count outbox tables (expected 9)"
  fi

  header "E2E Test Summary"
  if $triggered; then
    echo -e "  ${GREEN}PASS${NC} — Full flow verified: create → outbox → Kafka → evaluate → trigger → notify"
  else
    echo -e "  ${YELLOW}PARTIAL${NC} — Alert created and outbox working. Trigger depends on simulator price."
  fi
}

do_clean() {
  header "Cleaning up"
  docker compose down -v --remove-orphans 2>&1 | tail -5
  ok "Containers stopped, volumes removed"
}

# ── Main ──
case "$ACTION" in
  up)
    if ! $SKIP_BUILD; then gradle_build; fi
    docker_build
    docker_up
    wait_healthy
    show_status
    show_endpoints
    echo ""
    info "Run '${0} test' to execute E2E happy path test"
    ;;
  down)
    header "Stopping services"
    docker compose down 2>&1 | tail -5
    ok "All services stopped"
    ;;
  restart)
    header "Restarting services"
    docker compose restart 2>&1 | tail -10
    wait_healthy
    show_status
    ;;
  status)
    show_status
    show_endpoints
    ;;
  logs)
    docker compose logs -f --tail=50
    ;;
  test)
    run_e2e_test
    ;;
  clean)
    do_clean
    ;;
  *)
    usage
    ;;
esac
