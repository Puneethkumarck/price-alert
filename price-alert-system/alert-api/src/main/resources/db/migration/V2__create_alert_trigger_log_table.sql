CREATE TABLE alert_trigger_log (
    id                  VARCHAR(26)     PRIMARY KEY,
    alert_id            VARCHAR(26)     NOT NULL REFERENCES alerts(id),
    user_id             VARCHAR(26)     NOT NULL,
    symbol              VARCHAR(10)     NOT NULL,
    threshold_price     DECIMAL(12,6)   NOT NULL,
    trigger_price       DECIMAL(12,6)   NOT NULL,
    tick_timestamp      TIMESTAMPTZ     NOT NULL,
    triggered_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    trading_date        DATE            NOT NULL
);

CREATE INDEX idx_trigger_log_alert ON alert_trigger_log (alert_id);
CREATE INDEX idx_trigger_log_user ON alert_trigger_log (user_id, triggered_at DESC);
CREATE UNIQUE INDEX idx_trigger_log_dedup ON alert_trigger_log (alert_id, trading_date);
