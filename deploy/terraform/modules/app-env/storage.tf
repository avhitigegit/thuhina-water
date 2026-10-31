# S3 buckets (plan sections 5 and 11):
#  - backups:  nightly database dumps from backup.sh, deleted after backup_retention_days
#  - releases: deployment bundles (compose file, Nginx config, scripts) uploaded by GitHub Actions

locals {
  buckets = {
    backups  = { name = "${local.name}-backups-${local.account_id}", expire_days = var.backup_retention_days }
    releases = { name = "${local.name}-releases-${local.account_id}", expire_days = var.release_retention_days }
  }
}

resource "aws_s3_bucket" "this" {
  for_each = local.buckets

  bucket        = each.value.name
  force_destroy = var.force_destroy
}

resource "aws_s3_bucket_public_access_block" "this" {
  for_each = aws_s3_bucket.this

  bucket                  = each.value.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "this" {
  for_each = aws_s3_bucket.this

  bucket = each.value.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "this" {
  for_each = aws_s3_bucket.this

  bucket = each.value.id

  rule {
    id     = "expire-after-${local.buckets[each.key].expire_days}-days"
    status = "Enabled"

    filter {}

    expiration {
      days = local.buckets[each.key].expire_days
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }
}

resource "aws_s3_bucket_policy" "this" {
  for_each = aws_s3_bucket.this

  bucket = each.value.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "DenyInsecureTransport"
      Effect    = "Deny"
      Principal = "*"
      Action    = "s3:*"
      Resource  = [each.value.arn, "${each.value.arn}/*"]
      Condition = { Bool = { "aws:SecureTransport" = "false" } }
    }]
  })

  depends_on = [aws_s3_bucket_public_access_block.this]
}

# Short names used in the other files.
locals {
  backups_bucket_ref  = aws_s3_bucket.this["backups"]
  releases_bucket_ref = aws_s3_bucket.this["releases"]
}
