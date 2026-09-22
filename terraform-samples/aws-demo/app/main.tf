terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws    = { source = "hashicorp/aws", version = "~> 5.0" }
    random = { source = "hashicorp/random", version = "~> 3.6" }
  }

  # Remote state, in the bucket bootstrap/ created. `bucket` is deliberately
  # left out here — backend blocks can't reference variables, so it's
  # supplied at `terraform init` time with -backend-config (see init.sh).
  backend "s3" {
    key          = "aws-demo/terraform.tfstate"
    region       = "ap-southeast-2" # us-east-1 is SCP-denied on this account; ap-southeast-2 isn't
    encrypt      = true
    use_lockfile = true # state locking via an object lock in the same bucket (TF >= 1.10) — no DynamoDB table needed
  }
}

provider "aws" {
  region = var.region
}

variable "region" {
  type    = string
  default = "ap-southeast-2"
}

variable "bucket_prefix" {
  type        = string
  description = "Prefix for this demo's own (non-state) bucket name"
  default     = "tf-practice-demo"
}

variable "greeting" {
  type    = string
  default = "Hello from Terraform on AWS!"
}

resource "random_id" "suffix" {
  byte_length = 4
}

resource "aws_s3_bucket" "demo" {
  bucket        = "${var.bucket_prefix}-${random_id.suffix.hex}"
  force_destroy = true
}

resource "aws_s3_bucket_public_access_block" "demo" {
  bucket = aws_s3_bucket.demo.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Mirrors the local_file demo: an object whose content you can edit outside
# Terraform to replay the drift experiment against a real AWS backend.
resource "aws_s3_object" "hello" {
  bucket       = aws_s3_bucket.demo.id
  key          = "hello.txt"
  content      = var.greeting
  content_type = "text/plain"
}

output "bucket_name" {
  value = aws_s3_bucket.demo.id
}

output "object_key" {
  value = aws_s3_object.hello.key
}
