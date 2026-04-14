variable "cluster_name" {
  description = "Name of the MSK cluster"
  type        = string
}

variable "subnet_ids" {
  description = "Subnet IDs for the MSK brokers"
  type        = list(string)
}

variable "vpc_id" {
  description = "VPC ID"
  type        = string
}

variable "allowed_cidr_blocks" {
  description = "CIDR blocks allowed to connect to MSK"
  type        = list(string)
}

variable "environment" {
  description = "Environment name"
  type        = string
}
