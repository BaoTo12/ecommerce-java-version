resource "aws_iam_role" "irsa" {
  name = var.role_name

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRoleWithWebIdentity"
      Effect = "Allow"
      Principal = {
        Federated = var.oidc_provider_arn
      }
      Condition = {
        StringEquals = {
          "${replace(var.oidc_provider_url, "https://", "")}:sub": "system:serviceaccount:${var.namespace}:${var.service_account_name}"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "irsa" {
  name   = "${var.role_name}-policy"
  role   = aws_iam_role.irsa.id
  policy = var.policy_json
}
