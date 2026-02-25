# =============================================================================
# environments/local.tfvars
# Non-sensitive configuration for local Docker development.
#
# Usage:
#   terraform apply -var-file=environments/local.tfvars
#
# Sensitive values (jwt_secret, db_password, grafana_admin_password) must be
# provided via secrets.auto.tfvars (git-ignored) or TF_VAR_* environment vars.
# =============================================================================

project_source_path = "/Users/pchikkakalya-kempanna/Documents/AI/design_price_alert/price-alert-system"

kafka_image_tag    = "3.9.0"
postgres_image_tag = "17"
redis_image_tag    = "7-alpine"
app_image_tag      = "local"

db_user = "alerts"
db_name = "price_alerts"
