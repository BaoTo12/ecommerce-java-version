resource "aws_db_instance" "main" {
  identifier           = "${var.project_name}-db"
  allocated_storage    = var.allocated_storage
  storage_type         = "gp2"
  engine               = "postgres"
  engine_version       = "15.3"
  instance_class       = var.instance_class
  db_name              = var.db_name
  username             = var.username
  password             = var.password
  parameter_group_name = "default.postgres15"
  skip_final_snapshot  = true
  vpc_security_group_ids = [aws_security_group.rds.id]
  db_subnet_group_name   = aws_db_subnet_group.main.id

  tags = {
    Name        = "${var.project_name}-db"
    Environment = var.environment
  }
}

resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-db-subnet-group"
  subnet_ids = var.subnet_ids

  tags = {
    Name = "${var.project_name}-db-subnet-group"
  }
}

resource "aws_security_group" "rds" {
  name        = "${var.project_name}-rds-sg"
  description = "Allow inbound traffic to RDS from EKS"
  vpc_id      = var.vpc_id

  ingress {
    from_port   = 5432
    to_port     = 5432
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
    Name = "${var.project_name}-rds-sg"
  }
}
