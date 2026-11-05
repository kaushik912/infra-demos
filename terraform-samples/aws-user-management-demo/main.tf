terraform {
  required_version = ">= 1.5"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project = "user-management-demo"
      Managed = "terraform"
    }
  }
}

# Default VPC: no NAT gateway / custom VPC (cost traps).
data "aws_vpc" "default" {
  default = true
}

# Latest Amazon Linux 2023 AMI, resolved via SSM public parameter.
data "aws_ssm_parameter" "al2023" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

resource "aws_key_pair" "demo" {
  key_name   = "user-management-demo"
  public_key = file(pathexpand(var.public_key_path))
}

# SSH only, from your IP. App (8080/8443), MySQL, Redis are NOT exposed:
# reach the app through an `ssh -L` tunnel (see outputs). Email links point to
# https://localhost:8443 so they work through the tunnel.
resource "aws_security_group" "demo" {
  name        = "user-management-demo"
  description = "SSH from my IP only"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.my_ip_cidr]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_instance" "demo" {
  ami                         = data.aws_ssm_parameter.al2023.value
  instance_type               = var.instance_type
  key_name                    = aws_key_pair.demo.key_name
  vpc_security_group_ids      = [aws_security_group.demo.id]
  associate_public_ip_address = true
  user_data                   = file("${path.module}/user-data.sh")

  metadata_options {
    http_tokens = "required" # IMDSv2 only
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = 20
    encrypted   = true
  }

  tags = {
    Name = "user-management-demo"
  }
}

# Spend alarm so a forgotten instance can't quietly eat the credit.
# Only created when budget_email is set.
resource "aws_budgets_budget" "demo" {
  count        = var.budget_email == "" ? 0 : 1
  name         = "user-management-demo-monthly"
  budget_type  = "COST"
  limit_amount = tostring(var.budget_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 80
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.budget_email]
  }
}
