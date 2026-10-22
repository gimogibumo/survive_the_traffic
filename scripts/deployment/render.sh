#!/usr/bin/env bash
set -euo pipefail

image=${1:?Usage: render.sh ECR_IMAGE_URI}
if [[ ! "$image" =~ ^[0-9]{12}\.dkr\.ecr\.[a-z0-9-]+\.amazonaws\.com/survive-the-traffic:[a-zA-Z0-9._-]+$ ]]; then
  echo 'Expected an explicit tag in the existing survive-the-traffic ECR repository.' >&2
  exit 1
fi
repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT
cp -R "$repo_root/k8s" "$work_dir/k8s"
cat > "$work_dir/kustomization.yaml" <<EOF
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - k8s/overlays/prod
images:
  - name: survive-the-traffic
    newName: ${image%:*}
    newTag: ${image##*:}
EOF
kubectl kustomize "$work_dir"
