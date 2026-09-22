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