variable "network_id" {
  description = "Docker network ID from the network module"
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

variable "db_password" {
  description = "PostgreSQL password"
  type        = string
  sensitive   = true
}
