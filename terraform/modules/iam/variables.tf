variable "role_name" {
  description = "Name of the IAM role"
  type        = string
}

variable "oidc_provider_arn" {
  description = "ARN of the OIDC provider"
  type        = string
}

variable "oidc_provider_url" {
  description = "URL of the OIDC provider"
  type        = string
}

variable "namespace" {
  description = "Kubernetes namespace"
  type        = string
}

variable "service_account_name" {
  description = "Kubernetes service account name"
  type        = string
}

variable "policy_json" {
  description = "JSON policy document"
  type        = string
}
