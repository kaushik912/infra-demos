#!/usr/bin/env bash
# Simulates two teammates sharing one state file. Each has own TF_DATA_DIR (= own machine's .terraform).
set -u
cd "$(dirname "$0")"
rm -rf shared target .alice .bob
mkdir -p shared
alice() { TF_DATA_DIR=.alice terraform "$@"; }
bob()   { TF_DATA_DIR=.bob   terraform "$@"; }
step()  { printf '\n\n=== %s ===\n' "$*"; }

alice init -input=false >/dev/null; bob init -input=false >/dev/null

step "1. Alice applies v1 -> state written to shared/terraform.tfstate"
alice apply -auto-approve -input=false -no-color | grep -E 'Apply complete|created'

step "2. Bob (never applied) plans -> sees Alice's state, so NO changes"
bob plan -input=false -no-color | grep -E 'No changes|Plan:'

step "3. DRIFT: Bob hand-edits target/app.conf outside Terraform"
echo "greeting=HACKED" > target/app.conf
cat target/app.conf

step "4. Alice plans -> refresh detects real file != state; proposes to revert"
alice plan -input=false -no-color | grep -E 'changed outside|will be|Plan:|greeting|content'

step "5. Alice applies -> drift reverted"
alice apply -auto-approve -input=false -no-color | grep -E 'Apply complete'
cat target/app.conf

step "6. LOCK: Alice runs slow apply (holds lock 6s); Bob plans meanwhile"
alice apply -auto-approve -input=false -var delay=6 -no-color >/dev/null 2>&1 &
sleep 2
bob plan -input=false -no-color 2>&1 | grep -E 'Error|Lock Info|Operation|Who|ID:' 
wait

step "7. STALE CONFIG: Bob (old code, greeting=v1) plans after Alice shipped v2"
alice apply -auto-approve -input=false -var greeting=v2 -var delay=6 -no-color | grep -E 'Apply complete'
bob plan -input=false -var delay=6 -no-color | grep -E 'will be|Plan:|greeting'
echo "(-> Bob's plan would REVERT Alice's v2. State is truth; last apply wins. Hence: config in git + PR review + CI apply.)"
