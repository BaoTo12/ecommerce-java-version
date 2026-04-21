# ==============================================================================
# ? MODULE: IAM — IRSA (IAM Roles for Service Accounts)
#
# PURPOSE: Creates an IAM role that a specific Kubernetes pod can assume,
#          giving it AWS permissions WITHOUT hardcoding access keys.
#
# HOW IRSA WORKS (step by step):
#
# *  Step 1 — Kubernetes ServiceAccount:
#     apiVersion: v1
#     kind: ServiceAccount
#     metadata:
#       name: order-service-sa
#       annotations:
#         eks.amazonaws.com/role-arn: arn:aws:iam::123456789:role/order-service-role
#
# *  Step 2 — Pod uses ServiceAccount:
#     spec:
#       serviceAccountName: order-service-sa
#
# *  Step 3 — AWS SDK magic:
#     When pod calls AWS SDK, it gets a JWT token from Kubernetes
#     → SDK calls STS:AssumeRoleWithWebIdentity with that JWT
#     → STS validates JWT against the OIDC provider we registered
#     → STS returns temporary credentials (AccessKeyId, SecretAccessKey, SessionToken)
#     → Pod uses those credentials for the AWS API call
#
# *  Step 4 — IAM Role trust policy verifies:
#     "Is this request coming from a pod running as ServiceAccount 'order-service-sa'
#      in namespace 'default' in cluster with OIDC issuer <url>?"
#     If yes → assume role is allowed.
#
# WHY not just give the Node IAM role permissions?
#   The Node IAM role is shared by ALL pods on a node.
#   Giving it Secrets Manager access means EVERY pod can read EVERY secret.
#   With IRSA: order-service-sa can ONLY read order-service secrets.
# *  This is minimum viable Least Privilege.
# ==============================================================================

resource "aws_iam_role" "irsa" {
  name = var.role_name

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRoleWithWebIdentity"
      Effect = "Allow"
      Principal = {
        # The OIDC provider from the EKS module allows it to issue identities
        Federated = var.oidc_provider_arn
      }
      Condition = {
        StringEquals = {
          # This condition pins the role to a SPECIFIC ServiceAccount in a SPECIFIC namespace.
          # Format: "<oidc_url_without_https>:sub": "system:serviceaccount:<namespace>:<sa_name>"
          # Example: "oidc.eks.ap-southeast-1.amazonaws.com/id/XXXX:sub":
          #          "system:serviceaccount:default:order-service-sa"
          "${replace(var.oidc_provider_url, "https://", "")}:sub" = "system:serviceaccount:${var.namespace}:${var.service_account_name}"
          # Also lock to the AWS STS audience
          "${replace(var.oidc_provider_url, "https://", "")}:aud" = "sts.amazonaws.com"
        }
      }
    }]
  })
}

# Attach the custom policy (defined by the caller in main.tf)
resource "aws_iam_role_policy" "irsa" {
  name   = "${var.role_name}-policy"
  role   = aws_iam_role.irsa.id
  policy = var.policy_json
}
