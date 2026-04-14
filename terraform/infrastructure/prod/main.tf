provider "aws" {
  region = var.aws_region
}

module "vpc" {
  source = "../../modules/vpc"

  project_name    = var.project_name
  environment     = var.environment
  vpc_cidr        = var.vpc_cidr
  public_subnets  = var.public_subnets
  private_subnets = var.private_subnets
  azs             = var.azs
  cluster_name    = var.cluster_name
}

module "eks" {
  source = "../../modules/eks"

  cluster_name     = var.cluster_name
  subnet_ids       = module.vpc.private_subnet_ids
  desired_capacity = 2
  max_capacity     = 4
  min_capacity     = 2
  instance_types   = ["t3.medium"]
}

module "ecr" {
  source = "../../modules/ecr"

  for_each    = toset(["order-service", "payment-service", "inventory-service", "notification-service", "user-service"])
  name        = each.key
  environment = var.environment
}

module "rds" {
  source = "../../modules/rds"

  project_name        = var.project_name
  environment         = var.environment
  vpc_id              = module.vpc.vpc_id
  subnet_ids          = module.vpc.private_subnet_ids
  allowed_cidr_blocks = [var.vpc_cidr] # Allow VPC traffic
  db_name             = "ecommerce"
  username            = "dbadmin"
  password            = var.db_password
}

module "msk" {
  source = "../../modules/msk"

  cluster_name        = "${var.project_name}-msk"
  vpc_id              = module.vpc.vpc_id
  subnet_ids          = module.vpc.private_subnet_ids
  allowed_cidr_blocks = [var.vpc_cidr]
  environment         = var.environment
}

module "secrets" {
  source = "../../modules/secrets_manager"

  project_name = var.project_name
  environment  = var.environment
  db_username  = "dbadmin"
  db_password  = var.db_password
  db_host      = module.rds.rds_address
  jwt_secret   = var.jwt_secret
}

# Example IRSA for payment-service to access RDS (if needed via SDK)
module "irsa_payment" {
  source = "../../modules/iam"

  role_name            = "payment-rds-role"
  oidc_provider_arn    = module.eks.oidc_provider_arn
  oidc_provider_url    = module.eks.oidc_provider_url
  namespace            = "default"
  service_account_name = "payment-service-account"
  policy_json          = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = [
        "rds-db:connect"
      ]
      Effect   = "Allow"
      Resource = "*"
    }]
  })
}
