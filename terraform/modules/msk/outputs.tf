output "bootstrap_brokers" {
  description = "Bootstrap brokers for the MSK cluster"
  value       = aws_msk_cluster.main.bootstrap_brokers
}
