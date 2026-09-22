terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws    = { source = "hashicorp/aws", version = "~> 5.0" }
    random = { source = "hashicorp/random", version = "~> 3.6" }
  }

  # Local backend on purpose: this config CREATES the bucket that the
  # "app" config will use as its S3 backend. Nothing can point at that
  # bucket before it exists, so bootstrap's own state has to live locally.
}

provider "aws" {
  region = var.region
}

variable "region" {
  type    = string
  default = "ap-southeast-2" # us-east-1 is SCP-denied on this account; ap-southeast-2 isn't
}

variable "bucket_prefix" {
  type        = string
  description = "Prefix for the state bucket name (must end up globally unique across all of S3)"
  default     = "tf-practice"
}

# S3 bucket names are global. A random suffix avoids "already exists" errors
# without you having to hand-pick a unique name.
resource "random_id" "suffix" {
  byte_length = 4
}

resource "aws_s3_bucket" "tf_state" {
  bucket = "${var.bucket_prefix}-state-${random_id.suffix.hex}"

  # Lets `terraform destroy` remove the bucket even if state objects are
  # still in it. Fine for a practice account; think twice before doing
  # this on a real team's state bucket.
  force_destroy = true
}

resource "aws_s3_bucket_versioning" "tf_state" {
  bucket = aws_s3_bucket.tf_state.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "tf_state" {
  bucket = aws_s3_bucket.tf_state.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "tf_state" {
  bucket = aws_s3_bucket.tf_state.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

output "bucket_name" {
  value = aws_s3_bucket.tf_state.id
}
