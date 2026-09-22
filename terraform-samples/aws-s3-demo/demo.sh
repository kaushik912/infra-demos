#!/usr/bin/env bash
# Real AWS versions of the locking / drift / stale-config experiments from
# shared-state-demo/ — same ideas, but against a real S3 backend and a real
# S3 object instead of a local file. Alice and Bob = two TF_DATA_DIRs,
# standing in for two machines sharing one state bucket.
#
# Prerequisite: the state bucket from README.md Step 1, and main.tf's
# backend block already pointing at it.
set -euo pipefail
cd "$(dirname "$0")"

alice() { TF_DATA_DIR=.alice terraform "$@"; }
bob()   { TF_DATA_DIR=.bob   terraform "$@"; }
step()  { printf '\n\n=== %s ===\n' "$*"; }

alice init -input=false -reconfigure >/dev/null
bob   init -input=false -reconfigure >/dev/null

step "1. Alice applies -> real S3 bucket + object created, state written to S3"
alice apply -auto-approve -input=false -no-color | grep -E 'Apply complete|created'
BUCKET=$(alice output -raw bucket_name)
echo "Demo bucket: $BUCKET"

step "2. Bob (never applied) plans -> sees Alice's state via S3, so NO changes"
bob plan -input=false -no-color | grep -E 'No changes|Plan:'

step "3. DRIFT: Bob hand-edits the real S3 object outside Terraform"
echo "HACKED" | aws s3 cp - "s3://$BUCKET/hello.txt" --content-type text/plain
aws s3 cp "s3://$BUCKET/hello.txt" -

step "4. Alice plans -> refresh reads the real object, sees it != state; proposes to revert"
alice plan -input=false -no-color | grep -E 'must be replaced|will be updated|Plan:|content'

step "5. Alice applies -> drift reverted on the real object"
alice apply -auto-approve -input=false -no-color | grep -E 'Apply complete'
aws s3 cp "s3://$BUCKET/hello.txt" -

step "6. LOCK: Alice runs a slow apply (holds the REAL S3 lock ~8s); Bob plans meanwhile"
alice apply -auto-approve -input=false -var delay=8 -no-color >/dev/null 2>&1 &
sleep 3
bob plan -input=false -no-color 2>&1 | grep -E 'Error|Lock Info|Operation|Who|ID:' || echo "(lock already released — try a longer delay)"
wait

step "7. STALE CONFIG: Alice ships v2; Bob (old code, default greeting) plans/applies after"
alice apply -auto-approve -input=false -var 'greeting=v2 from Alice' -var delay=8 -no-color | grep -E 'Apply complete'
aws s3 cp "s3://$BUCKET/hello.txt" -
bob plan -input=false -var delay=8 -no-color | grep -E 'must be replaced|will be updated|Plan:|content'
echo "(-> Bob's plan would REVERT Alice's v2 back to the default greeting. State is truth from Terraform's view; last apply wins. Hence: config in git + PR review + CI-only apply.)"

step "cleanup"
echo "Run: alice apply -auto-approve -var delay=0 (restore default), or terraform destroy when done."
