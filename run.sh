#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker-compose-test.yml"
ENV_FILE="$ROOT_DIR/.env"
API_DIR="$ROOT_DIR/apps/api"
MOBILE_DIR="$ROOT_DIR/apps/mobile"

API_PID=""
WEB_PID=""

cleanup() {
  trap - INT TERM EXIT

  echo ""
  echo "Stopping RackPay development services..."

  if [[ -n "$WEB_PID" ]] && kill -0 "$WEB_PID" >/dev/null 2>&1; then
    kill "$WEB_PID" >/dev/null 2>&1 || true
  fi

  if [[ -n "$API_PID" ]] && kill -0 "$API_PID" >/dev/null 2>&1; then
    kill "$API_PID" >/dev/null 2>&1 || true
  fi

  wait "$WEB_PID" "$API_PID" 2>/dev/null || true
}

trap cleanup INT TERM EXIT

echo ""
echo "============================================================"
echo " RackPay Local Development"
echo "============================================================"
echo ""

for command in docker java; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "ERROR: $command is not installed or not available in PATH."
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
  echo "No root .env file found."
  echo "Creating .env from .env.example..."
  cp "$ROOT_DIR/.env.example" "$ENV_FILE"
fi

if [[ ! -f "$MOBILE_DIR/.env" ]]; then
  echo "No mobile .env file found."
  echo "Creating apps/mobile/.env from .env.example..."
  cp "$MOBILE_DIR/.env.example" "$MOBILE_DIR/.env"
fi

if command -v pnpm >/dev/null 2>&1; then
  PACKAGE_MANAGER="pnpm"
elif command -v npm >/dev/null 2>&1; then
  PACKAGE_MANAGER="npm"
else
  echo "ERROR: pnpm or npm is required to run the mobile frontend."
  exit 1
fi

echo "Starting PostgreSQL and Keycloak..."
docker compose -f "$COMPOSE_FILE" up -d

echo ""
echo "Waiting for PostgreSQL..."
until docker compose -f "$COMPOSE_FILE" exec -T postgres pg_isready -U rackpay -d rackpay >/dev/null 2>&1; do
  sleep 1
done
echo "PostgreSQL is ready."

echo ""
echo "Stopping any background Gradle processes..."
gradle --stop >/dev/null 2>&1 || true

set -a
source "$ENV_FILE"
set +a

echo ""
echo "Starting RackPay API on http://localhost:8080..."
(
  cd "$ROOT_DIR"
  gradle -p "$API_DIR" bootRun --no-daemon
) &
API_PID=$!

echo "Starting RackPay mobile frontend on http://localhost:5173..."
(
  cd "$MOBILE_DIR"
  if [[ "$PACKAGE_MANAGER" == "pnpm" ]]; then
    pnpm dev --host 0.0.0.0
  else
    npm run dev -- --host 0.0.0.0
  fi
) &
WEB_PID=$!

echo ""
echo "RackPay is running:"
echo "  Frontend: http://localhost:5173"
echo "  API:      http://localhost:8080"
echo ""
echo "Press Ctrl+C to stop the frontend and backend."
echo ""

wait -n "$API_PID" "$WEB_PID"
STATUS=$?

echo ""
echo "A RackPay development process stopped (exit code $STATUS)."
exit "$STATUS"
