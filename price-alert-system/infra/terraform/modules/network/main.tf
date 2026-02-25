resource "docker_network" "price_alert" {
  name   = "price-alert-network"
  driver = "bridge"

  ipam_config {
    subnet  = "172.20.0.0/16"
    gateway = "172.20.0.1"
  }
}
