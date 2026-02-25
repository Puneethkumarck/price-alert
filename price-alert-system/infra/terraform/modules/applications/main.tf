# ---------------------------------------------------------------------------
# NOTE: Run `./gradlew clean build -x test --parallel` from price-alert-system/
# before `terraform apply`. Terraform does not manage the Gradle build.
# The triggers map detects JAR changes and rebuilds images only when needed.
# ---------------------------------------------------------------------------

locals {
  # Datasource JDBC URL used by all 4 services that connect to PostgreSQL
  jdbc_url = "jdbc:postgresql://${var.postgres_host}:5432/${var.db_name}"
}

# ---------------------------------------------------------------------------
# market-feed-simulator — stateless WebSocket price feed, no DB or Kafka
# ---------------------------------------------------------------------------

resource "docker_image" "market_feed_simulator" {
  name = "price-alert/market-feed-simulator:${var.image_tag}"
  build {
    context    = var.source_path
    dockerfile = "Dockerfile"
    build_args = {
      MODULE = "market-feed-simulator"
    }
  }
  triggers = {
    jar_hash = filemd5("${var.source_path}/market-feed-simulator/build/libs/market-feed-simulator-0.1.0-SNAPSHOT.jar")
  }
}

resource "docker_container" "market_feed_simulator" {
  name  = "market-feed-simulator"
  image = docker_image.market_feed_simulator.image_id

  ports {
    internal = 8085
    external = 8085
  }

  healthcheck {
    test         = ["CMD-SHELL", "wget -qO- http://localhost:8085/actuator/health || exit 1"]
    interval     = "10s"
    timeout      = "5s"
    retries      = 10
    start_period = "20s"
  }

  networks_advanced {
    name = var.network_id
  }

  restart = "unless-stopped"
}

# ---------------------------------------------------------------------------
# alert-api — REST API, JWT auth, daily reset, Flyway migrations
# ---------------------------------------------------------------------------

resource "docker_image" "alert_api" {
  name = "price-alert/alert-api:${var.image_tag}"
  build {
    context    = var.source_path
    dockerfile = "Dockerfile"
    build_args = {
      MODULE = "alert-api"
    }
  }
  triggers = {
    jar_hash = filemd5("${var.source_path}/alert-api/build/libs/alert-api-0.1.0-SNAPSHOT.jar")
  }
}

resource "docker_container" "alert_api" {
  name  = "alert-api"
  image = docker_image.alert_api.image_id

  env = [
    "SPRING_DATASOURCE_URL=${local.jdbc_url}",
    "SPRING_DATASOURCE_USERNAME=${var.db_user}",
    "SPRING_DATASOURCE_PASSWORD=${var.db_password}",
    "SPRING_KAFKA_BOOTSTRAP_SERVERS=${var.kafka_bootstrap}",
    "SPRING_DATA_REDIS_HOST=${var.redis_host}",
    "SPRING_DATA_REDIS_PORT=6379",
    "JWT_SECRET=${var.jwt_secret}",
  ]

  ports {
    internal = 8080
    external = 8080
  }

  healthcheck {
    test         = ["CMD-SHELL", "wget -qO- http://localhost:8080/actuator/health || exit 1"]
    interval     = "10s"
    timeout      = "5s"
    retries      = 10
    start_period = "30s"
  }

  networks_advanced {
    name = var.network_id
  }

  restart = "unless-stopped"

  # Implicit ordering: Terraform resolves alert_api after module.infrastructure
  # because var.kafka_init_container_id, var.postgres_host, var.redis_host
  # are all outputs from module.infrastructure.
  # No explicit depends_on needed here — the data dependency is sufficient.
}

# ---------------------------------------------------------------------------
# tick-ingestor — WebSocket → Kafka via transactional outbox
# Flyway disabled: alert-api owns all migrations
# ---------------------------------------------------------------------------

resource "docker_image" "tick_ingestor" {
  name = "price-alert/tick-ingestor:${var.image_tag}"
  build {
    context    = var.source_path
    dockerfile = "Dockerfile"
    build_args = {
      MODULE = "tick-ingestor"
    }
  }
  triggers = {
    jar_hash = filemd5("${var.source_path}/tick-ingestor/build/libs/tick-ingestor-0.1.0-SNAPSHOT.jar")
  }
}

