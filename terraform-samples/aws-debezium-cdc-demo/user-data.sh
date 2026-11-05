#!/bin/bash
# Bootstrap: Docker + compose plugin + Java 17. Log: /var/log/user-data.log
exec > >(tee /var/log/user-data.log) 2>&1
set -euxo pipefail

dnf install -y docker java-17-amazon-corretto-devel rsync git
systemctl enable --now docker
usermod -aG docker ec2-user

mkdir -p /usr/local/lib/docker/cli-plugins
curl -fsSL https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

touch /var/lib/cloud/instance/user-data-done
