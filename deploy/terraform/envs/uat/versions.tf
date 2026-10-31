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
  backend "s3" {
    bucket       = "thuhina-tfstate-685253389970"
    key          = "uat/terraform.tfstate"
    region       = "ap-southeast-2"
    encrypt      = true
    use_lockfile = true
  }
}
