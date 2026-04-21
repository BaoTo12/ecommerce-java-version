output "cluster_name" {
  description = "EKS cluster name — use with: aws eks update-kubeconfig --name <value> --region ap-southeast-1"
  value       = aws_eks_cluster.main.name
}

output "cluster_endpoint" {
  description = "API server endpoint — used by kubectl and Helm"
  value       = aws_eks_cluster.main.endpoint
}

output "cluster_ca_certificate" {
  description = "Base64-encoded certificate authority data — required for kubeconfig"
  value       = aws_eks_cluster.main.certificate_authority[0].data
}

output "oidc_provider_arn" {
  description = "OIDC provider ARN — used in IRSA trust policies in iam module"
  value       = aws_iam_openid_connect_provider.eks.arn
}

output "oidc_provider_url" {
  description = "OIDC provider URL — used to build IRSA condition keys"
  value       = aws_eks_cluster.main.identity[0].oidc[0].issuer
}
