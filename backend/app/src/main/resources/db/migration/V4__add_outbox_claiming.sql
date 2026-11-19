
ALTER TABLE outbox_events
ADD COLUMN claimed_at TIMESTAMP(6) WITH TIME ZONE;

CREATE INDEX idx_outbox_events_claimed
ON outbox_events (claimed_at)
WHERE published_at IS NULL;
