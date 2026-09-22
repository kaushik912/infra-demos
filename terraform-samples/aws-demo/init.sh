#!/usr/bin/env bash
# Bootstraps the state bucket, then initializes app/ against it.
set -euo pipefail
cd "$(dirname "$0")"

echo "== 1/2: bootstrap (creates the S3 state bucket, local state) =="
(cd bootstrap && terraform init -input=false && terraform apply -auto-approve -input=false)
BUCKET=$(cd bootstrap && terraform output -raw bucket_name)
echo "State bucket: $BUCKET"

echo "== 2/2: app (points its S3 backend at that bucket) =="
(cd app && terraform init -input=false -backend-config="bucket=$BUCKET" -reconfigure)

echo
echo "Ready. Next:"
echo "  cd app && terraform plan"
echo "  cd app && terraform apply"
