# The S3 bucket that holds the environment's Terraform state (../environment), with S3's own lock
# file as its lock. `make aws-up` applies this once per account and region, when the bucket is
# missing; it outlives every environment, so `make aws-down` leaves it. Its own state is local, in
# this directory, and git-ignored: the bucket is found again by its name, which the account and
# region give.

terraform {
  required_version = ">= 1.11"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

variable "region" {
  description = "The AWS region the environment is built in."
  type        = string
  default     = "eu-central-1"
}

provider "aws" {
  region = var.region

  # Not an environment's run: the bucket serves every run.
  default_tags {
    tags = {
      project = "ecomm"
      run     = "bootstrap"
    }
  }
}

data "aws_caller_identity" "current" {}

resource "aws_s3_bucket" "state" {
  bucket = "ecomm-tfstate-${data.aws_caller_identity.current.account_id}-${var.region}"
}

# Every state written is kept, so a bad apply's state can be gone back to.
resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id
  versioning_configuration {
    status = "Enabled"
  }
}

# State holds every resource's attributes, so it is encrypted at rest, and never public.
resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket                  = aws_s3_bucket.state.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Old versions of a state are kept for 30 days.
resource "aws_s3_bucket_lifecycle_configuration" "state" {
  bucket = aws_s3_bucket.state.id
  rule {
    id     = "old-states"
    status = "Enabled"
    filter {}
    noncurrent_version_expiration {
      noncurrent_days = 30
    }
  }
}

output "bucket" {
  description = "The state bucket's name, which ../environment's backend is given."
  value       = aws_s3_bucket.state.id
}
