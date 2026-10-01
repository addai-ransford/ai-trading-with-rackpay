#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker-compose-test.yml"
ENV_FILE="$ROOT_DIR/.env"
API_DIR="$ROOT_DIR/apps/api"

echo ""
echo "============================================================"
echo " RackPay Local Development"
echo "============================================================"
echo ""

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: Docker is not installed or not available in PATH."
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "ERROR: Docker is not running. Start Docker Desktop and try again."
  exit 1
fi

if ! command -v java >/dev/null 2>&1; then
  echo "ERROR: Java is not installed or not available in PATH."
  echo "RackPay requires Java 21."
  exit 1
fi

JAVA_MAJOR="$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)"
if [[ "$JAVA_MAJOR" != "21" ]]; then
  echo "WARNING: RackPay requires Java 21. Detected Java ${JAVA_MAJOR:-unknown}."
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "No .env file found."
  echo "Creating .env from .env.example..."
  cp "$ROOT_DIR/.env.example" "$ENV_FILE"
  echo ""
  echo "Created $ENV_FILE."
  echo "Add your local Keycloak/provider sandbox credentials if needed."
  echo ""
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

# Stop any running Gradle daemons to clear stale processes
echo "Stopping any background Gradle processes..."
gradle --stop >/dev/null 2>&1 || true

# Export environment variables from .env
set -a
source "$ENV_FILE"
set +a

echo "Starting RackPay API..."
echo ""

# Execute bootRun as the main process
exec gradle -p "$API_DIR" bootRun --no-daemon