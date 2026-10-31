# GitHub deploy user (plan D-U11) – used because the Free-plan account blocks GitHub OIDC.
# It can only: push images to this environment's ECR repositories, upload a deployment bundle,
# and run a command on this one server. No console login. Its access key is NOT created here
# (that would put it in the state): it is created once with the AWS CLI and piped straight into
# the GitHub environment secrets (see deploy/terraform/README.md).

resource "aws_iam_user" "github_deploy" {
  count = var.create_github_deploy_user ? 1 : 0

  name = "${local.name}-github-deploy"
  path = "/ci/"
}

data "aws_iam_policy_document" "github_deploy" {
  statement {
    sid       = "EcrLogin"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "EcrPush"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:CompleteLayerUpload",
      "ecr:DescribeImages",
      "ecr:GetDownloadUrlForLayer",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart",
    ]
    resources = [for r in aws_ecr_repository.app : r.arn]
  }

  statement {
    sid       = "UploadRelease"
    actions   = ["s3:PutObject"]
    resources = ["${local.releases_bucket_ref.arn}/*"]
  }

  statement {
    sid     = "RunDeployOnThisServer"
    actions = ["ssm:SendCommand"]
    resources = [
      "arn:aws:ssm:${var.aws_region}::document/AWS-RunShellScript",
      aws_instance.server.arn,
    ]
  }

  statement {
    sid       = "ReadDeployResult"
    actions   = ["ssm:GetCommandInvocation", "ssm:ListCommandInvocations"]
    resources = ["*"]
  }
}

resource "aws_iam_user_policy" "github_deploy" {
  count = var.create_github_deploy_user ? 1 : 0

  name   = "${local.name}-github-deploy"
  user   = aws_iam_user.github_deploy[0].name
  policy = data.aws_iam_policy_document.github_deploy.json
}
