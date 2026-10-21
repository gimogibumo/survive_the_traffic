#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
COOKIE_JAR="${COOKIE_JAR:-/tmp/stt-actuator.cookies}"
INTERVAL="${INTERVAL:-0.2}"
SAMPLES="${SAMPLES:-75}"
OUTPUT="${OUTPUT:-load-tests/results/hikari-samples.csv}"

mkdir -p "$(dirname "$OUTPUT")"

metric_value() {

    local metric="$1"

    curl -fsS \
        -b "$COOKIE_JAR" \
        "$BASE_URL/actuator/metrics/$metric" \
    | python3 -c '
import json
import sys

data = json.load(sys.stdin)

print(
    data["measurements"][0]["value"]
)
'
}

echo \
"timestamp,total,active,idle,pending" \
> "$OUTPUT"

for ((i = 0; i < SAMPLES; i++))
do

    timestamp=$(
        python3 -c '
import time
print(f"{time.time():.3f}")
'
    )

    total=$(
        metric_value \
            "hikaricp.connections"
    )

    active=$(
        metric_value \
            "hikaricp.connections.active"
    )

    idle=$(
        metric_value \
            "hikaricp.connections.idle"
    )

    pending=$(
        metric_value \
            "hikaricp.connections.pending"
    )

    echo \
"$timestamp,$total,$active,$idle,$pending" \
    | tee -a "$OUTPUT"

    sleep "$INTERVAL"

done