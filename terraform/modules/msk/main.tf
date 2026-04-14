resource "aws_msk_cluster" "main" {
  cluster_name           = var.cluster_name
  kafka_version          = "3.4.0"
  number_of_broker_nodes = length(var.subnet_ids)

  broker_node_group_info {
    instance_type = "kafka.t3.small"
    client_subnets = var.subnet_ids
    security_groups = [aws_security_group.msk.id]
    storage_info {
      ebs_storage_info {
        volume_size = 20
      }
    }
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS_PLAINTEXT"
      in_cluster    = true
    }
  }

  tags = {
    Name        = var.cluster_name
    Environment = var.environment
  }
}

resource "aws_security_group" "msk" {
  name        = "${var.cluster_name}-msk-sg"
  description = "Allow inbound traffic to MSK from EKS"
  vpc_id      = var.vpc_id

  ingress {
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
