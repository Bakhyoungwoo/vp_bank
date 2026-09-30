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

## Notes from the 2026-09-28 setup

- The instance created that day was launched manually from the AWS console rather than via this
  `terraform apply`, so its security group was named `launch-wizard-1` (the default the console
  gives it), not `vap-production-terraform` from `main.tf`. `EC2_SECURITY_GROUP_ID` was pointed at
  that console-created group. The instance was later terminated (verified 2026-09-30: no EC2
  instances exist in this account/region), and the group was deleted the same day since nothing
  referenced it any more.
- **Whenever the instance is recreated (via `terraform apply` or the console), update both
  `SERVER_HOST` and `EC2_SECURITY_GROUP_ID` in GitHub Actions secrets to the new instance's values
  before rerunning `deploy.yml`** — `main.tf` creates its own `vap-production-terraform` security
  group, so the ID will differ from any previous manually-created one.
- Since there's currently no instance, `deploy.yml`'s `deploy` job is gated behind the
  `DEPLOY_ENABLED` repo variable (Settings → Secrets and variables → Actions → Variables) so
  `build-spring`/`build-python` keep running on push without the SSH step failing every time.
  **Once the instance is recreated and `SERVER_HOST`/`EC2_SECURITY_GROUP_ID` are updated, also
  set the `DEPLOY_ENABLED` repo variable to `true`** to re-enable the deploy job.
