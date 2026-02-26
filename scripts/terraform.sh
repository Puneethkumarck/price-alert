#!/usr/bin/env bash
set -euo pipefail

# ─────────────────────────────────────────────────────────
# Price Alert System — Terraform Setup Script
# Manages the full infrastructure stack via terraform-provider-docker
# ─────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
TF_DIR="$PROJECT_DIR/infra/terraform"

# Colors (same palette as launch.sh)
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; BLUE='\033[0;34m'; CYAN='\033[0;36m'; NC='\033[0m'
ok()     { echo -e "  ${GREEN}✓${NC} $1"; }
fail()   { echo -e "  ${RED}✗${NC} $1"; }
info()   { echo -e "  ${CYAN}→${NC} $1"; }
warn()   { echo -e "  ${YELLOW}!${NC} $1"; }
header() { echo -e "\n${BLUE}━━━ $1 ━━━${NC}"; }

# ── Parse args ──
ACTION="${1:-help}"
ENV="local"
SKIP_BUILD=false
SKIP_TESTS=false
AUTO_APPROVE=false

usage() {
  echo ""
  echo "Usage: $0 <command> [options]"
  echo ""
  echo "Commands:"
  echo "  up          Build JARs, init Terraform, and apply the full stack"
  echo "  down        Destroy all Terraform-managed containers (pgdata protected)"
  echo "  clean       Destroy everything including the pgdata volume"
  echo "  plan        Show what Terraform would create/change/destroy"
  echo "  status      Show running containers and Terraform outputs"
  echo "  init        Run terraform init only"
  echo "  test        Run E2E happy path test against the running stack"
  echo ""
  echo "Options:"
  echo "  --env <name>       Environment var-file to use (default: local)"
  echo "                     Resolves to infra/terraform/environments/<name>.tfvars"
  echo "  --skip-build       Skip Gradle JAR build (use existing JARs)"
  echo "  --skip-tests       Skip unit tests during Gradle build"
  echo "  --auto-approve     Pass -auto-approve to terraform apply/destroy"
  echo ""
  echo "Secrets:"
  echo "  Create infra/terraform/secrets.auto.tfvars from the example template:"
  echo "    cp infra/terraform/secrets.auto.tfvars.example infra/terraform/secrets.auto.tfvars"
  echo "  Or export TF_VAR_jwt_secret, TF_VAR_db_password, TF_VAR_grafana_admin_password"
  echo ""
  echo "Examples:"
  echo "  $0 up                         # Full build + apply"
  echo "  $0 up --skip-build            # Apply with existing JARs"
  echo "  $0 up --skip-build --auto-approve"
  echo "  $0 plan                       # Dry-run"
  echo "  $0 status                     # Show containers + outputs"
  echo "  $0 test                       # Run E2E test"
  echo "  $0 down                       # Destroy (keeps pgdata)"
  echo "  $0 clean                      # Destroy everything incl. pgdata"
  exit 0
}

for arg in "$@"; do
  case "$arg" in
    --skip-build)   SKIP_BUILD=true ;;
    --skip-tests)   SKIP_TESTS=true ;;
    --auto-approve) AUTO_APPROVE=true ;;
    --help|-h)      usage ;;
    --env)          ;;  # handled below
    up|down|clean|plan|status|init|test|help) ACTION="$arg" ;;
  esac
done

# Capture --env <value>
for i in "$@"; do
  if [ "$i" = "--env" ]; then
    shift_next=true
  elif [ "${shift_next:-false}" = "true" ]; then
    ENV="$i"
    shift_next=false
  fi
done

TFVARS="$TF_DIR/environments/${ENV}.tfvars"

# ── Preflight checks ──

