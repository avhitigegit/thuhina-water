data "aws_caller_identity" "current" {}

data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  name       = "${var.project}-${var.environment}" # e.g. thuhina-uat – prefix of every resource name
  account_id = data.aws_caller_identity.current.account_id
  az         = data.aws_availability_zones.available.names[0]

  # Every value under this path becomes one line of the server's .env (deploy.sh).
  env_param_path = "/${var.project}/${var.environment}/env"

  # Site address: the real domain, or <ip-with-dashes>.sslip.io, which points to the Elastic IP by itself.
  site_host = var.domain_name != "" ? var.domain_name : "${replace(aws_eip.server.public_ip, ".", "-")}.sslip.io"

  ecr_registry = "${local.account_id}.dkr.ecr.${var.aws_region}.amazonaws.com"
}
