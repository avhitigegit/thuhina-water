output "site_url" {
  description = "Address of the environment."
  value       = "https://${local.site_host}"
}

output "public_ip" {
  description = "Elastic IP of the server."
  value       = aws_eip.server.public_ip
}

output "instance_id" {
  description = "EC2 instance ID (used by SSM Session Manager and the deploy workflow)."
  value       = aws_instance.server.id
}

output "ecr_registry" {
  value = local.ecr_registry
}

output "ecr_repositories" {
  description = "Image repository URL per image."
  value       = { for k, r in aws_ecr_repository.app : k => r.repository_url }
}

output "backup_bucket" {
  value = local.backups_bucket_ref.bucket
}

output "releases_bucket" {
  value = local.releases_bucket_ref.bucket
}

output "env_param_path" {
  description = "SSM path whose values become the server's .env."
  value       = local.env_param_path
}

output "my_ip_security_group_id" {
  description = "Security group that allow-my-ip.ps1 updates."
  value       = aws_security_group.my_ip.id
}

output "github_deploy_user" {
  description = "IAM user for GitHub Actions (empty when not created)."
  value       = var.create_github_deploy_user ? aws_iam_user.github_deploy[0].name : ""
}

output "alerts_topic_arn" {
  value = aws_sns_topic.alerts.arn
}
