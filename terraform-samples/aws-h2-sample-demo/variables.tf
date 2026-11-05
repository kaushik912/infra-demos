variable "region" {
  description = "AWS region. ap-southeast-2 because this account's SCP blocks some actions in us-east-1 (see ../aws-demo/README.md)."
  type        = string
  default     = "ap-southeast-2"
}

variable "my_ip_cidr" {
  description = "Your public IP as CIDR for SSH access, e.g. 203.0.113.7/32 (curl -s ifconfig.me)."
  type        = string

  validation {
    condition     = can(cidrhost(var.my_ip_cidr, 0)) && var.my_ip_cidr != "0.0.0.0/0"
    error_message = "Must be a valid CIDR and not 0.0.0.0/0."
  }
}

variable "public_key_path" {
  description = "Path to your SSH public key."
  type        = string
  default     = "~/.ssh/id_ed25519.pub"
}

variable "instance_type" {
  description = "8 GB: Maven build + Zookeeper + Kafka + Spring app holding 100k rows in H2. Account only allows free-tier-eligible types; m7i-flex.large = 8 GB."
  type        = string
  default     = "m7i-flex.large"
}

variable "budget_email" {
  description = "Email for the 80% budget alert. Empty = no budget created."
  type        = string
  default     = ""
}

variable "budget_usd" {
  description = "Monthly budget (USD) for the alert."
  type        = number
  default     = 30
}

variable "app_source_dir" {
  description = "Local path of the Spring project to rsync to the box."
  type        = string
  default     = "/home/kaush/github_projs/reference-hendisantika/spring-boot-h2-sample"
}
