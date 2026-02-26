output "grafana_url" {
  description = "Grafana dashboard URL (default credentials: admin / grafana_admin_password variable)"
  value       = "http://localhost:3000"
}

output "prometheus_url" {
  description = "Prometheus UI and API URL"
  value       = "http://localhost:9090"
}

output "loki_url" {
  description = "Loki push/query API URL"
  value       = "http://localhost:3100"
}

output "tempo_url" {
  description = "Tempo trace query UI URL"
  value       = "http://localhost:3200"
}
