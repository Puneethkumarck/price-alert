CREATE TABLE alerts (
    id                  VARCHAR(26)     PRIMARY KEY,
    user_id             VARCHAR(26)     NOT NULL,
    symbol              VARCHAR(10)     NOT NULL,
    threshold_price     DECIMAL(12,6)   NOT NULL,
    direction           VARCHAR(5)      NOT NULL,
    status              VARCHAR(15)     NOT NULL DEFAULT 'ACTIVE',
    note                VARCHAR(255),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    last_triggered_at   TIMESTAMPTZ,
    last_trigger_price  DECIMAL(12,6)
);

CREATE INDEX idx_alerts_user_status ON alerts (user_id, status);
CREATE INDEX idx_alerts_symbol_status ON alerts (symbol, status) WHERE status = 'ACTIVE';
CREATE INDEX idx_alerts_triggered_today ON alerts (status) WHERE status = 'TRIGGERED_TODAY';
