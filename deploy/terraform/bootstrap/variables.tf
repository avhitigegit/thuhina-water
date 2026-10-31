variable "project" {
  description = "Short project name, used in every resource name."
  type        = string
}

variable "aws_region" {
  description = "AWS region of the state bucket (same region as the environment)."
  type        = string
}
