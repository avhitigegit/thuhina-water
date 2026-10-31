output "state_bucket" {
  description = "Put this name in envs/<env>/backend.tf."
  value       = aws_s3_bucket.tfstate.bucket
}

output "aws_region" {
  value = var.aws_region
}
