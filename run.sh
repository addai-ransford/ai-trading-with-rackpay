#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker-compose-test.yml"
ENV_FILE="$ROOT_DIR/.env"
API_DIR="$ROOT_DIR/apps/api"
MOBILE_DIR="$ROOT_DIR/apps/mobile"
LOG_DIR="$ROOT_DIR/.run-logs"
API_LOG="$LOG_DIR/api.log"
FRONTEND_LOG="$LOG_DIR/frontend.log"
API_PORT=8080
FRONTEND_PORT=5173

cd "$ROOT_DIR"

echo ""
echo "============================================================"
echo " RackPay Local Development"
echo "============================================================"
echo ""

for command in docker java node pnpm lsof; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "ERROR: Required command '$command' was not found in PATH."
    case "$command" in
      java) echo "Install Java 21." ;;
      node|pnpm) echo "Install Node.js and pnpm (Corepack is recommended)." ;;
      docker) echo "Install Docker Desktop and start it." ;;
      lsof) echo "Install lsof so run.sh can free the development ports." ;;
    esac
    exit 1
  fi
done

if ! docker info >/dev/null 2>&1; then
  echo "ERROR: Docker is not running. Start Docker Desktop and try again."
  exit 1
fi

JAVA_MAJOR="$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)"
if [[ "$JAVA_MAJOR" != "21" ]]; then
  echo "WARNING: RackPay requires Java 21. Detected Java ${JAVA_MAJOR:-unknown}."
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "No .env file found. Creating it from .env.example..."
  cp "$ROOT_DIR/.env.example" "$ENV_FILE"
  echo "Created $ENV_FILE. Review its local credentials before continuing."
fi

# docker-compose-test.yml publishes PostgreSQL on host port 5435.
# Keep the generated local configuration aligned with that mapping.
if grep -q '^RACKPAY_DATABASE_URL=jdbc:postgresql://localhost:5432/rackpay$' "$ENV_FILE"; then
  sed -i.bak 's#^RACKPAY_DATABASE_URL=jdbc:postgresql://localhost:5432/rackpay$#RACKPAY_DATABASE_URL=jdbc:postgresql://localhost:5435/rackpay#' "$ENV_FILE"
  rm -f "$ENV_FILE.bak"
  echo "Adjusted local PostgreSQL URL to port 5435 (docker-compose-test.yml)."
fi

free_port() {
  local port="$1"
  local pids
  pids="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
  if [[ -n "$pids" ]]; then
    echo "Stopping existing process(es) listening on port $port: $pids"
    # Graceful shutdown first, then force only processes that remain.
    kill $pids 2>/dev/null || true
    for _ in {1..10}; do
      if ! lsof -tiTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
        break
      fi
      sleep 1
    done
    pids="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
    if [[ -n "$pids" ]]; then
      echo "Force-stopping remaining process(es) on port $port: $pids"
      kill -9 $pids 2>/dev/null || true
    fi
  else
    echo "Port $port is free."
  fi
}

# Clear stale app servers before bringing either app up. Database and Keycloak
# ports are managed by Docker Compose and are intentionally not killed here.
free_port "$API_PORT"
free_port "$FRONTEND_PORT"

echo ""
echo "Starting PostgreSQL and Keycloak..."
docker compose -f "$COMPOSE_FILE" up -d

echo "Waiting for PostgreSQL..."
until docker compose -f "$COMPOSE_FILE" exec -T postgres pg_isready -U rackpay -d rackpay >/dev/null 2>&1; do
  sleep 1
done
echo "PostgreSQL is ready."

# Export the local backend environment.
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

mkdir -p "$LOG_DIR"

if [[ ! -d "$ROOT_DIR/node_modules" ]]; then
  echo ""
  echo "Installing workspace dependencies with pnpm..."
  pnpm install
fi

if [[ ! -d "$MOBILE_DIR/node_modules" && ! -d "$ROOT_DIR/node_modules/.pnpm" ]]; then
  echo "ERROR: Frontend dependencies are not installed. Run 'pnpm install' from the repository root."
  exit 1
fi

API_PID=""
cleanup() {
  local exit_code=$?
  trap - EXIT INT TERM
  if [[ -n "$API_PID" ]] && kill -0 "$API_PID" 2>/dev/null; then
    echo ""
    echo "Stopping RackPay API (PID $API_PID)..."
    kill "$API_PID" 2>/dev/null || true
    wait "$API_PID" 2>/dev/null || true
  fi
  exit "$exit_code"
}
trap cleanup EXIT INT TERM

echo ""
echo "Starting RackPay API on http://localhost:$API_PORT ..."
gradle -p "$API_DIR" bootRun --no-daemon >"$API_LOG" 2>&1 &
API_PID=$!
echo "API logs: $API_LOG"

echo "Waiting for API startup (up to 120 seconds)..."
API_READY=0
for _ in {1..120}; do
  if ! kill -0 "$API_PID" 2>/dev/null; then
    echo "ERROR: API process exited. Recent log output:"
    tail -n 80 "$API_LOG" || true
    exit 1
  fi
  if curl --silent --fail "http://localhost:$API_PORT/actuator/health" >/dev/null 2>&1; then
    API_READY=1
    break
  fi
  sleep 1
done
if [[ "$API_READY" -ne 1 ]]; then
  echo "WARNING: API health endpoint did not respond within 120 seconds."
  echo "The frontend will still start. Check API logs: $API_LOG"
fi

echo ""
echo "Starting RackPay frontend on http://localhost:$FRONTEND_PORT ..."
echo "Frontend logs: $FRONTEND_LOG"
echo ""
echo "RackPay is starting:"
echo "  Frontend: http://localhost:$FRONTEND_PORT"
echo "  API:      http://localhost:$API_PORT"
echo "  Keycloak: http://localhost:8081"
echo ""
echo "Press Ctrl+C to stop the frontend and API. PostgreSQL and Keycloak stay running in Docker."
echo ""

pnpm --filter @rackpay/mobile dev -- --host 0.0.0.0 2>&1 | tee "$FRONTEND_LOG"
