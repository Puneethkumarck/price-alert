output "kafka_bootstrap_internal" {
  description = "Kafka bootstrap address for container-to-container communication"
  value       = "kafka:19092"
}

output "postgres_host" {
  description = "PostgreSQL hostname (Docker service DNS name)"
  value       = "postgres"
}

output "redis_host" {
  description = "Redis hostname (Docker service DNS name)"
  value       = "redis"
}

output "kafka_init_container_id" {
  description = "ID of the kafka-init one-shot container — consumed by the applications module to establish ordering"
  value       = docker_container.kafka_init.id
}
