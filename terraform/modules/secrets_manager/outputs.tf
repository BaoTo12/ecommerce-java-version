output "db_credentials_arn" {
  description = "ARN of the DB credentials secret — use in IAM policy for secretsmanager:GetSecretValue"
  value       = aws_secretsmanager_secret.db_credentials.arn
}

output "jwt_secret_arn" {
  description = "ARN of the JWT secret — use in IAM policy for secretsmanager:GetSecretValue"
  value       = aws_secretsmanager_secret.jwt_secret.arn
}

output "kms_key_arn" {
  description = "ARN of the KMS key used to encrypt secrets — use in IAM policy for kms:Decrypt"
  value       = aws_kms_key.secrets.arn
}

output "kms_key_alias" {
  description = "Alias of the secrets KMS key"
  value       = aws_kms_alias.secrets.name
}
