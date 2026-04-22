
# VPC --> create firstly
module "vpc" {
  source          = "../../modules/vpc"
  project_name    = var.project_name
  environment     = var.environment
  vpc_cidr        = var.vpc_cidr
  public_subnets  = var.public_subnets
  private_subnets = var.private_subnets
  azs             = var.azs
  cluster_name    = var.cluster_name
}


# MODULE: EKS
# Traffic flows: Internet → Load Balancer (public subnet) → Service → Pod (private subnet)
module "eks" {
  source = "../../modules/eks"

  cluster_name             = var.cluster_name
  subnet_ids               = module.vpc.private_subnet_ids
  control_plane_subnet_ids = concat(module.vpc.public_subnet_ids, module.vpc.private_subnet_ids)
  desired_capacity         = 2
  max_capacity             = 4
  min_capacity             = 2
  instance_types           = ["t3.small"]
}

# MODULE: ECR 
module "ecr" {
  source = "../../modules/ecr"

  for_each    = toset(["user-service", "order-service", "inventory-service", "payment-service", "notification-service"])
  name        = each.key
  environment = var.environment
}

# MODULE: RDS (one per microservice — Database-per-Service pattern)
module "rds" {
  source = "../../modules/rds"

  # for_each maps each service name to its DB config defined in variables.tf
  for_each = var.services

  project_name = var.project_name
  environment  = var.environment
  service_name = each.key
  vpc_id       = module.vpc.vpc_id
  subnet_ids   = module.vpc.private_subnet_ids

  # Only allow inbound PostgreSQL connections from within the VPC
  allowed_cidr_blocks = [var.vpc_cidr]

  db_name  = each.value.db_name
  username = each.value.db_user
  port     = each.value.port

  # password is required — comes from var.db_password (terraform.tfvars or TF_VAR_db_password env)
  # The per-service user+password would be managed by a DB provisioning tool (e.g. Vault).
  password = var.db_password
}

/*
# MODULE: MSK (Amazon Managed Streaming for Apache Kafka)
module "msk" {
  source = "../../modules/msk"

  cluster_name        = "${var.project_name}-msk-${var.environment}"
  vpc_id              = module.vpc.vpc_id
  subnet_ids          = module.vpc.private_subnet_ids
  allowed_cidr_blocks = [var.vpc_cidr]
  environment         = var.environment
}
*/

# MODULE: SECRETS MANAGER
module "secrets" {
  source = "../../modules/secrets_manager"

  project_name = var.project_name
  environment  = var.environment

  # NOTE: db_password and jwt_secret come from terraform.tfvars (gitignored)
  # Never hardcode secrets. In CI/CD: set TF_VAR_db_password as an env variable.
  db_username = "dbadmin"
  db_password = var.db_password

  # Using RDS addresses from the rds module — pass order-service DB as example
  # In a real setup you'd store per-service credentials
  db_host = module.rds["order-service"].rds_address

  jwt_secret = var.jwt_secret
}


# MODULE: IAM (IRSA — IAM Roles for Service Accounts)
# We use for_each to create roles for all microservices defined in var.services
module "irsa" {
  source = "../../modules/iam"

  for_each = var.services

  role_name            = "${var.project_name}-${each.key}-role"
  oidc_provider_arn    = module.eks.oidc_provider_arn
  oidc_provider_url    = module.eks.oidc_provider_url
  namespace            = "default"
  service_account_name = "${each.key}-sa"

  # Standard policy for all services: allow reading application secrets
  policy_json = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "ReadSecretsManager"
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue",
          "secretsmanager:DescribeSecret"
        ]
        # Allow reading any secret prefixed with project name.
        # Fixed: using /* to match "project/env/..." pattern used in secrets_manager module
        Resource = "arn:aws:secretsmanager:${var.aws_region}:*:secret:${var.project_name}/*"
      },
      {
        Sid    = "DecryptSecrets"
        Effect = "Allow"
        Action = [
          "kms:Decrypt",
          "kms:DescribeKey"
        ]
        Resource = module.secrets.kms_key_arn
      }
    ]
  })
}

