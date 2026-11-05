# AWS: run user-management-web-app on one EC2 box

Single EC2 (Docker + compose preinstalled). App, MySQL, Redis run via the app's
own `docker-compose.yml` + `docker-compose.aws.yml` overlay. Java 25 builds
inside Docker, nothing to install on the host.

- Access: SSH tunnel only (SG = SSH from your IP). No ports, MySQL, Redis exposed.
- Email: Brevo SMTP (same as `spring-boot-onetimetoken-authentication`). Creds in
  `.env` (gitignored), never in tfvars/state.
- Cost: `m7i-flex.large` ~ $0.10/hr. **Destroy when done.**

## Before you start: sender address (code tweak)

App hardcodes `setFrom("no-reply@javadevjournal.com")` in
`AccountVerificationEmailContext` and `ForgotPasswordEmailContext`. Brevo
rejects unverified senders. Change both to your Brevo-verified sender
(e.g. `no-reply@jvm.my.id`, as the other project uses).

## 1. Provision

```bash
cp terraform.tfvars.example terraform.tfvars   # set my_ip_cidr
cp .env.example .env                           # fill Brevo SMTP user/key
terraform init && terraform apply
$(terraform output -raw ssh) 'ls /var/lib/cloud/instance/user-data-done && docker compose version'
```

## 2. Copy app + overlay + secrets

```bash
$(terraform output -raw rsync_project)
$(terraform output -raw scp_overlay)
```

## 3. Run (on the box)

```bash
$(terraform output -raw ssh)        # fresh login for docker group
cd user-management-web-app
docker compose -f docker-compose.yml -f docker-compose.aws.yml up -d --build
docker compose logs -f app          # first build ~3-5 min
```

## 4. Test from laptop

```bash
$(terraform output -raw ssh_tunnel)   # keep open
```
Open https://localhost:8443 (accept self-signed cert) -> register -> click the verify
email link (points at `https://localhost:8443`, works while tunnel is open) -> sign in
either with email + password, or "Email me a login link" (magic link, valid 5 min,
single use). No authenticator app or QR code needed. If the email is slow, the link is
also in `docker compose logs app` (demo only).

## Teardown

```bash
terraform destroy
```
Local state (throwaway demo; see `../aws-demo` for S3 backend pattern).
