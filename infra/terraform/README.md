# VAP production EC2

This configuration creates a replacement server without touching the existing instance:

- Ubuntu 24.04 EC2 (`t3.micro`)
- Elastic IP
- SSH from the administrator IP, plus HTTP/HTTPS
- Docker Engine and Compose plugin

Run from this directory after authenticating the AWS CLI:

```powershell
terraform init
terraform apply
```

The apply command does not destroy the existing instance. After the new server is reachable, create `~/.env`, update GitHub Actions `SERVER_HOST` and `EC2_SECURITY_GROUP_ID`, and rerun the deployment workflow.

Do not commit a real `terraform.tfvars` file or any production secrets.
