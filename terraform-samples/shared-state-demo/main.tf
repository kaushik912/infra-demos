terraform {
  required_version = ">= 1.5.0"

  # "Shared" backend: both people point at the SAME state file.
  # Local backend still does file locking. In real life: S3 + DynamoDB/lockfile, GCS, Terraform Cloud, etc.
  backend "local" {
    path = "../shared-state-demo/shared/terraform.tfstate"
  }

  required_providers {
    local = {
      source  = "hashicorp/local"
      version = "~> 2.5"
    }
  }
}

variable "greeting" {
  type    = string
  default = "v1"
}

# Set >0 to make apply slow (holds the lock) for the locking demo
variable "delay" {
  type    = number
  default = 0
}

resource "local_file" "app" {
  filename = "${path.module}/target/app.conf"
  content  = "greeting=${var.greeting}\n"
}

resource "terraform_data" "slow" {
  triggers_replace = var.delay
  provisioner "local-exec" {
    command = "sleep ${var.delay}"
  }
}
