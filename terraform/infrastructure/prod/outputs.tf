output "vpc_id" {
  value = module.vpc.vpc_id
}

output "eks_cluster_endpoint" {
  value = module.eks.cluster_endpoint
}

output "rds_endpoint" {
  value = module.rds.rds_endpoint
}

output "msk_bootstrap_brokers" {
  value = module.msk.bootstrap_brokers
}

output "ecr_repository_urls" {
  value = { for k, v in module.ecr : k => v.repository_url }
}
