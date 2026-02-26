# ---------------------------------------------------------------------------
# Sensitive secrets — never set defaults to real values for shared envs
# ---------------------------------------------------------------------------

variable "jwt_secret" {
  description = "HS256 secret for JWT signing in alert-api (minimum 32 characters)"
  type        = string
  sensitive   = true
}

variable "db_password" {
  description = "PostgreSQL password for the 'alerts' user"
  type        = string
  sensitive   = true
  default     = "alerts_local"
}

variable "grafana_admin_password" {
  description = "Grafana admin console password"
  type        = string
  sensitive   = true
  default     = "admin"
}

# ---------------------------------------------------------------------------
# Non-sensitive configuration
# ---------------------------------------------------------------------------

variable "project_source_path" {
  description = "Absolute path to the price-alert-system/ root directory (used for Dockerfile context and monitoring config bind mounts)"
  type        = string
}

variable "kafka_image_tag" {
  description = "apache/kafka Docker image tag"
  type        = string
  default     = "3.9.0"
}

variable "postgres_image_tag" {
  description = "postgres Docker image tag"
  type        = string
  default     = "17"
}

variable "redis_image_tag" {
  description = "redis Docker image tag"
  type        = string
  default     = "7-alpine"
}

variable "app_image_tag" {
  description = "Tag applied to all locally-built Spring Boot application images"
  type        = string
  default     = "local"
}

variable "db_user" {
  description = "PostgreSQL username"
  type        = string
  default     = "alerts"
}

variable "db_name" {
  description = "PostgreSQL database name"
  type        = string
  default     = "price_alerts"
}
