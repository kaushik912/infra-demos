# AWS S3 demo — manually-created state bucket

Simplest possible S3-backend setup: **you** create the state bucket by hand
(AWS CLI, one time), then point `main.tf` at it. No bootstrap config, no
chicken-and-egg problem — that's what `../aws-demo/` demonstrates instead,
if you want to see Terraform create its own state bucket.

Real AWS, cost effectively $0 (a few KB in S3). No Docker.

## Step 1 — create the state bucket manually

Bucket names are global across all of S3 (all accounts), so pick something
unique:

```bash
BUCKET=my-tf-state-$(whoami)-$(date +%s)
echo $BUCKET   # remember this — you'll need it in Step 2

aws s3api create-bucket \
  --bucket $BUCKET \
  --region ap-southeast-2 \
  --create-bucket-configuration LocationConstraint=ap-southeast-2

aws s3api put-bucket-versioning \
  --bucket $BUCKET \
  --versioning-configuration Status=Enabled

aws s3api put-bucket-encryption \
  --bucket $BUCKET \
  --server-side-encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'

aws s3api put-public-access-block \
  --bucket $BUCKET \
  --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
```

Those 4 calls do what `../aws-demo/bootstrap/` does in Terraform, by hand:
create the bucket, turn on versioning (recover old state), turn on
encryption (state can contain secrets), block all public access.

> `ap-southeast-2` is used because this account has an org-level SCP
> denying `s3:CreateBucket` in `us-east-1`. If you're on a different
> account, `us-east-1` will likely work fine — just also update `region`
> in `main.tf`.

## Step 2 — the backend block

`main.tf`'s `backend "s3" {}` deliberately leaves `bucket` out — backend
blocks can't reference variables, so it's supplied at init time instead
(Step 3). CI does the same, via a repo variable (see the CI section below).

```hcl
backend "s3" {
  key          = "aws-s3-demo/terraform.tfstate"
  region       = "ap-southeast-2"
  encrypt      = true
  use_lockfile = true
}
```

| Field | Meaning |
|---|---|
| `bucket` | The bucket from Step 1. Supplied via `-backend-config`, not hardcoded. |
| `key` | Path *inside* the bucket for this config's state file — arbitrary, just needs to be unique if the bucket is shared by multiple configs. |
| `region` | Where the bucket lives. |
| `encrypt` | Encrypts the state object at rest. |
| `use_lockfile` | State locking via an object lock in the same bucket (Terraform ≥ 1.10) — no DynamoDB table needed. |

## Step 3 — init and apply

```bash
terraform init -reconfigure -input=false -backend-config="bucket=$BUCKET"
terraform plan
terraform apply -auto-approve
```

This creates a second, separate bucket (`tf-demo-<random>`) plus a
`hello.txt` object inside it — the actual "app" resources, distinct from
the state bucket from Step 1.

## Step 4 — verify the state landed in S3

```bash
aws s3 ls s3://$BUCKET/aws-s3-demo/
aws s3 cp s3://$BUCKET/aws-s3-demo/terraform.tfstate - | head -30
```

You should see a `terraform.tfstate` object, and its JSON listing
`aws_s3_bucket.demo`, `aws_s3_object.hello`, etc. under `"resources"`.

## Step 5 — verify the resources the apply created

```bash
terraform output bucket_name
aws s3 ls
aws s3 ls s3://$(terraform output -raw bucket_name)/
aws s3 cp s3://$(terraform output -raw bucket_name)/hello.txt -
```

Or in the console: S3 → Buckets → the `tf-demo-<random>` bucket → `hello.txt`.

## Shared-state experiments, for real (locking / drift / stale config)

`demo.sh` replays the same three experiments as `../shared-state-demo/`, but
against this real S3 backend and a real S3 object instead of a local file.
Alice and Bob are simulated with two `TF_DATA_DIR`s (two "machines"), both
pointed at the one state bucket from Step 1.

```bash
./demo.sh
```

What it shows, in order:
1. **Shared state** — Bob, who never applied, plans clean because he reads Alice's state from S3.
2. **Drift** — Bob edits the real S3 object directly (`aws s3 cp`); Alice's next plan detects it and reverts it.
3. **Locking** — Alice holds a slow apply open; Bob's concurrent plan fails with a real `Error acquiring the state lock`.
4. **Stale config** — Alice ships `v2`; Bob, still on the default value, would revert it on apply. Locking doesn't stop this — only git + PR review + CI-only apply does.

Needs `main.tf`'s backend already pointing at your bucket (Steps 1–2 above).

## CI (GitHub Actions)

`.github/workflows/aws-s3-demo.yml` runs `plan` on every PR touching this
folder (posted as a PR comment) and `apply` on merge to `master` — using
the *same* S3 backend, so it sees exactly what your local runs see.

**One-time repo setup** (Settings → Secrets and variables → Actions):
- **Secrets**: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` — from the IAM user (`terraform-admin`) used throughout this demo. Static keys, simplest to start with; see the conversation notes on switching to OIDC federation (no stored keys) later.
- **Variables** (not secret): `TF_STATE_BUCKET` — the bucket name from Step 1.

That IAM user needs `s3:GetObject`/`PutObject`/`DeleteObject`/`ListBucket`
on the state bucket, plus permission to manage `tf-demo-*` buckets (create/
delete/put-object/get-object) for the actual demo resources.

## Cleanup

```bash
terraform destroy -auto-approve

# Versioning is ON for the state bucket, so a plain `aws s3 rb --force`
# will fail with BucketNotEmpty — it only removes current versions, not
# old versions or delete markers. Purge those first:
aws s3api delete-objects --bucket "$BUCKET" --delete "$(
  aws s3api list-object-versions --bucket "$BUCKET" --output json \
    | jq '{Objects: ((.Versions // []) + (.DeleteMarkers // []) | map({Key, VersionId})), Quiet: false}'
)"
aws s3api delete-bucket --bucket "$BUCKET" --region ap-southeast-2
```

`terraform destroy` only removes what Terraform manages (the demo bucket +
object) — the state bucket itself was created outside Terraform, so it has
to be removed outside Terraform too, and needs the version-purge step
above because it's versioned.
