variable "cluster_name" {
  description = "MSK cluster name"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID"
  type        = string
}

variable "subnet_ids" {
  description = "Private subnet IDs — one broker placed per subnet (per AZ)"
  type        = list(string)
}

variable "allowed_cidr_blocks" {
  description = "CIDR blocks allowed to connect to Kafka brokers"
  type        = list(string)
}

variable "environment" {
  description = "Environment name"
  type        = string
}
