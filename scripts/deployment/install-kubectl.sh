#!/usr/bin/env bash
set -euo pipefail

# Match the existing EKS minor version. Install into the requested writable path.
destination=${1:?Usage: install-kubectl.sh DESTINATION_DIRECTORY}
version=v1.37.1
system=$(uname -s | tr '[:upper:]' '[:lower:]')
case "$(uname -m)" in
  x86_64) arch=amd64 ;;
  arm64|aarch64) arch=arm64 ;;
  *) echo 'Unsupported architecture' >&2; exit 1 ;;
esac
mkdir -p "$destination"
work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT
base="https://dl.k8s.io/release/$version/bin/$system/$arch"
curl --fail --silent --show-error --location "$base/kubectl" -o "$work_dir/kubectl"
curl --fail --silent --show-error --location "$base/kubectl.sha256" -o "$work_dir/kubectl.sha256"
python3 - "$work_dir" <<'PY'
import hashlib, pathlib, sys
root = pathlib.Path(sys.argv[1])
assert hashlib.sha256((root / 'kubectl').read_bytes()).hexdigest() == \
    (root / 'kubectl.sha256').read_text().strip(), 'kubectl checksum mismatch'
PY
chmod +x "$work_dir/kubectl"
cp "$work_dir/kubectl" "$destination/kubectl"
