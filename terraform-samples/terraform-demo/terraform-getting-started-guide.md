# Terraform Getting Started Guide

Terraform is an infrastructure-as-code tool. You describe the infrastructure you want in configuration files, and Terraform creates, updates, or destroys resources to match.

## 1. Install Terraform

**Linux (Debian/Ubuntu):**

```bash
wget -O- https://apt.releases.hashicorp.com/gpg | sudo gpg --dearmor -o /usr/share/keyrings/hashicorp-archive-keyring.gpg
echo "deb [signed-by=/usr/share/keyrings/hashicorp-archive-keyring.gpg] https://apt.releases.hashicorp.com $(lsb_release -cs) main" | sudo tee /etc/apt/sources.list.d/hashicorp.list
sudo apt update && sudo apt install terraform
```

**macOS:**

```bash
brew tap hashicorp/tap
brew install hashicorp/tap/terraform
```

**Windows:** `choco install terraform`, or download the binary from the [Terraform downloads page](https://developer.hashicorp.com/terraform/install).

Verify the install:

```bash
terraform -version
```

## 2. Core Concepts

| Concept | Meaning |
|---------|---------|
| **Provider** | Plugin that talks to a platform API (AWS, Azure, GCP, Kubernetes, etc.). |
| **Resource** | A single piece of infrastructure (a server, bucket, DNS record). |
| **Data source** | Read-only lookup of existing infrastructure. |
| **Variable** | Input parameter for your configuration. |
| **Output** | Value exported after apply (for example, an IP address). |
| **State** | File (`terraform.tfstate`) mapping your config to real resources. |
| **Module** | Reusable group of resources. |

## 3. Your First Configuration

This example uses the `local` provider, so it needs no cloud account.

Create a project directory:

```bash
mkdir terraform-demo && cd terraform-demo
```

Create `main.tf`:

```hcl
terraform {
  required_version = ">= 1.5.0"

  required_providers {
    local = {
      source  = "hashicorp/local"
      version = "~> 2.5"
    }
  }
}

variable "greeting" {
  type    = string
  default = "Hello, Terraform!"
}

resource "local_file" "hello" {
  filename = "${path.module}/hello.txt"
  content  = var.greeting
}

output "file_path" {
  value = local_file.hello.filename
}
```

## 4. The Core Workflow

```bash
terraform init      # download providers, set up backend
terraform fmt       # format code
terraform validate  # check syntax and internal consistency
terraform plan      # preview changes
terraform apply     # create/update resources (asks for confirmation)
terraform destroy   # tear everything down
```

Run through it:

1. `terraform init` downloads the `local` provider into `.terraform/`.
2. `terraform plan` shows `local_file.hello` will be created.
3. `terraform apply` prompts for `yes`, then creates `hello.txt`.
4. Change the `greeting` default, then run `plan` and `apply` again. Terraform updates only what changed.
5. `terraform destroy` removes `hello.txt`.

Override a variable at apply time:

```bash
terraform apply -var="greeting=Hi there"
```

## 5. Using a Real Provider (AWS Example)

```hcl
terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = "us-east-1"
}

resource "aws_s3_bucket" "example" {
  bucket = "my-unique-bucket-name-12345"
}
```

Authenticate with environment variables. Never hardcode credentials in `.tf` files.

```bash
export AWS_ACCESS_KEY_ID="..."
export AWS_SECRET_ACCESS_KEY="..."
```

Or use `aws configure` / AWS SSO profiles.

## 6. Variables and Outputs

Put variables in `variables.tf` and values in `terraform.tfvars`:

```hcl
# variables.tf
variable "environment" {
  type        = string
  description = "Deployment environment"
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment must be dev, staging, or prod."
  }
}
```

```hcl
# terraform.tfvars
environment = "dev"
```

Mark sensitive values so they are hidden in CLI output:

```hcl
variable "db_password" {
  type      = string
  sensitive = true
}
```

Pass secrets via environment: `export TF_VAR_db_password="..."`.

## 7. State Management

State tracks what Terraform manages. Treat it as sensitive: it can contain secrets.

- Local state is fine for learning.
- For teams, use a **remote backend** with locking (S3 + DynamoDB, Terraform Cloud, Azure Storage, GCS).

```hcl
terraform {
  backend "s3" {
    bucket         = "my-tf-state"
    key            = "demo/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "tf-locks"
    encrypt        = true
  }
}
```

Useful state commands:

```bash
terraform state list
terraform state show local_file.hello
terraform import <address> <id>   # adopt an existing resource
```

## 8. Modules

A module is a directory of `.tf` files. Call it from another config:

```hcl
module "network" {
  source = "./modules/network"
  cidr   = "10.0.0.0/16"
}
```

Public modules live in the [Terraform Registry](https://registry.terraform.io/).

## 9. Recommended Project Layout

```
.
├── main.tf
├── variables.tf
├── outputs.tf
├── providers.tf
├── terraform.tfvars
├── modules/
└── .gitignore
```

## 10. .gitignore

```gitignore
.terraform/
*.tfstate
*.tfstate.*
crash.log
*.tfvars      # if it holds secrets
.terraformrc
```

Commit `.terraform.lock.hcl` so provider versions stay consistent across machines.

## 11. Best Practices

- Pin provider and Terraform versions.
- Always review `terraform plan` before `apply`.
- Use remote state with locking for teams.
- Keep secrets out of code and version control.
- Use modules to avoid duplication.
- Run `terraform fmt` and `terraform validate` in CI.
- Use separate state per environment (workspaces or separate directories).
- Tag resources for cost tracking and ownership.

## 12. Troubleshooting

| Problem | Fix |
|---------|-----|
| `Error: Failed to query available provider packages` | Check network and provider `source`/`version`. |
| `Error acquiring the state lock` | Another run holds the lock. Wait, or `terraform force-unlock <ID>` if stale. |
| Drift between real infra and state | Run `terraform plan -refresh-only`, then decide to apply or fix config. |
| Provider auth errors | Check credentials and environment variables. |
| Verbose debugging | `TF_LOG=DEBUG terraform plan` |

## Next Steps

- [Official tutorials](https://developer.hashicorp.com/terraform/tutorials)
- [Language documentation](https://developer.hashicorp.com/terraform/language)
- [Provider registry](https://registry.terraform.io/browse/providers)
