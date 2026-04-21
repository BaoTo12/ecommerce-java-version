variable "project_name" {
  description = "Project name — used in secret path: <project>/<env>/secret-name"
  type        = string
}

variable "environment" {
  description = "Deployment environment"
  type        = string
}

variable "db_username" {
  description = "Database master username"
  type        = string
}

variable "db_password" {
  description = "Database master password"
  type        = string
  sensitive   = true
}

variable "db_host" {
  description = "Database hostname (from rds module output rds_address)"
  type        = string
}

variable "jwt_secret" {
  description = "JWT signing key for the shared-security module. Must be at least 32 characters."
  type        = string
  sensitive   = true
}
