rootProject.name = "price-alert-system"

buildCache {
    local {
        isEnabled = true
    }
}

include("common")
include("market-feed-simulator")
include("tick-ingestor")
include("alert-api")
include("evaluator")
include("notification-persister")
