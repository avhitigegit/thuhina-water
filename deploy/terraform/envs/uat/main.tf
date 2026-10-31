# UAT environment – the shared module with UAT values (plan sections 3 and 14).

module "app" {
  source = "../../modules/app-env"

  project     = var.project
  environment = "uat"
  aws_region  = var.aws_region

  allowed_cidrs = var.allowed_cidrs

  instance_type = var.instance_type
  disk_size_gb  = var.disk_size_gb
  cpu_credits   = "standard" # never billed extra for CPU bursts

  app_settings = var.app_settings

  backup_retention_days  = var.backup_retention_days
  enable_snapshots       = false # UAT holds test data only
  force_destroy          = true  # terraform destroy may remove UAT completely
  termination_protection = false

  alert_email        = var.alert_email
  monthly_budget_usd = var.monthly_budget_usd

  create_github_deploy_user = true # GitHub OIDC is blocked in the Free-plan account (D-U11)
}
