# IAM Role for External Secrets Operator (ESO)
# Allows ESO to pull secrets from AWS Secrets Manager using IRSA.

module "eso_role" {
  source = "../../modules/iam"

  role_name            = "${var.project_name}-external-secrets-role"
  oidc_provider_arn    = module.eks.oidc_provider_arn
  oidc_provider_url    = module.eks.oidc_provider_url
  namespace            = "external-secrets-system"
  service_account_name = "external-secrets"

  policy_json = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "ReadSecrets"
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue",
          "secretsmanager:DescribeSecret"
        ]
        Resource = "arn:aws:secretsmanager:${var.aws_region}:*:secret:${var.project_name}/*"
      },
      {
        Sid    = "ListSecrets"
        Effect = "Allow"
        Action = "secretsmanager:ListSecrets"
        Resource = "*"
      },
      {
        Sid    = "DecryptKMS"
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

output "eso_role_arn" {
  value = module.eso_role.role_arn
}
