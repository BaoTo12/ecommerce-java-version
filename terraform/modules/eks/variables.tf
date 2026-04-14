variable "cluster_name" {
  description = "Name of the EKS cluster"
  type        = string
}

variable "subnet_ids" {
  description = "Subnet IDs for the EKS cluster and node group"
  type        = list(string)
}

variable "desired_capacity" {
  description = "Desired number of worker nodes"
  type        = number
  default     = 2
}

variable "max_capacity" {
  description = "Maximum number of worker nodes"
  type        = number
  default     = 3
}

variable "min_capacity" {
  description = "Minimum number of worker nodes"
  type        = number
  default     = 1
}

variable "instance_types" {
  description = "Instance types for the worker nodes"
  type        = list(string)
  default     = ["t3.medium"]
}
