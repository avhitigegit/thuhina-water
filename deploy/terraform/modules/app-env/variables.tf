# Inputs of one environment (plan section 14). Only these change between UAT, production
# and another project.

variable "project" {
  description = "Short project name, used in every resource name (e.g. thuhina)."
  type        = string
}

variable "environment" {
  description = "Environment name: uat or prod."
  type        = string

  validation {
    condition     = contains(["uat", "prod"], var.environment)
    error_message = "environment must be uat or prod."
  }
}

variable "aws_region" {
  description = "AWS region of the environment."
  type        = string
}

# ---- network

variable "vpc_cidr" {
  description = "Address range of the private network."
  type        = string
  default     = "10.20.0.0/16"
}

variable "public_subnet_cidr" {
  description = "Address range of the public subnet that holds the server."
  type        = string
  default     = "10.20.1.0/24"
}

variable "allowed_cidrs" {
  description = "Fixed IP ranges that may open the site on HTTPS (e.g. the client's office). [\"0.0.0.0/0\"] = everyone. Your own changing IP is added by allow-my-ip, not here."
  type        = list(string)
  default     = []
}

# ---- server

variable "instance_type" {
  description = "EC2 instance type (ARM / Graviton)."
  type        = string
  default     = "t4g.small"
}

variable "disk_size_gb" {
  description = "Size of the server disk (OS, database, files, local backups)."
  type        = number
  default     = 30
}

variable "swap_size_gb" {
  description = "Swap file on the server – extra safety for 2 GiB of RAM."
  type        = number
  default     = 2
}

variable "cpu_credits" {
  description = "T4g CPU credit mode. standard = never billed extra for CPU bursts; unlimited = faster under long load but can cost more."
  type        = string
  default     = "standard"
}

variable "termination_protection" {
  description = "Protect the server from being deleted (true in production)."
  type        = bool
  default     = false
}

variable "domain_name" {
  description = "Site address. Empty = <elastic-ip>.sslip.io (works without owning a domain)."
  type        = string
  default     = ""
}

# ---- application

variable "ecr_repositories" {
  description = "One image repository per container image built by GitHub Actions."
  type        = list(string)
  default     = ["api", "web"]
}

variable "app_settings" {
  description = "Plain (not secret) settings written to the server's .env on every deployment."
  type        = map(string)
  default     = {}
}

# ---- backups & clean-up

variable "backup_retention_days" {
  description = "Days a database backup is kept in S3."
  type        = number
  default     = 7
}

variable "release_retention_days" {
  description = "Days a deployment bundle is kept in S3."
  type        = number
  default     = 30
}

variable "enable_snapshots" {
  description = "Weekly snapshot of the server disk, kept 4 weeks (production)."
  type        = bool
  default     = false
}

variable "force_destroy" {
  description = "Allow terraform destroy to delete buckets and image repositories that still hold files (true only in UAT)."
  type        = bool
  default     = false
}

# ---- alerts & costs

variable "alert_email" {
  description = "Email for alarms and budget warnings."
  type        = string
}

variable "monthly_budget_usd" {
  description = "Monthly spending alarm in US$ (credits are not counted, so the real usage is compared)."
  type        = number
}

# ---- deployment from GitHub

variable "create_github_deploy_user" {
  description = "Create the small IAM user GitHub Actions uses to deploy (used where OIDC is not allowed)."
  type        = bool
  default     = true
}
