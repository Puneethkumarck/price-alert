-- namastack-outbox schema for PostgreSQL (per-service table prefix isolation)
-- Source: https://github.com/namastack/namastack-outbox
-- Each service gets its own outbox tables to prevent cross-service handler conflicts.

-- ==========================================
-- alert-api outbox (prefix: alertapi_)
-- ==========================================
CREATE TABLE IF NOT EXISTS alertapi_outbox_record
(
    id             VARCHAR(255)             NOT NULL PRIMARY KEY,
    status         VARCHAR(20)              NOT NULL,
    record_key     VARCHAR(255)             NOT NULL,
    record_type    VARCHAR(255)             NOT NULL,
    payload        TEXT                     NOT NULL,
    context        TEXT,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at   TIMESTAMP WITH TIME ZONE,
    failure_count  INT                      NOT NULL,
    failure_reason VARCHAR(1000),
    next_retry_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    partition_no   INTEGER                  NOT NULL,
    handler_id     VARCHAR(1000)            NOT NULL
);

CREATE TABLE IF NOT EXISTS alertapi_outbox_instance
(
    instance_id    VARCHAR(255) PRIMARY KEY,
    hostname       VARCHAR(255)             NOT NULL,
    port           INTEGER                  NOT NULL,
    status         VARCHAR(50)              NOT NULL,
    started_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    last_heartbeat TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS alertapi_outbox_partition
(
    partition_number INTEGER PRIMARY KEY,
    instance_id      VARCHAR(255),
    version          BIGINT                   NOT NULL DEFAULT 0,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_alertapi_outbox_record_key_created ON alertapi_outbox_record (record_key, created_at);
CREATE INDEX IF NOT EXISTS idx_alertapi_outbox_record_part_status ON alertapi_outbox_record (partition_no, status, next_retry_at);
CREATE INDEX IF NOT EXISTS idx_alertapi_outbox_record_status ON alertapi_outbox_record (status);
CREATE INDEX IF NOT EXISTS idx_alertapi_outbox_record_key_comp ON alertapi_outbox_record (record_key, completed_at, created_at);
CREATE INDEX IF NOT EXISTS idx_alertapi_outbox_inst_status ON alertapi_outbox_instance (status, last_heartbeat);
CREATE INDEX IF NOT EXISTS idx_alertapi_outbox_part_inst ON alertapi_outbox_partition (instance_id);

-- ==========================================
-- evaluator outbox (prefix: evaluator_)
-- ==========================================
CREATE TABLE IF NOT EXISTS evaluator_outbox_record
(
    id             VARCHAR(255)             NOT NULL PRIMARY KEY,
    status         VARCHAR(20)              NOT NULL,
    record_key     VARCHAR(255)             NOT NULL,
    record_type    VARCHAR(255)             NOT NULL,
    payload        TEXT                     NOT NULL,
    context        TEXT,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at   TIMESTAMP WITH TIME ZONE,
    failure_count  INT                      NOT NULL,
    failure_reason VARCHAR(1000),
    next_retry_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    partition_no   INTEGER                  NOT NULL,
    handler_id     VARCHAR(1000)            NOT NULL
);

CREATE TABLE IF NOT EXISTS evaluator_outbox_instance
(
    instance_id    VARCHAR(255) PRIMARY KEY,
    hostname       VARCHAR(255)             NOT NULL,
    port           INTEGER                  NOT NULL,
    status         VARCHAR(50)              NOT NULL,
    started_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    last_heartbeat TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS evaluator_outbox_partition
(
    partition_number INTEGER PRIMARY KEY,
    instance_id      VARCHAR(255),
    version          BIGINT                   NOT NULL DEFAULT 0,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_evaluator_outbox_record_key_created ON evaluator_outbox_record (record_key, created_at);
CREATE INDEX IF NOT EXISTS idx_evaluator_outbox_record_part_status ON evaluator_outbox_record (partition_no, status, next_retry_at);
CREATE INDEX IF NOT EXISTS idx_evaluator_outbox_record_status ON evaluator_outbox_record (status);
CREATE INDEX IF NOT EXISTS idx_evaluator_outbox_record_key_comp ON evaluator_outbox_record (record_key, completed_at, created_at);
CREATE INDEX IF NOT EXISTS idx_evaluator_outbox_inst_status ON evaluator_outbox_instance (status, last_heartbeat);
CREATE INDEX IF NOT EXISTS idx_evaluator_outbox_part_inst ON evaluator_outbox_partition (instance_id);

-- ==========================================
-- tick-ingestor outbox (prefix: ingestor_)
-- ==========================================
CREATE TABLE IF NOT EXISTS ingestor_outbox_record
(
    id             VARCHAR(255)             NOT NULL PRIMARY KEY,
    status         VARCHAR(20)              NOT NULL,
    record_key     VARCHAR(255)             NOT NULL,
    record_type    VARCHAR(255)             NOT NULL,
    payload        TEXT                     NOT NULL,
    context        TEXT,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at   TIMESTAMP WITH TIME ZONE,
    failure_count  INT                      NOT NULL,
    failure_reason VARCHAR(1000),
    next_retry_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    partition_no   INTEGER                  NOT NULL,
    handler_id     VARCHAR(1000)            NOT NULL
);

CREATE TABLE IF NOT EXISTS ingestor_outbox_instance
(
    instance_id    VARCHAR(255) PRIMARY KEY,
    hostname       VARCHAR(255)             NOT NULL,
    port           INTEGER                  NOT NULL,
    status         VARCHAR(50)              NOT NULL,
    started_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    last_heartbeat TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS ingestor_outbox_partition
(
    partition_number INTEGER PRIMARY KEY,
    instance_id      VARCHAR(255),
    version          BIGINT                   NOT NULL DEFAULT 0,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ingestor_outbox_record_key_created ON ingestor_outbox_record (record_key, created_at);
CREATE INDEX IF NOT EXISTS idx_ingestor_outbox_record_part_status ON ingestor_outbox_record (partition_no, status, next_retry_at);
CREATE INDEX IF NOT EXISTS idx_ingestor_outbox_record_status ON ingestor_outbox_record (status);
CREATE INDEX IF NOT EXISTS idx_ingestor_outbox_record_key_comp ON ingestor_outbox_record (record_key, completed_at, created_at);
CREATE INDEX IF NOT EXISTS idx_ingestor_outbox_inst_status ON ingestor_outbox_instance (status, last_heartbeat);
CREATE INDEX IF NOT EXISTS idx_ingestor_outbox_part_inst ON ingestor_outbox_partition (instance_id);
