variable "project_name" {
  description = "Project name prefix"
  type        = string
}

variable "environment" {
  description = "Deployment environment"
  type        = string
}

variable "service_name" {
  description = "Microservice name (e.g. order-service) — used to name the DB uniquely"
  type        = string
}

variable "instance_class" {
  description = "RDS instance type. db.t3.micro for dev, db.t3.medium+ for prod."
  type        = string
  default     = "db.t3.micro"
}

variable "allocated_storage" {
  description = "Storage in GB for this DB instance"
  type        = number
  default     = 20
}

variable "db_name" {
  description = "Name of the initial database to create inside the RDS instance"
  type        = string
}

variable "username" {
  description = "Master database username"
  type        = string
}

variable "password" {
  description = "Master database password — must be at least 8 characters"
  type        = string
  sensitive   = true
}

variable "subnet_ids" {
  description = "List of private subnet IDs for the DB subnet group"
  type        = list(string)
}

variable "vpc_id" {
  description = "VPC ID — used to create the security group in the right VPC"
  type        = string
}

variable "allowed_cidr_blocks" {
  description = "CIDR blocks allowed to connect to RDS (typically the VPC CIDR)"
  type        = list(string)
}

variable "port" {
  description = "Database port"
  type        = number
  default     = 5432
}
