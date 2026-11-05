#!/bin/bash
# Bootstrap: Docker (to build the image) + k3s (single-node Kubernetes).
# Java/Gradle are NOT needed on the host: the image build runs inside Docker.
# Log: /var/log/user-data.log
exec > >(tee /var/log/user-data.log) 2>&1
set -euxo pipefail

dnf install -y docker rsync git
systemctl enable --now docker
usermod -aG docker ec2-user

# k3s: kubeconfig readable by ec2-user; traefik off (we use a NodePort, saves RAM).
curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="server --write-kubeconfig-mode 644 --disable traefik" sh -

# kubectl for ec2-user (k3s ships one at /usr/local/bin/kubectl); point it at k3s.
echo 'export KUBECONFIG=/etc/rancher/k3s/k3s.yaml' > /etc/profile.d/k3s.sh

# Wait for the node to be Ready before declaring done.
for i in $(seq 1 60); do
  /usr/local/bin/k3s kubectl get nodes 2>/dev/null | grep -q ' Ready' && break
  sleep 5
done

touch /var/lib/cloud/instance/user-data-done
