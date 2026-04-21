


resource "aws_db_instance" "main" {
  identifier = "${var.project_name}-${var.service_name}-db-${var.environment}"
  # Configuration for RDS
  engine            = "postgres"
  engine_version    = "15.7"
  instance_class    = var.instance_class
  storage_type      = "gp3"
  allocated_storage = var.allocated_storage
  storage_encrypted = true

  # Database credentials
  db_name  = var.db_name
  username = var.username
  password = var.password

  parameter_group_name = "default.postgres15"

  # Configure Firewall
  vpc_security_group_ids = [aws_security_group.rds.id]

  db_subnet_group_name = aws_db_subnet_group.main.name

  # Automated backups — keep 7 days of point-in-time recovery
  backup_retention_period = 7
  backup_window           = "03:00-04:00" # Singapore time = 10:00-11:00 UTC+7 (low traffic)

  # Maintenance window for minor version upgrades etc.
  maintenance_window = "Mon:04:00-Mon:05:00"

  # multi_az = true creates a standby replica in a different AZ.
  # If primary fails, RDS automatically failovers to standby (~60s downtime).
  multi_az = false

  # skip_final_snapshot: VERY IMPORTANT
  # true  = Terraform can destroy this DB with no backup (ok for dev/learning)
  # false = Terraform takes a final snapshot before destroying (production!)
  skip_final_snapshot = true

  tags = {
    Name        = "${var.project_name}-${var.service_name}-db"
    Environment = var.environment
    Service     = var.service_name
  }

}


# DB Subnet Group: Tells RDS which subnets it can place instances in.
# Must include subnets in at least 2 AZs (required by AWS, even for single-AZ).
resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-${var.service_name}-subnet-group"
  subnet_ids = var.subnet_ids

  tags = {
    Name = "${var.project_name}-${var.service_name}-db-subnet-group"
  }
}

# Security Group for RDS
resource "aws_security_group" "rds" {
  name        = "${var.project_name}-${var.service_name}-rds-sg"
  description = "Allow PostgreSQL inbound only from within VPC"
  vpc_id      = var.vpc_id

  ingress {
    description = "PostgreSQL from VPC"
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = var.allowed_cidr_blocks
  }

  egress {
    description = "Allow all outbound (needed for RDS to call AWS APIs)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-${var.service_name}-rds-sg"
  }
}
