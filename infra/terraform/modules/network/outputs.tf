output "network_id" {
  description = "Docker network ID — passed to all other modules"
  value       = docker_network.price_alert.id
}

output "network_name" {
  description = "Docker network name"
  value       = docker_network.price_alert.name
}
