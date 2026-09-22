terraform {
  required_version = ">= 1.5"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # State bucket created MANUALLY (see README) — not by Terraform.
  # `bucket` is deliberately omitted — backend blocks can't reference
  # variables, so it's supplied at init time:
  #   terraform init -reconfigure -backend-config="bucket=<your bucket>"
  # (CI does this too, via the TF_STATE_BUCKET repo variable — see
  # .github/workflows/aws-s3-demo.yml)
  backend "s3" {
    key          = "aws-s3-demo/terraform.tfstate"
    region       = "ap-southeast-2"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region = "ap-southeast-2"
}

variable "greeting" {
  type    = string
  default = "Hello from Terraform!\n"
}

# Set >0 to hold the apply open long enough to demo state locking.
variable "delay" {
  type    = number
  default = 0
}

resource "random_id" "suffix" {
  byte_length = 4
}

resource "terraform_data" "slow" {
  triggers_replace = var.delay
  provisioner "local-exec" {
    command = "sleep ${var.delay}"
  }
}

resource "aws_s3_bucket" "demo" {
  bucket = "tf-demo-${random_id.suffix.hex}"
}

resource "aws_s3_bucket_public_access_block" "demo" {
  bucket                  = aws_s3_bucket.demo.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "demo" {
  bucket = aws_s3_bucket.demo.id
  versioning_configuration {
    status = "Suspended"
  }
}

resource "aws_s3_object" "hello" {
  bucket       = aws_s3_bucket.demo.id
  key          = "hello.txt"
  content      = var.greeting
  content_type = "text/plain"
}

output "bucket_name" {
  value = aws_s3_bucket.demo.id
}

output "bucket_region" {
  value = aws_s3_bucket.demo.region
}
