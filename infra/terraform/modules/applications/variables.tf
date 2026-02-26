variable "network_id" {
  description = "Docker network ID from the network module"
  type        = string
}

variable "source_path" {
  description = "Absolute path to price-alert-system/ root (Dockerfile context and JAR location)"
  type        = string
}

variable "image_tag" {
  description = "Tag applied to all locally-built Spring Boot application images"
  type        = string
  default     = "local"
}

variable "jwt_secret" {
  description = "HS256 secret for JWT signing in alert-api"
  type        = string
  sensitive   = true
}

variable "db_password" {
  description = "PostgreSQL password"
  type        = string
  sensitive   = true
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

variable "kafka_bootstrap" {
  description = "Kafka bootstrap server address for container-to-container communication"
  type        = string
}

variable "postgres_host" {
  description = "PostgreSQL hostname"
  type        = string
}

variable "redis_host" {
  description = "Redis hostname"
  type        = string
}

variable "kafka_init_container_id" {
  description = "ID of the kafka-init container — used to enforce ordering (apps start only after topics are created)"
  type        = string
}
