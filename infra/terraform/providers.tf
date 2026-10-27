locals {
  project = "event-booking"
}

provider "aws" {
  region  = var.aws_region
  profile = var.aws_profile

  # Floci serves S3 at localhost:4566, so bucket-name subdomains won't resolve.
  s3_use_path_style = var.use_floci

  # Floci has no EC2 instance metadata service.
  skip_metadata_api_check = var.use_floci

  # Note: provider v6 manages S3 tags via S3 Control at "<account-id>.localhost:4566".
  # On Windows that name needs a hosts-file entry: 127.0.0.1 000000000000.localhost

  # Route every service this project uses (across the learning checklist) to Floci.
  # Add a service here before using it, otherwise it silently targets real AWS.
  dynamic "endpoints" {
    for_each = var.use_floci ? [1] : []
    content {
      acm              = var.floci_endpoint
      budgets          = var.floci_endpoint
      ce               = var.floci_endpoint
      cloudwatch       = var.floci_endpoint
      codebuild        = var.floci_endpoint
      cognitoidp       = var.floci_endpoint
      ec2              = var.floci_endpoint
      ecr              = var.floci_endpoint
      ecs              = var.floci_endpoint
      eks              = var.floci_endpoint
      elasticache      = var.floci_endpoint
      elbv2            = var.floci_endpoint
      events           = var.floci_endpoint
      iam              = var.floci_endpoint
      kafka            = var.floci_endpoint
      kms              = var.floci_endpoint
      lambda           = var.floci_endpoint
      logs             = var.floci_endpoint
      rds              = var.floci_endpoint
      s3               = var.floci_endpoint
      scheduler        = var.floci_endpoint
      secretsmanager   = var.floci_endpoint
      servicediscovery = var.floci_endpoint
      ses              = var.floci_endpoint
      sesv2            = var.floci_endpoint
      sfn              = var.floci_endpoint
      sns              = var.floci_endpoint
      sqs              = var.floci_endpoint
      ssm              = var.floci_endpoint
      sts              = var.floci_endpoint
      wafv2            = var.floci_endpoint
    }
  }

  default_tags {
    tags = {
      Project     = local.project
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}
