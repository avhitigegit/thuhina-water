# Server permissions – the "staff ID card" (plan section 5). No passwords or keys on the server:
# the AWS CLI and the CloudWatch agent on the server use this role through the instance profile.

data "aws_iam_policy_document" "ec2_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "server" {
  name               = "${local.name}-server"
  description        = "${local.name} EC2 server: SSM, CloudWatch agent, ECR pull, backups, settings"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
}

# Session Manager (terminal without SSH) and Run Command (deploys from GitHub).
resource "aws_iam_role_policy_attachment" "ssm_core" {
  role       = aws_iam_role.server.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# Memory / disk metrics; also lets the agent read its AmazonCloudWatch-* config parameter.
resource "aws_iam_role_policy_attachment" "cloudwatch_agent" {
  role       = aws_iam_role.server.name
  policy_arn = "arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy"
}

data "aws_iam_policy_document" "server" {
  statement {
    sid       = "EcrLogin"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "EcrPull"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer",
    ]
    resources = [for r in aws_ecr_repository.app : r.arn]
  }

  statement {
    sid       = "ReadSettings"
    actions   = ["ssm:GetParametersByPath", "ssm:GetParameter", "ssm:GetParameters"]
    resources = ["arn:aws:ssm:${var.aws_region}:${local.account_id}:parameter/${var.project}/${var.environment}/*"]
  }

  statement {
    sid       = "Backups"
    actions   = ["s3:PutObject", "s3:GetObject", "s3:ListBucket"]
    resources = [local.backups_bucket_ref.arn, "${local.backups_bucket_ref.arn}/*"]
  }

  statement {
    sid       = "Releases"
    actions   = ["s3:GetObject", "s3:ListBucket"]
    resources = [local.releases_bucket_ref.arn, "${local.releases_bucket_ref.arn}/*"]
  }
}

resource "aws_iam_role_policy" "server" {
  name   = "${local.name}-server"
  role   = aws_iam_role.server.id
  policy = data.aws_iam_policy_document.server.json
}

resource "aws_iam_instance_profile" "server" {
  name = "${local.name}-server"
  role = aws_iam_role.server.name
}
