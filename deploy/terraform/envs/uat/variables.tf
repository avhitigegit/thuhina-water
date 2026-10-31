variable "project" {
  type = string
}

variable "aws_region" {
  type = string
}

variable "aws_account_id" {
  description = "The only AWS account this environment may be applied to."
  type        = string
}

variable "allowed_cidrs" {
  type = list(string)
}

variable "alert_email" {
  type = string
}

variable "monthly_budget_usd" {
  type = number
}

variable "instance_type" {
  type = string
}

variable "disk_size_gb" {
  type = number
}

variable "backup_retention_days" {
  type = number
}

variable "app_settings" {
  type = map(string)
}
