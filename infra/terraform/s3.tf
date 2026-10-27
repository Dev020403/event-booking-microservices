# Event images (checklist 2.5).
# event-service hands out presigned URLs; clients upload to and download from this bucket directly.
# The bucket is private: every read goes through a short-lived presigned GET URL.

data "aws_caller_identity" "current" {}

locals {
  # Bucket names are global across all AWS accounts, so the account ID keeps the name unique on real AWS.
  event_images_bucket = "${local.project}-${var.environment}-event-images-${data.aws_caller_identity.current.account_id}"

  # Object key prefix written by event-service: events/{eventId}/{uuid}.{ext}
  event_images_prefix = "events/"
}

resource "aws_s3_bucket" "event_images" {
  bucket = local.event_images_bucket

  # Allow `terraform destroy` to delete a non-empty bucket on Floci only; on real AWS, losing images must be deliberate.
  force_destroy = var.use_floci
}

# Ownership: the bucket owner owns every object and ACLs are disabled. Access is controlled only by policies.
resource "aws_s3_bucket_ownership_controls" "event_images" {
  bucket = aws_s3_bucket.event_images.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

# Block every form of public access. Presigned URLs still work because they are signed requests, not public reads.
resource "aws_s3_bucket_public_access_block" "event_images" {
  bucket = aws_s3_bucket.event_images.id

  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = true
  restrict_public_buckets = true
}

# Encryption at rest with S3-managed keys (SSE-S3). Can be switched to a KMS key in checklist 1.2.
resource "aws_s3_bucket_server_side_encryption_configuration" "event_images" {
  bucket = aws_s3_bucket.event_images.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# CORS: lets a browser front end PUT to a presigned URL and display images from another origin.
# Not needed for curl or Postman, which do not enforce CORS.
resource "aws_s3_bucket_cors_configuration" "event_images" {
  bucket = aws_s3_bucket.event_images.id

  cors_rule {
    allowed_methods = ["PUT", "GET", "HEAD"]
    allowed_origins = var.event_images_cors_allowed_origins
    allowed_headers = ["Content-Type"]
    expose_headers  = ["ETag"]
    max_age_seconds = 3000
  }
}

# Lifecycle: clean up multipart uploads that were started but never completed (they are billed but invisible).
# Known gap: a single PUT that is never confirmed via the API leaves an orphan object; handled later if needed.
resource "aws_s3_bucket_lifecycle_configuration" "event_images" {
  bucket = aws_s3_bucket.event_images.id

  rule {
    id     = "abort-incomplete-multipart-uploads"
    status = "Enabled"

    filter {}

    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }
}

# Bucket policy: reject any request not made over HTTPS.
# Real AWS only: the Floci endpoint is plain HTTP, so this policy would block every request if Floci enforced it.
data "aws_iam_policy_document" "event_images_tls_only" {
  statement {
    sid     = "DenyInsecureTransport"
    effect  = "Deny"
    actions = ["s3:*"]
    resources = [
      aws_s3_bucket.event_images.arn,
      "${aws_s3_bucket.event_images.arn}/*",
    ]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "event_images_tls_only" {
  count = var.use_floci ? 0 : 1

  bucket = aws_s3_bucket.event_images.id
  policy = data.aws_iam_policy_document.event_images_tls_only.json

  # S3 rejects policy updates while the public access block is being applied.
  depends_on = [aws_s3_bucket_public_access_block.event_images]
}

# Least-privilege permissions event-service needs for images. Attached to its role in checklist 1.1.
# Floci does not enforce IAM, so this is for practice and for real AWS later.
# HeadObject is authorised by s3:GetObject; presigning itself needs no permission, but the signer's
# permissions are checked when the URL is used.
data "aws_iam_policy_document" "event_service_images" {
  statement {
    sid    = "EventImagesObjects"
    effect = "Allow"
    actions = [
      "s3:PutObject",
      "s3:GetObject",
      "s3:DeleteObject",
    ]
    resources = ["${aws_s3_bucket.event_images.arn}/${local.event_images_prefix}*"]
  }
}

resource "aws_iam_policy" "event_service_images" {
  name        = "${local.project}-${var.environment}-event-service-images"
  description = "event-service access to event images in S3"
  policy      = data.aws_iam_policy_document.event_service_images.json
}

output "event_images_bucket" {
  description = "Bucket name for event-service (S3 bucket setting)."
  value       = aws_s3_bucket.event_images.bucket
}

output "event_images_bucket_arn" {
  value = aws_s3_bucket.event_images.arn
}

output "event_service_images_policy_arn" {
  value = aws_iam_policy.event_service_images.arn
}

output "account_id" {
  value = data.aws_caller_identity.current.account_id
}
