terraform {
  required_version = ">= 1.6.0, < 2.0.0"

  backend "s3" {
    # S3 bucket created by bootstrap/main.tf
    # Replace <ACCOUNT_ID> with your AWS account ID, OR run:
    # terraform init -backend-config="bucket=$(terraform -chdir=../../bootstrap output -raw state_bucket_name)"
    bucket = "ecommerce-terraform-state-582604091743"

    # Path within the bucket where state is stored.
    # Pattern: environments/<env>/<stack>/terraform.tfstate
    # Using a nested path lets you have dev/staging/prod states in one bucket.
    key = "environments/prod/terraform.tfstate"

    region = "ap-southeast-1"

    # Encrypt state file in transit AND at rest.
    # encrypt = true uses the kms_key_id below for encryption.
    encrypt = true

    kms_key_id = "alias/terraform-state-key"

    dynamodb_table = "terraform-state-lock"
  }

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
    # tls: used in eks module to get OIDC thumbprint for IRSA
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "terraform"
      Repository  = "ecommerce-java-version"
    }
  }
}
