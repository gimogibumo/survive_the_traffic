#!/usr/bin/env bash
set -euo pipefail

host=$(kubectl get ingress ticketing-api -n ticketing \
  -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
if [[ ! "$host" =~ ^[a-zA-Z0-9.-]+\.elb\.amazonaws\.com$ ]]; then
  echo 'ALB hostname is missing or invalid.' >&2
  exit 1
fi
for route in /actuator/health/readiness /api/concerts; do
  success=false
  for attempt in {1..12}; do
    status=$(curl --silent --output /dev/null --write-out '%{http_code}' \
      --connect-timeout 5 --max-time 10 "http://$host$route") || status=000
    if [[ "$status" == 200 ]]; then
      success=true
      break
    fi
    sleep 5
  done
  echo "$route HTTP $status"
  if [[ "$success" != true ]]; then
    exit 1
  fi
done
