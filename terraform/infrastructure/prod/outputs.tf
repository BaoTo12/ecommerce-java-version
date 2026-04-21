# ==============================================================================
# OUTPUTS — Values exposed after terraform apply
#
# Outputs serve two purposes:
#   1. Display useful information to the operator after apply completes
#   2. Allow other Terraform root modules to reference these values
#      (via terraform_remote_state data source)
#
# sensitive = true: value is computed but not printed to console.
# ==============================================================================

output "vpc_id" {
  description = "VPC ID — useful for debugging network issues"
  value       = module.vpc.vpc_id
}

output "eks_cluster_name" {
  description = "EKS cluster name — use with: aws eks update-kubeconfig --name <value>"
  value       = module.eks.cluster_name
}

output "eks_cluster_endpoint" {
  description = "EKS API server endpoint — used by kubectl and Helm"
  value       = module.eks.cluster_endpoint
}

output "ecr_repository_urls" {
  description = "ECR URLs per service — use these in your GitHub Actions docker push commands"
  value       = { for k, v in module.ecr : k => v.repository_url }
}

output "rds_endpoints" {
  description = "RDS endpoint per service — pass to Helm chart values"
  value       = { for k, v in module.rds : k => v.rds_endpoint }
}

/*
output "msk_bootstrap_brokers" {
  description = "Kafka bootstrap brokers — use in Spring Boot kafka.bootstrap-servers config"
  value       = module.msk.bootstrap_brokers
}
*/

output "secrets_manager_arns" {
  description = "ARNs of created secrets — use in IAM policies and External Secrets Operator config"
  value = {
    db_credentials = module.secrets.db_credentials_arn
    jwt_secret     = module.secrets.jwt_secret_arn
  }
}

output "irsa_role_arns" {
  description = "IAM Role ARNs for each microservice (used for ServiceAccount annotations)"
  value       = { for k, v in module.irsa : k => v.role_arn }
}
