# Terraform

This creates a deliberately small, single-node k3s host in the default VPC.

```powershell
terraform init
Copy-Item terraform.tfvars.example terraform.tfvars
# Edit admin_cidr to your current public IP /32.
terraform plan
terraform apply
terraform output -raw private_key | Out-File -Encoding ascii vap-k3s.pem
```

The generated key and Terraform state contain sensitive data and are ignored by git.
Do not run `terraform destroy` until the MySQL data has been backed up.
