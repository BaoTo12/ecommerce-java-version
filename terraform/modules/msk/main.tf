# MSK (Amazon Managed Streaming for Apache Kafka)

# PATTERNS ENABLED BY KAFKA:
#
#   1. SAGA PATTERN (Orchestration-based, managed by order-service):
#      order-service → OrderCreated event → inventory-service (reserve stock)
#                                         → payment-service (process payment)
#      Each step either succeeds (continue) or compensates (rollback previous steps)
#
#   2. TRANSACTIONAL OUTBOX PATTERN:
#      Problem: Writing to DB and publishing to Kafka are TWO operations.
#               If Kafka publish fails after DB write → data inconsistency.
#      Solution: Write event to "outbox" table IN SAME DB TRANSACTION as business data.
#               Outbox poller reads from outbox table → publishes to Kafka → marks as sent.
#               At-least-once delivery guarantees.

resource "aws_msk_cluster" "main" {
  cluster_name  = var.cluster_name
  kafka_version = "3.5.1"
  # One broker per subnet (per AZ). With 3 private subnets → 3 brokers.
  # 3 brokers = minimum for production HA (fault tolerance of 1 broker failure).
  number_of_broker_nodes = length(var.subnet_ids)

  broker_node_group_info {
    # kafka.t3.small: cheapest option for learning. Use kafka.m5.large for production.
    instance_type   = "kafka.t3.small"
    client_subnets  = var.subnet_ids
    security_groups = [aws_security_group.msk.id]

    storage_info {
      ebs_storage_info {
        volume_size = 20 # GB per broker. Increase for higher throughput requirements.
      }
    }
  }

  encryption_info {
    encryption_in_transit {
      # TLS_PLAINTEXT: Clients can connect with or without TLS.
      # For production, use "TLS" to enforce encryption in transit.
      client_broker = "TLS_PLAINTEXT"
      # Always encrypt broker-to-broker traffic even within the cluster.
      in_cluster = true
    }
  }

  # MSK configuration — fine-tune Kafka behavior
  configuration_info {
    arn      = aws_msk_configuration.main.arn
    revision = aws_msk_configuration.main.latest_revision
  }

  tags = {
    Name        = var.cluster_name
    Environment = var.environment
  }
}

# Kafka configuration properties
resource "aws_msk_configuration" "main" {
  kafka_versions = ["3.5.1"]
  name           = "${var.cluster_name}-config"

  server_properties = <<-PROPERTIES
    # Auto-create topics (convenient for development)
    auto.create.topics.enable=false

    # Default replication factor — with 3 brokers, replicate each topic 3 times
    default.replication.factor=3

    # Minimum in-sync replicas — at least 2 brokers must acknowledge a write
    # Prevents data loss if one broker crashes
    min.insync.replicas=2

    # Retention: keep messages for 7 days
    log.retention.hours=168

    # Message size limit: 1MB default
    message.max.bytes=1048576
  PROPERTIES
}

# ==============================================================================
# SECURITY GROUP — Kafka network access
# Ports:
#   9092 = Plaintext (KAFKA_PLAINTEXT)
#   9094 = TLS (KAFKA_SSL)
# ==============================================================================
resource "aws_security_group" "msk" {
  name        = "${var.cluster_name}-msk-sg"
  description = "Allow Kafka traffic from within VPC only"
  vpc_id      = var.vpc_id

  ingress {
    description = "Kafka plaintext + TLS from VPC"
    from_port   = 9092
    to_port     = 9094
    protocol    = "tcp"
    cidr_blocks = var.allowed_cidr_blocks
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.cluster_name}-msk-sg"
  }
}
