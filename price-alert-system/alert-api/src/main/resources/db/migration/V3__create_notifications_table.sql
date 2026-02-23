CREATE TABLE notifications (
    id                  VARCHAR(26)     PRIMARY KEY,
    alert_trigger_id    VARCHAR(26)     NOT NULL,
    alert_id            VARCHAR(26)     NOT NULL,
    user_id             VARCHAR(26)     NOT NULL,
    symbol              VARCHAR(10)     NOT NULL,
    threshold_price     DECIMAL(12,6)   NOT NULL,
    trigger_price       DECIMAL(12,6)   NOT NULL,
    direction           VARCHAR(5)      NOT NULL,
    note                VARCHAR(255),
    idempotency_key     VARCHAR(64)     NOT NULL UNIQUE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    read                BOOLEAN         NOT NULL DEFAULT false
);

CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_unread ON notifications (user_id, read) WHERE read = false;
