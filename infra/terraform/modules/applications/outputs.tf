output "alert_api_container_name" {
  description = "alert-api Docker container name"
  value       = docker_container.alert_api.name
}

output "tick_ingestor_container_name" {
  description = "tick-ingestor Docker container name"
  value       = docker_container.tick_ingestor.name
}

output "evaluator_container_name" {
  description = "evaluator Docker container name"
  value       = docker_container.evaluator.name
}

output "notification_persister_container_name" {
  description = "notification-persister Docker container name"
  value       = docker_container.notification_persister.name
}

output "simulator_container_name" {
  description = "market-feed-simulator Docker container name"
  value       = docker_container.market_feed_simulator.name
}
