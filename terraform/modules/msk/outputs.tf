output "bootstrap_brokers" {
  description = "Kafka bootstrap brokers (plaintext) — use in Spring Boot: spring.kafka.bootstrap-servers"
  value       = aws_msk_cluster.main.bootstrap_brokers
}

output "bootstrap_brokers_tls" {
  description = "Kafka bootstrap brokers (TLS) — use when encryption in transit is required"
  value       = aws_msk_cluster.main.bootstrap_brokers_tls
}

output "cluster_arn" {
  description = "MSK cluster ARN — use in IAM policies to grant producer/consumer access"
  value       = aws_msk_cluster.main.arn
}

output "zookeeper_connect_string" {
  description = "ZooKeeper connection string — needed for some Kafka admin tools"
  value       = aws_msk_cluster.main.zookeeper_connect_string
}
