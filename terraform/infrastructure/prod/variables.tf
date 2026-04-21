# ==============================================================================
# VARIABLES — Input variables for the prod environment
#
# Variables are the "parameters" of your Terraform configuration.
# Values come from (in priority order):
#   1. -var flag: terraform apply -var="aws_region=us-east-1"
#   2. Environment variable: export TF_VAR_aws_region=us-east-1
#   3. terraform.tfvars file (gitignored for sensitive values)
#   4. terraform.tfvars.example (rename to terraform.tfvars and fill in)
#   5. default value in this file
# ==============================================================================

variable "aws_region" {
  description = "AWS region to deploy all resources"
  type        = string
  # BUG FIX: Original had "us-southeast-1" which is NOT a valid AWS region.
  # Valid Singapore region is "ap-southeast-1".
  default = "ap-southeast-1"
}

variable "project_name" {
  description = "Project name, used as a prefix for all resource names (e.g., ecommerce-vpc, ecommerce-eks)"
  type        = string
  default     = "ecommerce"
}

variable "environment" {
  description = "Deployment environment name"
  type        = string
  default     = "prod"

  # Validation prevents typos. If you pass "production" by mistake, Terraform errors immediately.
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be one of: dev, staging, prod."
  }
}

# ==============================================================================
# NETWORKING VARIABLES
# ==============================================================================

variable "vpc_cidr" {
  description = "CIDR block for the VPC. All subnets must be within this range."
  type        = string
  default     = "10.0.0.0/16"
  # /16 gives us 65,536 IPs — more than enough to divide into multiple subnets
}

variable "public_subnets" {
  description = "CIDR blocks for public subnets (one per AZ). Resources here get public IPs."
  type        = list(string)
  # WHY 3 subnets? High Availability — if one AZ goes down, two others still serve traffic.
  default = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
}

variable "private_subnets" {
  description = "CIDR blocks for private subnets (one per AZ). EKS nodes, RDS, MSK live here."
  type        = list(string)
  default     = ["10.0.11.0/24", "10.0.12.0/24", "10.0.13.0/24"]
}

variable "azs" {
  description = "Availability Zones to use. Must match the number of subnet CIDRs above."
  type        = list(string)
  # BUG FIX: Original had ["us-east-1a", "us-east-1b"] but the region was ap-southeast-1.
  # AZs must match the region. ap-southeast-1 has: a, b, c.
  default = ["ap-southeast-1a", "ap-southeast-1b", "ap-southeast-1c"]
}

# ==============================================================================
# EKS VARIABLES
# ==============================================================================

variable "cluster_name" {
  description = "Name of the EKS cluster"
  type        = string
  default     = "ecommerce-eks"
}

# ==============================================================================
# SERVICES — Database-per-Service configuration
#
# WHY use a map(object) here?
# Instead of copy-pasting module blocks for each of 5 services, we define
# them as data and use for_each in main.tf.
# This makes adding a new service as simple as adding a new entry here.
# ==============================================================================
variable "services" {
  description = "Map of microservices with their individual database configuration"
  type = map(object({
    db_name = string
    db_user = string
    port    = number
  }))

  default = {
    "user-service" = {
      db_name = "userdb"
      db_user = "user_svc"
      port    = 5432
    }
    "order-service" = {
      db_name = "orderdb"
      db_user = "order_svc"
      port    = 5432
    }
    "inventory-service" = {
      db_name = "inventorydb"
      db_user = "inventory_svc"
      port    = 5432
    }
    "payment-service" = {
      db_name = "paymentdb"
      db_user = "payment_svc"
      port    = 5432
    }
    "notification-service" = {
      db_name = "notificationdb"
      db_user = "notification_svc"
      port    = 5432
    }
  }
}

variable "db_instance_class" {
  description = "RDS instance class. t3.micro is fine for dev/learning; use db.t3.medium+ for prod."
  type        = string
  default     = "db.t3.micro"
}

variable "db_allocated_storage" {
  description = "Allocated storage in GB for each RDS instance."
  type        = number
  default     = 20
}

# ==============================================================================
# SENSITIVE VARIABLES — These must NEVER have default values
#
# WHY no defaults?
# If you set a default like default = "MyPassword123", it gets committed to git.
# Sensitive variables must come from:
#   - terraform.tfvars (gitignored)
#   - Environment variables: export TF_VAR_db_password="..."
#   - CI/CD secrets: GitHub Actions secrets → env variable
# ==============================================================================

variable "db_password" {
  description = "Master password for all RDS instances. Must be at least 8 characters."
  type        = string
  sensitive   = true
  # sensitive = true: Terraform will NOT print this value in plan/apply output.
  # It will show "<sensitive>" instead.
}

variable "jwt_secret" {
  description = "JWT signing key for shared-security module. Use a long random string (32+ chars)."
  type        = string
  sensitive   = true
}
