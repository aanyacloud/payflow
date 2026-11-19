
CREATE TABLE outbox_events (
    event_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP(6) WITH TIME ZONE,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,

    CONSTRAINT outbox_events_pkey PRIMARY KEY (event_id),
    CONSTRAINT chk_outbox_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_outbox_events_pending
ON outbox_events (created_at)
WHERE published_at IS NULL;

CREATE INDEX idx_outbox_events_aggregate
ON outbox_events (aggregate_type, aggregate_id);
