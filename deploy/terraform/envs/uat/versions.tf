terraform {
  required_version = ">= 1.10.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # State in the bucket made by ../../bootstrap (plan 6.2). S3 lock file – no DynamoDB table needed.
  # The bucket name holds the account ID, so it comes from backend.hcl (not in Git):
  #   terraform init -backend-config="backend.hcl"      (copy backend.hcl.example)
  backend "s3" {
    key          = "uat/terraform.tfstate"
    region       = "ap-southeast-2"
    encrypt      = true
    use_lockfile = true
  }
}