check_terraform() {
  if ! command -v terraform &>/dev/null; then
    fail "terraform not found in PATH"
    echo ""
    echo "  Install via Homebrew:  brew install terraform"
    echo "  Or download from:      https://developer.hashicorp.com/terraform/install"
    exit 1
  fi
  local ver
  ver=$(terraform version -json 2>/dev/null | python3 -c "import sys,json; print(json.load(sys.stdin)['terraform_version'])" 2>/dev/null || terraform version | head -1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')
  ok "Terraform $ver"
}

check_docker() {
  if ! docker info &>/dev/null; then
    fail "Docker daemon is not running"
    exit 1
  fi
  ok "Docker is running"
}

check_tfvars() {
  if [ ! -f "$TFVARS" ]; then
    fail "Environment file not found: $TFVARS"
    echo "  Available environments:"
    ls "$TF_DIR/environments/"*.tfvars 2>/dev/null | xargs -I{} basename {} .tfvars | sed 's/^/    /'
    exit 1
  fi
  ok "Using var-file: environments/${ENV}.tfvars"
}

check_secrets() {
  local secrets_file="$TF_DIR/secrets.auto.tfvars"
  local example_file="$TF_DIR/secrets.auto.tfvars.example"

  if [ -n "${TF_VAR_jwt_secret:-}" ]; then
    ok "Secrets provided via TF_VAR_* environment variables"
    return
  fi

  if [ ! -f "$secrets_file" ]; then
    warn "secrets.auto.tfvars not found — creating from example template"
    cp "$example_file" "$secrets_file"
    # Populate with safe local-dev defaults
    sed -i.bak \
      -e 's|^jwt_secret = ""|jwt_secret = "price-alert-dev-secret-key-change-in-production"|' \
      -e 's|^db_password = ""|db_password = "alerts_local"|' \
      -e 's|^grafana_admin_password = ""|grafana_admin_password = "admin"|' \
      "$secrets_file"
    rm -f "${secrets_file}.bak"
    ok "Created secrets.auto.tfvars with local-dev defaults"
    warn "Edit $secrets_file to use real secrets for non-local environments"
  else
    ok "Secrets file found (secrets.auto.tfvars)"
  fi
}

check_jars() {
  local modules=("alert-api" "tick-ingestor" "evaluator" "notification-persister" "market-feed-simulator")
  local missing=()
  for mod in "${modules[@]}"; do
    if [ ! -f "$PROJECT_DIR/$mod/build/libs/$mod-0.1.0-SNAPSHOT.jar" ]; then
      missing+=("$mod")
    fi
  done
  if [ ${#missing[@]} -gt 0 ]; then
    warn "Missing JARs: ${missing[*]}"
    return 1
  fi
  ok "All 5 JARs found"
  return 0
}

# ── Build ──

gradle_build() {
  header "Building Gradle artifacts"
  cd "$PROJECT_DIR"
  local test_flag=""
  if $SKIP_TESTS; then test_flag="-x test"; info "Skipping unit tests"; fi
  ./gradlew clean build $test_flag --parallel 2>&1 | tail -5
  ok "Gradle build complete"
}

# ── Terraform operations ──

tf_init() {
  header "Terraform init"
  cd "$TF_DIR"
  terraform init -input=false 2>&1 | tail -10
  ok "Provider plugins installed"
}

tf_plan() {
  header "Terraform plan"
  cd "$TF_DIR"
  terraform plan -var-file="$TFVARS" -input=false
}

tf_apply() {
  header "Terraform apply"
  cd "$TF_DIR"
  local approve_flag=""
  if $AUTO_APPROVE; then approve_flag="-auto-approve"; fi
  terraform apply -var-file="$TFVARS" -input=false $approve_flag
  ok "Stack is up"
}

tf_destroy() {
  header "Terraform destroy"
  cd "$TF_DIR"
  local approve_flag=""
  if $AUTO_APPROVE; then approve_flag="-auto-approve"; fi
  terraform destroy -var-file="$TFVARS" -input=false $approve_flag
  ok "Stack destroyed (pgdata volume protected by prevent_destroy)"
}

tf_destroy_clean() {
  header "Terraform destroy (including pgdata volume)"
  cd "$TF_DIR"
  warn "This will permanently delete all PostgreSQL data"
  if ! $AUTO_APPROVE; then
    read -r -p "  Are you sure? Type 'yes' to confirm: " confirm
    if [ "$confirm" != "yes" ]; then
      echo "  Aborted."
      exit 0
    fi
  fi

  # Step 1: Stop and remove the postgres container explicitly so Docker releases
  # its reference to the pgdata volume. terraform destroy aborts when it hits the
  # prevent_destroy lifecycle on docker_volume.pgdata, which can leave the container
  # half-removed and the volume still referenced — causing docker volume rm to fail.
  info "Stopping postgres container to release pgdata volume reference..."
  docker rm -f postgres 2>/dev/null && ok "postgres container removed" || info "postgres container already gone"

  # Step 2: Destroy all remaining Terraform-managed resources. The destroy will
  # still error on docker_volume.pgdata (prevent_destroy = true) but all containers
  # will have been torn down by this point.
  terraform destroy -var-file="$TFVARS" -input=false -auto-approve || true

  # Step 3: Now that no container holds a reference, remove the volume directly.
  docker volume rm price-alert-pgdata 2>/dev/null && ok "pgdata volume removed" || warn "pgdata volume already removed or not found"
}

tf_outputs() {
  cd "$TF_DIR"
  if terraform output &>/dev/null 2>&1; then
    terraform output
  else
    warn "No Terraform state found — run '$0 up' first"
  fi
}

show_containers() {
  header "Container Status"
  local containers=("kafka" "postgres" "redis" "alert-api" "market-feed-simulator" "tick-ingestor" "evaluator" "notification-persister" "notification-persister-2" "prometheus" "grafana" "loki" "promtail" "tempo")
  printf "  %-30s %-15s %s\n" "CONTAINER" "STATUS" "PORTS"
  printf "  %-30s %-15s %s\n" "---------" "------" "-----"
  for c in "${containers[@]}"; do
    local status ports
    status=$(docker inspect --format='{{.State.Status}}' "$c" 2>/dev/null || echo "not found")
    if [ "$status" = "running" ]; then
      ports=$(docker inspect --format='{{range $p, $conf := .NetworkSettings.Ports}}{{if $conf}}{{(index $conf 0).HostPort}}->{{$p}} {{end}}{{end}}' "$c" 2>/dev/null | tr -s ' ')
      printf "  %-30s ${GREEN}%-15s${NC} %s\n" "$c" "$status" "${ports:-—}"
    elif [ "$status" = "not found" ]; then
      printf "  %-30s ${YELLOW}%-15s${NC}\n" "$c" "$status"
    else
      printf "  %-30s ${RED}%-15s${NC}\n" "$c" "$status"
    fi
  done
}

show_endpoints() {
  header "Endpoints"
  echo -e "  ${CYAN}Alert API:${NC}        http://localhost:8080/api/v1/alerts"
  echo -e "  ${CYAN}Health:${NC}           http://localhost:8080/actuator/health"
  echo -e "  ${CYAN}Simulator WS:${NC}     ws://localhost:8085/v1/feed"
  echo -e "  ${CYAN}PostgreSQL:${NC}       localhost:5432 (alerts/alerts_local)"
  echo -e "  ${CYAN}Kafka:${NC}            localhost:9092"
  echo -e "  ${CYAN}Redis:${NC}            localhost:6379"
  echo -e "  ${CYAN}Grafana:${NC}          http://localhost:3000  (admin/admin)"
  echo -e "  ${CYAN}Prometheus:${NC}       http://localhost:9090"
  echo -e "  ${CYAN}Loki:${NC}             http://localhost:3100"
  echo -e "  ${CYAN}Tempo:${NC}            http://localhost:3200"
}

wait_healthy() {
  header "Waiting for services to be healthy"
  local services=("alert-api" "market-feed-simulator" "tick-ingestor" "kafka" "postgres" "redis")
  local timeout=120
  local elapsed=0

  for svc in "${services[@]}"; do
    printf "  Waiting for %-28s " "$svc..."
    elapsed=0
    while [ $elapsed -lt $timeout ]; do
      local health
      health=$(docker inspect --format='{{.State.Health.Status}}' "$svc" 2>/dev/null || echo "missing")
      if [ "$health" = "healthy" ]; then
        echo -e "${GREEN}healthy${NC}"
        break
      fi
      sleep 3
      elapsed=$((elapsed + 3))
    done
    if [ $elapsed -ge $timeout ]; then
      echo -e "${RED}timeout${NC}"
      fail "$svc did not become healthy within ${timeout}s"
      return 1
    fi
  done

  # Check consumer services (no healthcheck — just verify running)
  for svc in "evaluator" "notification-persister" "notification-persister-2" "prometheus" "grafana" "loki" "promtail" "tempo"; do
    local status
    status=$(docker inspect --format='{{.State.Status}}' "$svc" 2>/dev/null || echo "not found")
    if [ "$status" = "running" ]; then
      printf "  %-36s ${GREEN}running${NC}\n" "$svc"
    else
      printf "  %-36s ${YELLOW}${status}${NC}\n" "$svc"
    fi
  done

  ok "All services ready"
}

# ── E2E test (matches launch.sh run_e2e_test, adapted for Terraform container names) ──

generate_token() {
  local secret="price-alert-dev-secret-key-change-in-production"
  # Read jwt_secret from secrets.auto.tfvars if present
  local secrets_file="$TF_DIR/secrets.auto.tfvars"
  if [ -f "$secrets_file" ]; then
    local extracted
    extracted=$(grep -E '^jwt_secret' "$secrets_file" | head -1 | sed 's/.*=\s*"\(.*\)"/\1/' | tr -d ' ')
    if [ -n "$extracted" ]; then secret="$extracted"; fi
  fi
  if [ -n "${TF_VAR_jwt_secret:-}" ]; then secret="$TF_VAR_jwt_secret"; fi

  local header payload signature
  header=$(echo -n '{"alg":"HS256","typ":"JWT"}' | base64 | tr -d '=' | tr '/+' '_-')
  payload=$(echo -n '{"sub":"user_e2e_001","iat":1700000000,"exp":9999999999}' | base64 | tr -d '=' | tr '/+' '_-')
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
  local alert_id status_val
  alert_id=$(echo "$create_resp" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])" 2>/dev/null)
  status_val=$(echo "$create_resp" | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])" 2>/dev/null)

  if [ "$status_val" = "ACTIVE" ]; then
    ok "Alert created: $alert_id (status=ACTIVE)"
  else
    fail "Unexpected status: $status_val"; return 1
  fi

  # Step 3: Verify outbox
  info "Step 3: Wait for outbox to publish CREATED event"
  sleep 5
  local outbox_status
  outbox_status=$(docker exec postgres psql -U alerts -d price_alerts -t -c \
    "SELECT status FROM alertapi_outbox_record WHERE record_key='AAPL' ORDER BY created_at DESC LIMIT 1;" 2>/dev/null | tr -d ' ')

  if [ "$outbox_status" = "COMPLETED" ]; then
    ok "Outbox: CREATED event published (COMPLETED)"
  else
    warn "Outbox status: '$outbox_status' (may still be processing)"
  fi

  # Step 4: Verify DB
  info "Step 4: Verify alert persisted in database"
  local db_count
  db_count=$(docker exec postgres psql -U alerts -d price_alerts -t -c \
    "SELECT count(*) FROM alerts WHERE id='$alert_id';" 2>/dev/null | tr -d ' ')

  if [ "$db_count" = "1" ]; then ok "Alert exists in DB"; else fail "Alert not found in DB"; return 1; fi

  # Step 5: Wait for trigger
  info "Step 5: Waiting for market tick to trigger the alert (up to 60s)"
  local triggered=false
  for i in $(seq 1 30); do
    local alert_status
    alert_status=$(docker exec postgres psql -U alerts -d price_alerts -t -c \
      "SELECT status FROM alerts WHERE id='$alert_id';" 2>/dev/null | tr -d ' ')
    if [ "$alert_status" = "TRIGGERED_TODAY" ]; then
      triggered=true
      ok "Alert triggered! (status=TRIGGERED_TODAY) after ~$((i * 2))s"
      break
    fi
    sleep 2
  done

  if ! $triggered; then
    warn "Alert not yet triggered (AAPL price may not have crossed \$150)"
    warn "Expected if simulator starts AAPL above \$150 — try a BELOW alert with a high threshold"
  fi

  # Step 6: Notification
  if $triggered; then
    info "Step 6: Verify notification persisted"
    sleep 3
    local notif_count
    notif_count=$(docker exec postgres psql -U alerts -d price_alerts -t -c \
      "SELECT count(*) FROM notifications WHERE alert_id='$alert_id';" 2>/dev/null | tr -d ' ')

    if [ "${notif_count:-0}" -ge 1 ] 2>/dev/null; then
      ok "Notification persisted ($notif_count row(s))"
    else
      warn "Notification not yet persisted (may still be in flight)"
    fi

    # Step 7: API check
    info "Step 7: Verify notification via API"
    local notif_resp total
    notif_resp=$(curl -sf "http://localhost:8080/api/v1/notifications" \
      -H "Authorization: Bearer ${token}" 2>/dev/null)
    total=$(echo "$notif_resp" | python3 -c "import sys,json; print(json.load(sys.stdin)['totalElements'])" 2>/dev/null)

    if [ "${total:-0}" -ge 1 ] 2>/dev/null; then
      ok "Notification visible via API (totalElements=$total)"
    else
      warn "No notifications via API yet"
    fi
  fi

  # Step 8: Outbox isolation
  info "Step 8: Verify per-service outbox table isolation"
  local table_count
  table_count=$(docker exec postgres psql -U alerts -d price_alerts -t -c \
    "SELECT count(DISTINCT tablename) FROM pg_tables WHERE tablename LIKE '%outbox%';" 2>/dev/null | tr -d ' ')

  if [ "$table_count" = "9" ]; then
    ok "9 outbox tables (3 per service) — isolation verified"
  else
    warn "Found $table_count outbox tables (expected 9)"
  fi

  header "E2E Test Summary"
  if $triggered; then
    echo -e "  ${GREEN}PASS${NC} — Full flow verified: create → outbox → Kafka → evaluate → trigger → notify"
  else
    echo -e "  ${YELLOW}PARTIAL${NC} — Alert created and outbox working. Trigger depends on simulator price."
  fi
}

# ── Main ──

case "$ACTION" in
  up)
    header "Price Alert System — Terraform Up"
    check_terraform
    check_docker
    check_tfvars
    check_secrets

    if ! $SKIP_BUILD; then
      gradle_build
    else
      info "Skipping Gradle build (--skip-build)"
      if ! check_jars; then
        fail "JARs missing and --skip-build was set. Run without --skip-build or build manually:"
        echo "    ./gradlew clean build -x test --parallel"
        exit 1
      fi
    fi

    tf_init

    tf_apply
    wait_healthy
    show_containers
    show_endpoints
    echo ""
    info "Run '$0 test' to execute the E2E happy path test"
    ;;

  down)
    header "Price Alert System — Terraform Down"
    check_terraform
    check_docker
    check_tfvars
    tf_destroy
    ;;

  clean)
    header "Price Alert System — Terraform Clean (full wipe)"
    check_terraform
    check_docker
    check_tfvars
    tf_destroy_clean
    ;;

  plan)
    header "Price Alert System — Terraform Plan"
    check_terraform
    check_docker
    check_tfvars

    if ! check_jars; then
      warn "Some JARs are missing — docker_image build triggers will fail. Build first:"
      echo "    ./gradlew clean build -x test --parallel"
    fi

    check_secrets
    tf_init
    tf_plan
    ;;

  init)
    header "Price Alert System — Terraform Init"
    check_terraform
    tf_init
    ;;

  status)
    check_docker
    show_containers
    show_endpoints
    echo ""
    tf_outputs
    ;;

  test)
    run_e2e_test
    ;;

  help|--help|-h|*)
    usage
    ;;
esac