resource "docker_container" "tick_ingestor" {
  name  = "tick-ingestor"
  image = docker_image.tick_ingestor.image_id

  env = [
    "INGESTOR_SIMULATOR_URL=ws://market-feed-simulator:8085/v1/feed",
    "SPRING_KAFKA_BOOTSTRAP_SERVERS=${var.kafka_bootstrap}",
    "SPRING_DATASOURCE_URL=${local.jdbc_url}",
    "SPRING_DATASOURCE_USERNAME=${var.db_user}",
    "SPRING_DATASOURCE_PASSWORD=${var.db_password}",
    "SPRING_FLYWAY_ENABLED=false",
  ]

  # No host port — tick-ingestor is an internal consumer service
  # (port 8081 accessible within the Docker network only)

  healthcheck {
    test         = ["CMD-SHELL", "wget -qO- http://localhost:8081/actuator/health || exit 1"]
    interval     = "10s"
    timeout      = "5s"
    retries      = 10
    start_period = "20s"
  }

  networks_advanced {
    name = var.network_id
  }

  restart = "unless-stopped"

  depends_on = [
    docker_container.market_feed_simulator,
    docker_container.alert_api,
  ]
}

# ---------------------------------------------------------------------------
# evaluator — in-memory alert index, evaluates market ticks
# ---------------------------------------------------------------------------

resource "docker_image" "evaluator" {
  name = "price-alert/evaluator:${var.image_tag}"
  build {
    context    = var.source_path
    dockerfile = "Dockerfile"
    build_args = {
      MODULE = "evaluator"
    }
  }
  triggers = {
    jar_hash = filemd5("${var.source_path}/evaluator/build/libs/evaluator-0.1.0-SNAPSHOT.jar")
  }
}

resource "docker_container" "evaluator" {
  name  = "evaluator"
  image = docker_image.evaluator.image_id

  env = [
    "SPRING_DATASOURCE_URL=${local.jdbc_url}",
    "SPRING_DATASOURCE_USERNAME=${var.db_user}",
    "SPRING_DATASOURCE_PASSWORD=${var.db_password}",
    "SPRING_KAFKA_BOOTSTRAP_SERVERS=${var.kafka_bootstrap}",
    "SPRING_FLYWAY_ENABLED=false",
  ]

  # No host port — evaluator is an internal consumer service

  healthcheck {
    test         = ["CMD-SHELL", "wget -qO- http://localhost:8082/actuator/health || exit 1"]
    interval     = "10s"
    timeout      = "5s"
    retries      = 10
    start_period = "20s"
  }

  networks_advanced {
    name = var.network_id
  }

  restart = "unless-stopped"

  depends_on = [docker_container.alert_api]
}

# ---------------------------------------------------------------------------
# notification-persister — consumes alert-triggers, writes notifications
# ---------------------------------------------------------------------------

resource "docker_image" "notification_persister" {
  name = "price-alert/notification-persister:${var.image_tag}"
  build {
    context    = var.source_path
    dockerfile = "Dockerfile"
    build_args = {
      MODULE = "notification-persister"
    }
  }
  triggers = {
    jar_hash = filemd5("${var.source_path}/notification-persister/build/libs/notification-persister-0.1.0-SNAPSHOT.jar")
  }
}

resource "docker_container" "notification_persister" {
  name  = "notification-persister"
  image = docker_image.notification_persister.image_id

  env = [
    "SPRING_DATASOURCE_URL=${local.jdbc_url}",
    "SPRING_DATASOURCE_USERNAME=${var.db_user}",
    "SPRING_DATASOURCE_PASSWORD=${var.db_password}",
    "SPRING_KAFKA_BOOTSTRAP_SERVERS=${var.kafka_bootstrap}",
    "SPRING_FLYWAY_ENABLED=false",
  ]

  # No host port — notification-persister is an internal consumer service

  healthcheck {
    test         = ["CMD-SHELL", "wget -qO- http://localhost:8083/actuator/health || exit 1"]
    interval     = "10s"
    timeout      = "5s"
    retries      = 10
    start_period = "20s"
  }

  networks_advanced {
    name = var.network_id
  }

  restart = "unless-stopped"

  depends_on = [docker_container.alert_api]
}
