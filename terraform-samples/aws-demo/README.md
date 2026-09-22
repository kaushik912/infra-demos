# AWS demo: S3 remote state + a tiny S3 resource

Real AWS, real S3 backend, cost effectively $0 (a few KB in S3 = fractions
of a cent/month, well under your AWS credit). No Docker.

## Layout

```
aws-demo/
├── bootstrap/     # creates the S3 bucket that stores state — uses LOCAL state itself
├── app/           # the actual demo resources — uses the bootstrap bucket as its S3 backend
├── init.sh        # runs both steps, wires app's backend to bootstrap's bucket
└── destroy.sh      # tears down in the right order
```

## Why two configs (bootstrap + app)?

A config using an S3 backend needs the bucket to **already exist** before
`terraform init` runs — Terraform doesn't create its own backend as part of
the same apply. So:

1. `bootstrap/` creates the bucket, using the **local** backend (has to —
   there's no bucket yet for it to point at).
2. `app/` uses that bucket as an **S3 backend**, and creates the actual demo
   resources.

This is a standard pattern, not specific to this demo.

## Run it

```
./init.sh
cd app
terraform plan
terraform apply -auto-approve
```

## Key S3 backend fields (`app/main.tf`)

```hcl
backend "s3" {
  key          = "aws-demo/terraform.tfstate"
  region       = "ap-southeast-2"
  encrypt      = true
  use_lockfile = true
}
```

**Note:** this account has an org-level SCP (Service Control Policy) that
denies `s3:CreateBucket` in `us-east-1` but allows it in `ap-southeast-2` —
found by trial. If you hit `AccessDenied ... explicit deny in a service
control policy` on `apply`, it's not an IAM problem; try a different region.

| Field | Meaning |
|---|---|
| `bucket` | Which bucket holds the state. **Omitted here** — backend blocks can't reference variables/resources, so it's passed at init time: `terraform init -backend-config="bucket=<name>"`. `init.sh` does this for you, reading it from bootstrap's output. |
| `key` | Path *inside* the bucket for this state file, e.g. `aws-demo/terraform.tfstate`. Lets one bucket hold state for many configs, each at its own key. |
| `region` | Region the bucket lives in. |
| `encrypt` | Encrypts the state object at rest (SSE-S3). |
| `use_lockfile` | Turns on state locking using an object lock in the same bucket (Terraform ≥ 1.10). Older guides use a separate DynamoDB table for this — not needed anymore. |

Two more S3-specific things worth knowing, not used here but common in real
setups:
- **`dynamodb_table`** — the pre-1.10 way to lock state; `use_lockfile` replaces it for new setups.
- **`profile`** — pick an `~/.aws/credentials` profile instead of the default one.

## What `bootstrap/` locks down (and why)

- `aws_s3_bucket_versioning` — keeps old state versions, so a bad apply's
  state can be recovered from bucket version history.
- `aws_s3_bucket_server_side_encryption_configuration` — encrypts state at
  rest (state can contain secrets in resource attributes).
- `aws_s3_bucket_public_access_block` — blocks all public access. State
  must never be world-readable.
- `force_destroy = true` — lets `terraform destroy` remove the bucket even
  if objects remain inside. Convenient for a practice account; wouldn't
  set this on a real team's state bucket.

## What `app/` creates

- One S3 bucket (`tf-practice-demo-<random>`), public access blocked.
- One object (`hello.txt`) in it, containing `var.greeting`.

This mirrors the earlier `local_file` demo — you can replay the same drift
experiment (edit the object outside Terraform via `aws s3 cp`, then
`terraform plan`) against a real backend.

## Cost

- S3 storage/requests for a few KB across two buckets: fractions of a cent.
- No EC2, NAT gateway, load balancer, or RDS — the usual cost traps — are
  used anywhere in this demo.
- `force_destroy = true` means `destroy.sh` fully cleans up; nothing is
  left running.

## Cleanup

```
./destroy.sh
```

Destroys `app/` first (its state lives inside the bootstrap bucket), then
`bootstrap/` (the bucket itself). Doing it in the other order would delete
the bucket while app's state still points at it.
