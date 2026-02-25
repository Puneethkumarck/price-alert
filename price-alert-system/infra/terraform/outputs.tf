output "alert_api_url" {
  description = "REST API base URL"
  value       = "http://localhost:8080"
}

output "alert_api_health" {
  description = "Spring Boot actuator health endpoint"
  value       = "http://localhost:8080/actuator/health"
}

output "market_feed_simulator_ws" {
  description = "WebSocket price feed endpoint"
  value       = "ws://localhost:8085/v1/feed"
}

output "postgres_endpoint" {
  description = "PostgreSQL connection endpoint (host:port)"
  value       = "localhost:5432"
}

output "kafka_external_bootstrap" {
  description = "Kafka bootstrap server for external (host) clients"
  value       = "localhost:9092"
}

output "redis_endpoint" {
  description = "Redis connection endpoint"
  value       = "localhost:6379"
}

output "grafana_url" {
  description = "Grafana dashboard URL (admin/admin by default)"
  value       = module.monitoring.grafana_url
}

output "prometheus_url" {
  description = "Prometheus UI URL"
  value       = module.monitoring.prometheus_url
}

output "loki_url" {
  description = "Loki push/query API URL"
  value       = module.monitoring.loki_url
}

output "tempo_url" {
  description = "Tempo query API URL"
  value       = module.monitoring.tempo_url
}
