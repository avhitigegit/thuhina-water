output "site_url" {
  value = module.app.site_url
}

output "public_ip" {
  value = module.app.public_ip
}

output "instance_id" {
  value = module.app.instance_id
}

output "ecr_registry" {
  value = module.app.ecr_registry
}

output "ecr_repositories" {
  value = module.app.ecr_repositories
}

output "backup_bucket" {
  value = module.app.backup_bucket
}

output "releases_bucket" {
  value = module.app.releases_bucket
}

output "env_param_path" {
  value = module.app.env_param_path
}

output "my_ip_security_group_id" {
  value = module.app.my_ip_security_group_id
}

output "github_deploy_user" {
  value = module.app.github_deploy_user
}

output "aws_region" {
  value = var.aws_region
}
