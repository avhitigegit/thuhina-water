# AWS login comes from the AWS_PROFILE environment variable (thuhina-uat, made with `aws login`),
# so no profile name or key is written in the code.

provider "aws" {
  region = var.aws_region

  # Only ever touch the UAT account – stops an apply with the wrong login.
  allowed_account_ids = [var.aws_account_id]

  default_tags {
    tags = {
      Project     = var.project
      Environment = "uat"
      ManagedBy   = "terraform"
    }
  }
}
