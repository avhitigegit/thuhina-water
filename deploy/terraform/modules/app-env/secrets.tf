# Settings and secrets (plan section 8). Everything under /<project>/<env>/env/ becomes one line
# of the server's .env when deploy.sh runs. Secrets are random, generated here, and stored encrypted
# (SecureString) – nobody types or sees them. They are also in the Terraform state, which is why
# the state bucket is private and encrypted.

resource "random_password" "db" {
  length  = 32
  special = false # safe in JDBC URLs and .env files
}

resource "random_password" "jwt" {
  length  = 64
  special = false
}

resource "random_password" "encryption_key" {
  length  = 32
  special = false # AES key for stored secrets (WhatsApp, M13)
}

locals {
  secret_env = {
    POSTGRES_PASSWORD  = random_password.db.result
    JWT_SECRET         = random_password.jwt.result
    APP_ENCRYPTION_KEY = random_password.encryption_key.result
  }

  # Values the deploy scripts need, plus the plain app settings of this environment.
  plain_env = merge({
    APP_ENV         = var.environment
    APP_DOMAIN      = local.site_host
    AWS_REGION      = var.aws_region
    ECR_REGISTRY    = local.ecr_registry
    BACKUP_BUCKET   = local.backups_bucket_ref.bucket
    RELEASES_BUCKET = local.releases_bucket_ref.bucket
  }, var.app_settings)
}

resource "aws_ssm_parameter" "secret_env" {
  for_each = nonsensitive(toset(keys(local.secret_env)))

  name  = "${local.env_param_path}/${each.value}"
  type  = "SecureString"
  value = local.secret_env[each.value]
}

resource "aws_ssm_parameter" "plain_env" {
  for_each = local.plain_env

  name  = "${local.env_param_path}/${each.key}"
  type  = "String"
  value = each.value
}
