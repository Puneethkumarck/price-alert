provider "docker" {
  # Defaults to unix:///var/run/docker.sock
  # Override with DOCKER_HOST environment variable if needed (e.g. for OrbStack or remote Docker).
  host = "unix:///var/run/docker.sock"
}
