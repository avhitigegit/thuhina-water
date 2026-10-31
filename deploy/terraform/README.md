# Thuhina Water – AWS infrastructure (Terraform)

Everything the application needs in AWS, written as code. One module, one folder per environment.
Background and decisions: `doc/UAT_Deployment_Architecture_and_Plan.md` (kept outside Git).

```
deploy/terraform/
  bootstrap/            once per AWS account: the S3 bucket that stores Terraform state (local state)
  modules/app-env/      everything one environment needs
    network.tf          VPC, public subnet, internet gateway, route table
    security.tf         security groups: "web" (fixed allowed IPs, HTTP for certificates) + "my-ip"
    server.tf           EC2 (Ubuntu 24.04 ARM), encrypted disk, IMDSv2, Elastic IP
    iam.tf              server role: SSM, CloudWatch agent, ECR pull, backups, settings
    ecr.tf              image repositories (api, web), last 10 images kept
    storage.tf          S3 buckets: backups (7 days in UAT) and releases (deploy bundles)
    secrets.tf          random passwords + settings in SSM Parameter Store → server .env
    monitoring.tf       CloudWatch agent config, alarm email topic, CPU / memory / disk / status alarms
    budget.tf           monthly spending alarm (credits not counted)
    github-deploy.tf    small IAM user for GitHub Actions (OIDC is blocked on the Free plan)
    snapshots.tf        weekly disk snapshots (production only)
    templates/user-data.sh.tftpl   server start-up script (Docker, AWS CLI, CloudWatch agent, swap)
  envs/
    uat/                UAT: backend (state location), provider, module call, uat.tfvars
```

## Before you start (once per computer)

1. Install Terraform (≥ 1.10), the AWS CLI v2 and the GitHub CLI.
2. Log in to the UAT account – open the AWS Console in the browser first, then:
   ```powershell
   aws login --profile thuhina-uat          # region ap-southeast-2, pick the "Hello World" session
   ```
3. Terraform's S3 backend cannot read `aws login` sessions directly, so it uses a second profile that asks the
   AWS CLI for the current short-lived keys. In `%USERPROFILE%\.aws\config`:
   ```ini
   [profile thuhina-uat-tf]
   region = ap-southeast-2
   credential_process = C:\Users\<you>\.aws\thuhina-uat-credentials.cmd
   ```
   and `%USERPROFILE%\.aws\thuhina-uat-credentials.cmd`:
   ```bat
   @echo off
   "C:\Program Files\Amazon\AWSCLIV2\aws.exe" configure export-credentials --profile thuhina-uat --format process
   ```
   (A helper file is used because `cmd /C` drops the quotes around a path with spaces.)

When a command says the credentials expired, run `aws login --profile thuhina-uat` again.

## Values kept out of Git

The repository is public, so the two values that identify the AWS account live in local files that Git ignores.
Create them once per computer from the `.example` files in `envs/uat/`:

| File | Holds | Where the value comes from |
|---|---|---|
| `backend.hcl` | `bucket = "<state bucket>"` | `terraform -chdir=../../bootstrap output state_bucket` |
| `account.auto.tfvars` | `aws_account_id = "<id>"` | `aws sts get-caller-identity --profile thuhina-uat` |

## Everyday commands (UAT)

```powershell
$env:AWS_PROFILE = "thuhina-uat-tf"
cd deploy\terraform\envs\uat
terraform init -backend-config="backend.hcl"           # once, or after a provider / module change
terraform plan  -var-file="uat.tfvars" -out="uat.tfplan"   # what WILL change – read it
terraform apply "uat.tfplan"                           # does exactly that plan
terraform output                                       # UAT address, IP, instance ID, ECR, buckets
```

Rules:
- Read every plan. A line `must be replaced` on `aws_instance.server` means the server (and its database) would be
  rebuilt – stop and ask.
- Never change AWS by hand in the console. If it happened, bring it back into Terraform (import or re-apply).
- `terraform plan` on a clean `dev` must say **No changes**.
- Never commit `*.tfstate`, `*.tfplan` or `.terraform/` (they hold secrets), nor `backend.hcl` / `account.auto.tfvars`.
  `.terraform.lock.hcl` **is** committed.

## First-time setup of a new account

```powershell
cd deploy\terraform\bootstrap
terraform init ; terraform apply              # creates <project>-tfstate-<account id>
# write the bucket name into envs/<env>/backend.hcl and the account ID into account.auto.tfvars, then:
cd ..\envs\uat ; terraform init -backend-config="backend.hcl" ; terraform plan -var-file="uat.tfvars" -out="uat.tfplan" ; terraform apply "uat.tfplan"
```

After the first apply:
1. Confirm the email "AWS Notification – Subscription Confirmation" (alarm emails).
2. Give GitHub its deploy key – created by the AWS CLI and piped straight into the GitHub environment secrets,
   so it is never shown or saved:
   ```powershell
   $k = aws iam create-access-key --user-name thuhina-uat-github-deploy --profile thuhina-uat | ConvertFrom-Json
   $k.AccessKey.AccessKeyId     | gh secret set AWS_ACCESS_KEY_ID     --env uat
   $k.AccessKey.SecretAccessKey | gh secret set AWS_SECRET_ACCESS_KEY --env uat
   Remove-Variable k
   ```
   Rotate every 90 days: create a new key the same way, then delete the old one (`aws iam delete-access-key`).

## Useful checks

```powershell
# terminal on the server (no SSH) – needs the Session Manager plugin, or use EC2 → Connect → Session Manager
aws ssm start-session --target (terraform output -raw instance_id) --profile thuhina-uat
# server start-up log
#   /var/log/thuhina-bootstrap.log   (finished when /var/lib/cloud/instance/thuhina-bootstrap-done exists)
```

## Removing UAT completely

```powershell
terraform destroy -var-file="uat.tfvars"      # envs/uat – buckets and images are deleted too (force_destroy)
```
The state bucket (bootstrap) is protected with `prevent_destroy` and stays.
