#!/bin/bash
# Bootstrap: Docker + compose plugin + Java 25 (project targets Java 25).
# Log: /var/log/user-data.log
exec > >(tee /var/log/user-data.log) 2>&1
set -euxo pipefail

dnf install -y docker rsync git
systemctl enable --now docker
usermod -aG docker ec2-user

mkdir -p /usr/local/lib/docker/cli-plugins
curl -fsSL https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# Java 25: Corretto package if the repo has it, else the Corretto tarball.
if ! dnf install -y java-25-amazon-corretto-devel; then
  curl -fsSL https://corretto.aws/downloads/latest/amazon-corretto-25-x64-linux-jdk.tar.gz -o /tmp/jdk25.tgz
  mkdir -p /opt/jdk25
  tar -xzf /tmp/jdk25.tgz -C /opt/jdk25 --strip-components=1
  cat > /etc/profile.d/jdk25.sh <<'EOF'
export JAVA_HOME=/opt/jdk25
export PATH=$JAVA_HOME/bin:$PATH
EOF
fi

touch /var/lib/cloud/instance/user-data-done
