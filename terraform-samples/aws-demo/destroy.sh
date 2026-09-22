#!/usr/bin/env bash
# Tears down in the correct order: app first (its state lives IN the
# bootstrap bucket), then bootstrap (the bucket itself).
set -euo pipefail
cd "$(dirname "$0")"

echo "== 1/2: destroy app (demo bucket + object) =="
(cd app && terraform destroy -auto-approve -input=false)

echo "== 2/2: destroy bootstrap (state bucket) =="
(cd bootstrap && terraform destroy -auto-approve -input=false)

echo "Done. Both buckets removed."
