variable "network_id" {
  description = "Docker network ID from the network module"
  type        = string
}

variable "grafana_admin_password" {
  description = "Grafana admin console password"
  type        = string
  sensitive   = true
}

variable "monitoring_config_path" {
  description = "Absolute path to the monitoring/ directory (bind-mounted into observability containers)"
  type        = string
}
