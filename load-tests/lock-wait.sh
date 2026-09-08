#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

base_url=${BASE_URL:-http://localhost:8080}
mkdir -p load-tests/results
curl -fsS -X POST "$base_url/api/performance/seed/hot-seat?userCount=100" \
  -o load-tests/results/hot-seat-lock-seed.json
seat_id=$(jq -er '.seatIds[0]' load-tests/results/hot-seat-lock-seed.json)

docker exec -i ticketing-postgres psql -U ticketing -d ticketing \
  -v seat_id="$seat_id" -v hold_seconds=10 \
  < load-tests/hold-seat-lock.sql > load-tests/results/lock-holder.txt &
holder=$!
trap 'wait "$holder" || true' EXIT

# Wait until the holder has acquired the row lock and entered pg_sleep.
ready=false
for attempt in {1..50}; do
  if [[ $(docker exec ticketing-postgres psql -U ticketing -d ticketing -Atc \
    "SELECT count(*) FROM pg_stat_activity WHERE application_name = 'phase3-lock-holder' AND wait_event = 'PgSleep'") == 1 ]]; then
    ready=true
    break
  fi
  sleep 0.1
done
[[ "$ready" == true ]]

BASE_URL="$base_url" SEED_FILE=../results/hot-seat-lock-seed.json VUS=100 ITERATIONS=100 \
  k6 run --quiet --summary-export load-tests/results/hot-seat-lock-vus100.json \
  load-tests/k6/hot-seat-reservation.js &
runner=$!
trap 'wait "$runner" || true; wait "$holder" || true' EXIT

sleep 1
docker exec ticketing-postgres psql -U ticketing -d ticketing -c \
  "SELECT wait_event_type, wait_event, count(*) FROM pg_stat_activity WHERE datname = current_database() GROUP BY wait_event_type, wait_event ORDER BY 1, 2" \
  > load-tests/results/lock-waits.txt
curl -fsS "$base_url/actuator/metrics/hikaricp.connections.pending" \
  -o load-tests/results/hikari-lock-pending.json
wait "$runner"
wait "$holder"
trap - EXIT
