# AWS: run spring-boot-h2-sample on one EC2 box

Single EC2 (Docker + Java 25). Zookeeper + Kafka run via the app's `compose.yml`;
the Spring app runs with `./mvnw spring-boot:run`. H2 is in-memory, so no DB to
provision. No secrets needed (H2 creds are in the repo, DB dies with the JVM).

- Access: SSH tunnel only (SG = SSH from your IP).
- Cost: `m7i-flex.large` ~ $0.10/hr. **Destroy when done.**

## 1. Provision

```bash
cp terraform.tfvars.example terraform.tfvars   # set my_ip_cidr
terraform init && terraform apply
$(terraform output -raw ssh) 'ls /var/lib/cloud/instance/user-data-done && docker compose version && java -version'
```

## 2. Copy the project

```bash
$(terraform output -raw rsync_project)
```

## 3. Start Kafka + app (on the box)

```bash
$(terraform output -raw ssh)       # fresh login so docker group applies
cd spring-boot-h2-sample
docker compose up -d               # zookeeper + kafka
nohup ./mvnw spring-boot:run > app.log 2>&1 &
tail -f app.log                    # wait for "Started SpringBootH2SampleApplication"
```

## 4. Test (on the box)

```bash
curl -s localhost:8080/save                 # loads 100k students into H2 (watch app.log for start/done)
curl -s localhost:8080/send-to-kafka-broker # publishes them to topic student_topic

# verify messages landed
docker compose exec kafka kafka-run-class org.apache.kafka.tools.GetOffsetShell \
  --bootstrap-server localhost:9092 --topic student_topic
docker compose exec kafka kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic student_topic --from-beginning --max-messages 3
```
Expected: offset `student_topic:0:100000` (sending is async, may take a few seconds).
Run `/save` only once, or you get 200k rows.

## 5. H2 console from laptop

```bash
$(terraform output -raw ssh_tunnel)   # keep open
```
If local port 8080 is busy (`bind: Address already in use`), tunnel to another port:
`ssh -L 9090:localhost:8080 ec2-user@<ip>` and use `localhost:9090` below.

Open http://localhost:8080/h2-console. Use the **full** app URL as JDBC URL:
`jdbc:h2:mem:testdb?serverTimezone=UTC&cachePrepStmts=true&useServerPrepStmts=true&rewriteBatchedStatements=true`
(H2 treats the `?...` part as part of the in-memory DB name, so plain `jdbc:h2:mem:testdb`
gives "Database not found"). User `yu71`, password `53cret`, then
`SELECT COUNT(*) FROM student;` (table name may differ).

Boot 4 note: the console needs the `spring-boot-h2console` dependency, which the
sample's `pom.xml` lacked (404 "No static resource h2-console" without it).

## Teardown

```bash
terraform destroy
```
Local state (throwaway demo).
