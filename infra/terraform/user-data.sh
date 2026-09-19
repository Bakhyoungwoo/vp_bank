#!/bin/bash
set -euxo pipefail

exec > >(tee /var/log/vap-k3s-bootstrap.log | logger -t vap-k3s-bootstrap -s 2>/dev/console) 2>&1

apt-get update
apt-get install -y curl

curl -sfL https://get.k3s.io | INSTALL_K3S_VERSION='v1.30.6+k3s1' sh -s - server \
  --write-kubeconfig-mode 644 \
  --disable servicelb

systemctl enable k3s
systemctl restart k3s
