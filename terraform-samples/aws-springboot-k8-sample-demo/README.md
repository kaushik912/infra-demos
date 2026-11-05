# AWS: run springboot-k8-sample on single-node Kubernetes (k3s on EC2)

Single EC2 with Docker (image build) + k3s (Kubernetes). No EKS (control plane
~$0.10/hr extra + nodes + ECR). Manifests (`k8s.yaml`) and a working
`Dockerfile.aws` live here because the repo has neither a usable Dockerfile nor manifests:

- Repo Dockerfile uses `gradle:8.7`; Boot 4.1 needs the wrapper's Gradle 9.x.
- Its `ENTRYPOINT ["java","-jar","app/app-*.jar"]` never glob-expands (exec form).
- Its `build.gradle` sets `sourceCompatibility` at project level, which Gradle 9 removed
  ("Could not set unknown property 'sourceCompatibility'"). Fixed in the local copy of the
  repo's `build.gradle` by moving it into a `java {}` block (`git diff` shows the change).

The app exposes `/test/isReady`, `/test/isAlive`, `POST /test/echo`; `k8s.yaml` maps them
to readiness / liveness probes (2 replicas, NodePort 30080).

- Access: SSH tunnel only (SG = SSH from your IP). k3s API + NodePort not exposed.
- Secrets: none.
- Cost: `c7i-flex.large` (4 GB) ~ $0.09/hr. **Destroy when done.** If the type is
  rejected, set `instance_type = "m7i-flex.large"`. Free plan can't resize in place.

## 1. Provision

```bash
cp terraform.tfvars.example terraform.tfvars   # set my_ip_cidr (check last digit vs: curl -s ifconfig.me)
terraform init && terraform apply
$(terraform output -raw ssh) 'ls /var/lib/cloud/instance/user-data-done && k3s kubectl get nodes'
```
User-data takes ~2 min (k3s install + wait for node Ready).

## 2. Copy app + our Dockerfile/manifest

```bash
$(terraform output -raw rsync_project)
$(terraform output -raw scp_overlay)
```

## 3. Build image, import into k3s, deploy (on the box)

```bash
$(terraform output -raw ssh)        # fresh login so docker group applies
cd springboot-k8-sample
docker build -f Dockerfile.aws -t springboot-k8-sample:local .      # first build ~3-5 min
docker save springboot-k8-sample:local | sudo k3s ctr images import -
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
kubectl apply -f k8s.yaml
kubectl get pods -w                 # wait for 2/2 Running, READY 1/1
```

## 4. Test

On the box:
```bash
curl localhost:30080/test/isReady
curl -X POST localhost:30080/test/echo -d 'hello'
```
From laptop:
```bash
$(terraform output -raw ssh_tunnel)    # keep open
curl localhost:9090/test/isAlive
```

## 5. See Kubernetes do its job (on the box)

```bash
kubectl delete pod -l app=springboot-k8-sample --wait=false   # self-heals back to 2
kubectl scale deploy/springboot-k8-sample --replicas=4
kubectl get pods -o wide
kubectl describe pod -l app=springboot-k8-sample | grep -A3 -i "liveness\|readiness"
# traffic spreads across pods:
for i in 1 2 3 4; do curl -s localhost:30080/test/isReady; echo; done
```

## Troubleshooting

- `ErrImageNeverPull`: image not imported; re-run the `docker save | k3s ctr images import`.
- Build OOM: use `m7i-flex.large`.
- SSH times out: `my_ip_cidr` wrong (compare with `curl -s ifconfig.me`).

## Teardown

```bash
terraform destroy
```
Local state (throwaway demo; see `../aws-demo` for S3 backend pattern).
