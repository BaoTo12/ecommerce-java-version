# ==============================================================================
# BOOTSTRAP — Run this ONCE before anything else.
#
# PURPOSE: Creates the S3 bucket and DynamoDB table that Terraform will use
#          to store state remotely for all future infrastructure runs.
#
# IMPORTANT: After running, commit the terraform.tfstate file or store it safely.
#            If you lose it, you lose the ability to manage these resources via Terraform.
# ==============================================================================

terraform {
  required_version = ">= 1.6.0"

  # WHY local backend?
  # We CANNOT use S3 backend here because S3 doesn't exist yet.
  # This is the step that CREATES the S3 bucket.
  backend "local" {}

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = "ap-southeast-1"

  default_tags {
    tags = {
      Project   = "ecommerce"
      ManagedBy = "terraform-bootstrap"
    }
  }
}

# ? DATA SOURCES

# Get the current AWS account ID dynamically.
# Used to make the S3 bucket name globally unique (bucket names are global in AWS).
data "aws_caller_identity" "current" {}

# KMS KEY — Encrypts the Terraform state file stored in S3
#
# WHY create a KMS key for state encryption?
# The .tfstate file contains EVERYTHING Terraform knows about your infrastructure:
#   - Database hostnames and ports
#   - Resource IDs and ARNs
#   - Sometimes even passwords (if not careful)
# This key ensures even if someone gains S3 access, they cannot read the state.
#
# WHY is this key DIFFERENT from the KMS key in secrets_manager module?
# See CONCEPTS.md Question 3 — short answer: Separation of Concerns + Least Privilege.
#   This key:    encrypts Terraform state (read by: Terraform CLI, CI/CD)
#   Secrets key: encrypts app secrets (read by: microservice pods at runtime)
# ==============================================================================
resource "aws_kms_key" "terraform_state" {
  description = "Encrypts Terraform state file stored in S3"

  # Key rotation: AWS automatically creates a new cryptographic material each year.
  # Old data encrypted with old material is still readable (AWS handles this).
  # This is a security best practice — rotate keys to limit exposure window.
  enable_key_rotation = true

  # Deletion window: After you destroy this key, AWS waits this many days before
  # actually deleting it. This gives you time to recover if deleted by mistake.
  # Minimum is 7 days, maximum is 30 days.
  deletion_window_in_days = 30

  # Key policy: Defines who can use and manage this key.
  # Without this, only the key creator has access (too restrictive).
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        # Root account must always have admin access to KMS keys.
        # If you don't include this, you can accidentally lock yourself out.
        Sid    = "EnableRootAccountAccess"
        Effect = "Allow"
        Principal = {
          AWS = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
        }
        Action   = "kms:*"
        Resource = "*"
      }
    ]
  })
}

# KMS ALIAS — Human-readable name for the KMS key
resource "aws_kms_alias" "terraform_state" {
  name          = "alias/terraform-state-key"
  target_key_id = aws_kms_key.terraform_state.id
}

# S3 BUCKET — Stores the Terraform state file
#
# WHY remote state in S3 instead of local file?
#   1. Team collaboration: everyone reads/writes the same state
#   2. CI/CD pipelines can access it
#   3. S3 gives you versioning (rollback on broken apply)
#   4. State locking via DynamoDB prevents concurrent modifications
# ==============================================================================
resource "aws_s3_bucket" "terraform_state" {
  # WHY include account ID in bucket name?
  # S3 bucket names are GLOBALLY unique across all AWS accounts.
  # Using account ID ensures uniqueness without random suffixes.
  bucket = "ecommerce-terraform-state-${data.aws_caller_identity.current.account_id}"

  # force_destroy = false means Terraform WILL REFUSE to delete this bucket
  # if it has any files in it. This protects you from accidentally destroying
  # your entire state history with a single `terraform destroy`.
  force_destroy = false
}

# Enable versioning: every time state is written, S3 keeps the previous version.
# If an apply corrupts the state, you can restore from a previous version.
resource "aws_s3_bucket_versioning" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id
  versioning_configuration {
    status = "Enabled"
  }
}

# Encrypt state file at rest using the KMS key we created above.
resource "aws_s3_bucket_server_side_encryption_configuration" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.terraform_state.arn
    }
    # bucket_key_enabled reduces the number of KMS API calls S3 makes.
    # Without this, every S3 operation calls KMS → expensive + slower.
    # With this, S3 caches a "bucket key" derived from your KMS key.
    bucket_key_enabled = true
  }
}

# Block ALL public access to this bucket.
# There is NO scenario where Terraform state should be publicly readable.
resource "aws_s3_bucket_public_access_block" "terraform_state" {
  bucket = aws_s3_bucket.terraform_state.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Delete old non-current versions after 90 days to save storage costs.
# Current version (latest state) is always kept.
resource "aws_s3_bucket_lifecycle_configuration" "terraform_state" {
  # Must ensure versioning is enabled before configuring lifecycle
  depends_on = [aws_s3_bucket_versioning.terraform_state]

  bucket = aws_s3_bucket.terraform_state.id

  rule {
    id     = "cleanup-old-state-versions"
    status = "Enabled"

    noncurrent_version_expiration {
      noncurrent_days = 90
    }
  }
}

# ==============================================================================
# DYNAMODB TABLE — State Locking
#
# WHY is locking necessary?
# Terraform state is NOT safe for concurrent access.
# If two people run `terraform apply` at the same time:
#   Person A reads state → Person B reads state → Both modify → One overwrites the other
#   Result: resources exist in AWS but not in state (or vice versa) → CHAOS
#
# DynamoDB provides a distributed lock (mutex):
#   1. Before apply: Terraform writes a lock entry to DynamoDB
#   2. Another apply attempts to start → sees the lock → waits or errors
#   3. After apply completes: Terraform deletes the lock entry
# ==============================================================================
resource "aws_dynamodb_table" "terraform_lock" {
  name = "terraform-state-lock"

  # PAY_PER_REQUEST: No provisioned capacity needed.
  # Locks are infrequent (only during apply), so on-demand billing is cheapest.
  billing_mode = "PAY_PER_REQUEST"

  # LockID is the attribute Terraform uses. It must be exactly "LockID".
  hash_key = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }

  tags = {
    Name = "Terraform State Lock Table"
  }
}

# OUTPUTS — Copy these values into infrastructure/prod/backend.tf

output "state_bucket_name" {
  description = "Paste this into backend.tf > bucket ="
  value       = aws_s3_bucket.terraform_state.id
}

output "dynamodb_table_name" {
  description = "Paste this into backend.tf > dynamodb_table ="
  value       = aws_dynamodb_table.terraform_lock.name
}

output "kms_key_alias" {
  description = "Paste this into backend.tf > kms_key_id ="
  value       = aws_kms_alias.terraform_state.name
}

output "aws_region" {
  description = "Region where bootstrap resources were created"
  value       = "ap-southeast-1"
}
