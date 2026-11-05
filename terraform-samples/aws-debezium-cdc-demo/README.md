# AWS: run debezium-cdc-demo on one EC2 box

Terraform provisions a single EC2 (Docker + Java 17 preinstalled). You then
run the existing `../../debezium-cdc-demo/compose.yaml` + Spring app on it.

Cost: `m7i-flex.large` (8 GB) ~ $0.10/hr (~$2.4/day), covered by credits. This
account only allows free-tier-eligible instance types, so `t3.large` is rejected
(`InvalidParameterCombination ... not eligible for Free Tier`). List allowed ones:
`aws ec2 describe-instance-types --filters Name=free-tier-eligible,Values=true`.
**Destroy when done.** No NAT, no RDS, no MSK.

## Layout

```
aws-debezium-cdc-demo/
├── main.tf        # EC2, SG (SSH from your IP only), key pair, optional budget alert
├── variables.tf
├── outputs.tf     # ready-made ssh / tunnel / rsync commands
├── user-data.sh   # installs docker, compose, java 17 on first boot
└── terraform.tfvars.example
```

Local state (throwaway demo; see `../aws-demo` for the S3 backend pattern).
Region `ap-southeast-2` (account SCP, see `../aws-demo/README.md`).

## 1. Provision

```bash
cp terraform.tfvars.example terraform.tfvars
# edit: my_ip_cidr = "$(curl -s ifconfig.me)/32"
terraform init
terraform apply
```

Wait ~1-2 min for user-data, then check:

```bash
$(terraform output -raw ssh) 'ls /var/lib/cloud/instance/user-data-done && docker compose version'
```

## 2. Copy the project (from `infra-demos/`)

```bash
cd ../..   # infra-demos/
$(terraform -chdir=terraform-samples/aws-debezium-cdc-demo output -raw rsync_project)
```

## 3. Run the demo (on the box)

```bash
cd terraform-samples/aws-debezium-cdc-demo   # from infra-demos/
$(terraform output -raw ssh)       # new login so docker group applies
cd debezium-cdc-demo
docker compose up -d
nohup ./mvnw spring-boot:run > app.log 2>&1 &    # creates the table; wait ~30s
docker compose exec db psql -U postgres -d debezium -c "ALTER TABLE policy REPLICA IDENTITY FULL;"
curl -s -X POST -H "Content-Type: application/json" --data @connector/policy-connector.json localhost:8083/connectors
curl -s localhost:8083/connectors/policy-connector/status
```

Exercise + watch (CRUD curls: see `debezium-cdc-demo/README.md`):

```bash
curl -s -X POST localhost:8080/api/policies -H 'Content-Type: application/json' -d '{...}'
tail -f app.log          # consumer logs op c/u/d
```

Optional tunnel to hit the API from your laptop:

```bash
$(terraform output -raw ssh_tunnel)    # then localhost:8080 / :8083 locally
```

## 4. Teardown

On the box first (orphan slot bloats WAL, though the volume dies anyway):

```bash
curl -X DELETE localhost:8083/connectors/policy-connector
docker compose exec db psql -U postgres -d debezium -c "SELECT pg_drop_replication_slot('policy_slot');"
docker compose down -v
```

Then locally:

```bash
terraform destroy
```

Pause instead of destroy: stop the instance in console (compute billing stops;
the 20 GB EBS still bills ~$0.07/day).

## Safety notes

- Only port 22 is open, to `my_ip_cidr`. Kafka advertises `localhost:9092`, so
  it only works on-box anyway. Never open 5433/9092/8083 publicly.
- Set `budget_email` for an 80% spend alert at `budget_usd` (default $30).
- Real RDS/MSK variant is intentionally not here: MSK alone is ~$100+/mo.
