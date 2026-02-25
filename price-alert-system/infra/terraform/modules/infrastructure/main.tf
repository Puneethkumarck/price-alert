# ---------------------------------------------------------------------------
# Kafka (KRaft mode — no Zookeeper)
# ---------------------------------------------------------------------------

resource "docker_image" "kafka" {
  name = "apache/kafka:${var.kafka_image_tag}"
}

resource "docker_container" "kafka" {
  name  = "kafka"
  image = docker_image.kafka.image_id

  env = [
    "KAFKA_NODE_ID=1",
    "KAFKA_PROCESS_ROLES=broker,controller",
    "KAFKA_LISTENERS=INTERNAL://:19092,EXTERNAL://:9092,CONTROLLER://:9093",
    "KAFKA_ADVERTISED_LISTENERS=INTERNAL://kafka:19092,EXTERNAL://localhost:9092",
    "KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093",
    "KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER",
    "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT",
    "KAFKA_INTER_BROKER_LISTENER_NAME=INTERNAL",
    "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1",
    "KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1",
    "KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1",
    "KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS=0",
    "KAFKA_LOG_RETENTION_HOURS=24",
    "KAFKA_AUTO_CREATE_TOPICS_ENABLE=false",
  ]

  ports {
    internal = 9092
    external = 9092
  }

  healthcheck {
    test         = ["CMD-SHELL", "/opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 > /dev/null 2>&1"]
    interval     = "10s"
    timeout      = "10s"
    retries      = 10
    start_period = "30s"
  }

  networks_advanced {
    name = var.network_id
  }

  restart = "unless-stopped"
}

# ---------------------------------------------------------------------------
# Kafka init — one-shot container that creates the 3 required topics.
# attach=true causes Terraform to WAIT for this container to exit before
# creating any resources that depend on it (the 5 Spring Boot services).
# must_run=false tells Terraform not to restart it after it exits cleanly.
# ---------------------------------------------------------------------------

resource "docker_container" "kafka_init" {
  name       = "kafka-init"
  image      = docker_image.kafka.image_id
  attach     = true
  must_run   = false
  restart    = "no"
  entrypoint = ["/bin/sh", "-c"]

  command = [<<-EOT
    echo "[kafka-init] Waiting for Kafka broker at kafka:19092..."
    until /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server kafka:19092 > /dev/null 2>&1; do
      echo "[kafka-init] Not ready, retrying in 5s..."
      sleep 5
    done
    echo "[kafka-init] Kafka is ready. Creating topics..."

    /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:19092 \
      --create --if-not-exists \
      --topic market-ticks \
      --partitions 16 \
      --replication-factor 1 \
      --config retention.ms=14400000

    /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:19092 \
      --create --if-not-exists \
      --topic alert-changes \
      --partitions 8 \
      --replication-factor 1 \
      --config retention.ms=86400000

    /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:19092 \
      --create --if-not-exists \
      --topic alert-triggers \
      --partitions 8 \
      --replication-factor 1 \
      --config retention.ms=604800000

    echo "[kafka-init] Topics created:"
    /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:19092 --list
  EOT
  ]

  networks_advanced {
    name = var.network_id
  }

  depends_on = [docker_container.kafka]
}

# ---------------------------------------------------------------------------
# PostgreSQL 17
# ---------------------------------------------------------------------------

resource "docker_volume" "pgdata" {
  name = "price-alert-pgdata"

  lifecycle {
    # Prevent accidental data loss on terraform destroy.
    # To intentionally destroy: terraform destroy -target=module.infrastructure.docker_volume.pgdata
    prevent_destroy = true
  }
}

resource "docker_image" "postgres" {
  name = "postgres:${var.postgres_image_tag}"
}

resource "docker_container" "postgres" {
  name  = "postgres"
  image = docker_image.postgres.image_id

  env = [
    "POSTGRES_DB=${var.db_name}",
    "POSTGRES_USER=${var.db_user}",
    "POSTGRES_PASSWORD=${var.db_password}",
  ]

  ports {
    internal = 5432
    external = 5432
  }

  volumes {
    volume_name    = docker_volume.pgdata.name
    container_path = "/var/lib/postgresql/data"
  }

  healthcheck {
    test         = ["CMD-SHELL", "pg_isready -U ${var.db_user} -d ${var.db_name}"]
    interval     = "5s"
    timeout      = "5s"
    retries      = 10
    start_period = "10s"
  }

  networks_advanced {
    name = var.network_id
  }

  restart = "unless-stopped"
}

# ---------------------------------------------------------------------------
# Redis 7
# ---------------------------------------------------------------------------

resource "docker_image" "redis" {
  name = "redis:${var.redis_image_tag}"
}

resource "docker_container" "redis" {
  name  = "redis"
  image = docker_image.redis.image_id

  ports {
    internal = 6379
    external = 6379
  }

  healthcheck {
    test         = ["CMD", "redis-cli", "ping"]
    interval     = "5s"
    timeout      = "5s"
    retries      = 10
    start_period = "5s"
  }

  networks_advanced {
    name = var.network_id
  }

  restart = "unless-stopped"
}
