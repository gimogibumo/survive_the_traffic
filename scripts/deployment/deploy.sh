#!/usr/bin/env bash
set -euo pipefail

mode=${1:?Usage: deploy.sh deploy|rollback ECR_IMAGE_URI RELEASE_ID}
image=${2:?An explicit image URI is required}
release=${3:?Use RUN_ID-RUN_ATTEMPT or a unique manual release ID}
if [[ "$mode" != deploy && "$mode" != rollback ]]; then
  echo 'Mode must be deploy or rollback.' >&2
  exit 1
fi
if [[ ! "$release" =~ ^[a-z0-9-]{1,28}$ ]]; then
  echo 'Release ID must be 1-28 lowercase letters, digits or hyphens.' >&2
  exit 1
fi
if [[ "$mode" == deploy && ! "${image##*:}" =~ ^[0-9a-f]{40}$ ]]; then
  echo 'Deployment requires a full commit SHA image tag.' >&2
  exit 1
fi
repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT
bash "$repo_root/scripts/deployment/render.sh" "$image" > "$work_dir/application.yaml"
# Validate against the current API before touching the DB.
kubectl apply --dry-run=server -f "$work_dir/application.yaml" > /dev/null
for deployment in ticketing-api ticketing-queue-worker; do
  previous=$(kubectl get deployment "$deployment" -n ticketing \
    -o jsonpath='{.spec.template.spec.containers[0].image}')
  echo "Previous $deployment image: $previous"
done

if [[ "$mode" == deploy ]]; then
  job="ticketing-migration-${image##*:}"
  job="${job:0:32}-$release"
  kubectl create --dry-run=client --validate=false \
    -f "$repo_root/k8s/base/migration-job.yaml" -o json > "$work_dir/migration.json"
  python3 - "$work_dir/migration.json" "$job" "$image" <<'PY'
import json, sys
path, name, image = sys.argv[1:]
with open(path) as source:
    job = json.load(source)
job['metadata']['name'] = name
job['spec']['template']['spec']['containers'][0]['image'] = image
with open(path, 'w') as output:
    json.dump(job, output)
PY
  kubectl create -f "$work_dir/migration.json"
  # A failed or timed-out migration stops execution before application apply.
  if ! kubectl wait --for=condition=complete "job/$job" -n ticketing --timeout=330s; then
    kubectl get job "$job" -n ticketing
    echo 'Migration did not complete; application rollout was not started.' >&2
    exit 1
  fi
fi

kubectl apply -f "$work_dir/application.yaml"
kubectl rollout status deployment/ticketing-api -n ticketing --timeout=300s
kubectl rollout status deployment/ticketing-queue-worker -n ticketing --timeout=180s
# Non-web workers have no HTTP readiness probe; additionally require a stable pod.
sleep 10
kubectl get pods -n ticketing -l app=ticketing-queue-worker -o json > "$work_dir/worker.json"
python3 - "$work_dir/worker.json" <<'PY'
import json, sys
with open(sys.argv[1]) as source:
    pods = [p for p in json.load(source)['items'] if not p['metadata'].get('deletionTimestamp')]
assert len(pods) == 1, 'Expected exactly one worker pod'
statuses = pods[0]['status'].get('containerStatuses', [])
assert statuses and all(s.get('ready') and s['restartCount'] == 0 for s in statuses), \
    'Worker is not stable; inspect its status as an operator'
PY
bash "$repo_root/scripts/deployment/smoke.sh"
