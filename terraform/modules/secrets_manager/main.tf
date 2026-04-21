# ? Secrets Manager: Stores sensitive values (DB passwords, JWT key) that microservices
#          need at runtime. Encrypts them with a dedicated KMS key.

# HOW microservices access these secrets:
#   1. Microservice pod has a Kubernetes ServiceAccount
#   2. ServiceAccount is annotated with an IAM role ARN (set up by iam module)
#   3. IAM role allows secretsmanager:GetSecretValue on specific secret ARNs
#   4. Spring Boot app uses AWS SDK to call GetSecretValue at startup
#   5. AWS SDK automatically uses temporary credentials from IMDS/IRSA
#
# ALTERNATIVE: External Secrets Operator (ESO)
#   ESO is a Kubernetes controller that syncs Secrets Manager → Kubernetes Secrets.
#   Pods then read from Kubernetes Secrets (no AWS SDK needed).
#   This is the recommended approach for production Kubernetes deployments.


# ==============================================================================
# KMS KEY — Encrypts secrets in Secrets Manager
#
# This is the SECOND KMS key in this architecture.
# KEY 1 (bootstrap): encrypts Terraform state in S3
# KEY 2 (this):      encrypts application secrets in Secrets Manager

resource "aws_kms_key" "secrets" {
  description         = "Encrypts application secrets in AWS Secrets Manager"
  enable_key_rotation = true

  tags = {
    Name = "${var.project_name}-secrets-kms-key"
  }
}

resource "aws_kms_alias" "secrets" {
  name          = "alias/${var.project_name}-secrets-key"
  target_key_id = aws_kms_key.secrets.id
}

# SECRET: Database Credentials
# Stores DB connection info for all services to use.
# Store as JSON so services can parse individual fields.
resource "aws_secretsmanager_secret" "db_credentials" {
  name        = "${var.project_name}/${var.environment}/db-credentials"
  description = "Master database credentials shared across services"
  kms_key_id  = aws_kms_alias.secrets.arn

  # recovery_window_in_days:
  # 0 = delete immediately (ok for dev/learning — avoids name conflicts on re-create)
  # 7-30 = wait N days before deleting (production safety net)
  recovery_window_in_days = 0

  tags = {
    Environment = var.environment
  }
}

resource "aws_secretsmanager_secret_version" "db_credentials" {
  secret_id = aws_secretsmanager_secret.db_credentials.id

  secret_string = jsonencode({
    username = var.db_username
    password = var.db_password
    host     = var.db_host
    port     = 5432
    engine   = "postgres"
  })
}


# SECRET: JWT Signing Key
resource "aws_secretsmanager_secret" "jwt_secret" {
  name        = "${var.project_name}/${var.environment}/jwt-secret"
  description = "JWT signing key for shared-security module"
  kms_key_id  = aws_kms_key.secrets.arn

  recovery_window_in_days = 0

  tags = {
    Environment = var.environment
  }
}

resource "aws_secretsmanager_secret_version" "jwt_secret" {
  secret_id = aws_secretsmanager_secret.jwt_secret.id

  secret_string = jsonencode({
    jwt_secret = var.jwt_secret
  })
}
