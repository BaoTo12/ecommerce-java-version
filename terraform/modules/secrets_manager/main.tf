resource "aws_secretsmanager_secret" "db_credentials" {
  name        = "${var.project_name}-db-credentials"
  description = "Database credentials for ecommerce services"
  recovery_window_in_days = 0 # For learning/dev, set to 0 to allow immediate deletion if needed

  tags = {
    Environment = var.environment
  }
}

resource "aws_secretsmanager_secret_version" "db_credentials" {
  secret_id     = aws_secretsmanager_secret.db_credentials.id
  secret_string = jsonencode({
    username = var.db_username
    password = var.db_password
    engine   = "postgres"
    host     = var.db_host
    port     = 5432
  })
}

resource "aws_secretsmanager_secret" "jwt_secret" {
  name        = "${var.project_name}-jwt-secret"
  description = "JWT Signing Key"
  recovery_window_in_days = 0

  tags = {
    Environment = var.environment
  }
}

resource "aws_secretsmanager_secret_version" "jwt_secret" {
  secret_id     = aws_secretsmanager_secret.jwt_secret.id
  secret_string = jsonencode({
    jwt_secret = var.jwt_secret
  })
}
