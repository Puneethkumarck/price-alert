locals {
  cfg = var.monitoring_config_path
}

# ---------------------------------------------------------------------------
# Prometheus — scrapes /actuator/prometheus from all 5 app services every 15s
# ---------------------------------------------------------------------------

resource "docker_image" "prometheus" {
  name = "prom/prometheus:latest"
}

resource "docker_container" "prometheus" {
  name  = "prometheus"
  image = docker_image.prometheus.image_id

  ports {
    internal = 9090
    external = 9090
  }

  volumes {
    host_path      = "${local.cfg}/prometheus.yml"
    container_path = "/etc/prometheus/prometheus.yml"
    read_only      = true
  }

  networks_advanced {
    name = var.network_id
  }

  restart = "unless-stopped"
}

# ---------------------------------------------------------------------------
# Loki — log aggregation backend
# ---------------------------------------------------------------------------

resource "docker_image" "loki" {
  name = "grafana/loki:latest"
}

resource "docker_container" "loki" {
  name    = "loki"
  image   = docker_image.loki.image_id
  command = ["-config.file=/etc/loki/local-config.yaml"]

  ports {
    internal = 3100
    external = 3100
  }

  volumes {
    host_path      = "${local.cfg}/loki.yml"
    container_path = "/etc/loki/local-config.yaml"
    read_only      = true
  }

  networks_advanced {
    name = var.network_id
  }

  restart = "unless-stopped"
}

# ---------------------------------------------------------------------------
# Promtail — ships Docker container logs to Loki
# Requires read-only access to the Docker socket for log discovery.
# ---------------------------------------------------------------------------

resource "docker_image" "promtail" {
  name = "grafana/promtail:latest"
}

resource "docker_container" "promtail" {
  name    = "promtail"
  image   = docker_image.promtail.image_id
  command = ["-config.file=/etc/promtail/config.yml"]

  volumes {
    host_path      = "/var/run/docker.sock"
    container_path = "/var/run/docker.sock"
    read_only      = true
  }

  volumes {
    host_path      = "${local.cfg}/promtail.yml"
    container_path = "/etc/promtail/config.yml"
    read_only      = true
  }

  networks_advanced {
    name = var.network_id
  }

  restart = "unless-stopped"

  depends_on = [docker_container.loki]
}

# ---------------------------------------------------------------------------
# Tempo — distributed tracing via OpenTelemetry OTLP
# ---------------------------------------------------------------------------

resource "docker_image" "tempo" {
  name = "grafana/tempo:latest"
}

resource "docker_container" "tempo" {
  name    = "tempo"
  image   = docker_image.tempo.image_id
  command = ["-config.file=/etc/tempo/config.yaml"]

  ports {
    internal = 3200
    external = 3200
  }

  ports {
    internal = 4318
    external = 4318
  }

  volumes {
    host_path      = "${local.cfg}/tempo.yml"
    container_path = "/etc/tempo/config.yaml"
    read_only      = true
  }

  networks_advanced {
    name = var.network_id
  }

  restart = "unless-stopped"
}

# ---------------------------------------------------------------------------
# Grafana — dashboards for business metrics, JVM overview, and service logs
# ---------------------------------------------------------------------------

resource "docker_image" "grafana" {
  name = "grafana/grafana:latest"
}

resource "docker_container" "grafana" {
  name  = "grafana"
  image = docker_image.grafana.image_id

  env = [
    "GF_SECURITY_ADMIN_USER=admin",
    "GF_SECURITY_ADMIN_PASSWORD=${var.grafana_admin_password}",
    "GF_USERS_ALLOW_SIGN_UP=false",
  ]

  ports {
    internal = 3000
    external = 3000
  }

  volumes {
    host_path      = "${local.cfg}/grafana/provisioning"
    container_path = "/etc/grafana/provisioning"
    read_only      = true
  }

  volumes {
    host_path      = "${local.cfg}/grafana/dashboards"
    container_path = "/var/lib/grafana/dashboards"
    read_only      = true
  }

  networks_advanced {
    name = var.network_id
  }

  restart = "unless-stopped"

  depends_on = [
    docker_container.prometheus,
    docker_container.loki,
    docker_container.tempo,
  ]
}
