# Weekly snapshot of the server disk, kept 4 weeks (design 12.3) – production only
# (enable_snapshots = true). Data Lifecycle Manager snapshots every instance tagged Backup = weekly.

data "aws_iam_policy_document" "dlm_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["dlm.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "dlm" {
  count = var.enable_snapshots ? 1 : 0

  name               = "${local.name}-snapshots"
  assume_role_policy = data.aws_iam_policy_document.dlm_assume.json
}

resource "aws_iam_role_policy_attachment" "dlm" {
  count = var.enable_snapshots ? 1 : 0

  role       = aws_iam_role.dlm[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSDataLifecycleManagerServiceRole"
}

resource "aws_dlm_lifecycle_policy" "weekly" {
  count = var.enable_snapshots ? 1 : 0

  description        = "${local.name} weekly server snapshot, kept 4 weeks"
  execution_role_arn = aws_iam_role.dlm[0].arn
  state              = "ENABLED"

  policy_details {
    resource_types = ["INSTANCE"]
    target_tags    = { Backup = "weekly" }

    schedule {
      name      = "weekly-sunday-0100"
      copy_tags = true

      create_rule {
        cron_expression = "cron(30 19 ? * SAT *)" # Sunday 01:00 Asia/Colombo (UTC+5:30)
      }

      retain_rule {
        count = 4
      }
    }
  }
}
