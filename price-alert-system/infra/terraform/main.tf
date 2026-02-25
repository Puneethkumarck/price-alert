module "network" {
  source = "./modules/network"
}

module "infrastructure" {
  source = "./modules/infrastructure"

  network_id         = module.network.network_id
  kafka_image_tag    = var.kafka_image_tag
  postgres_image_tag = var.postgres_image_tag
  redis_image_tag    = var.redis_image_tag
  db_user            = var.db_user
  db_name            = var.db_name
  db_password        = var.db_password
}

module "applications" {
  source = "./modules/applications"

  network_id              = module.network.network_id
  source_path             = var.project_source_path
  image_tag               = var.app_image_tag
  jwt_secret              = var.jwt_secret
  db_password             = var.db_password
  db_user                 = var.db_user
  db_name                 = var.db_name
  kafka_bootstrap         = module.infrastructure.kafka_bootstrap_internal
  postgres_host           = module.infrastructure.postgres_host
  redis_host              = module.infrastructure.redis_host
  kafka_init_container_id = module.infrastructure.kafka_init_container_id

  depends_on = [module.infrastructure]
}

module "monitoring" {
  source = "./modules/monitoring"

  network_id             = module.network.network_id
  grafana_admin_password = var.grafana_admin_password
  monitoring_config_path = "${var.project_source_path}/monitoring"

  depends_on = [module.applications]
}
