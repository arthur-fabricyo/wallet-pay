-- available_at: when the row may be claimed again (retry backoff and publishing lease).
-- parked_at: set when the row is given up on (attempt ceiling or unreadable row); parked rows are not published.
ALTER TABLE outbox_events ADD COLUMN available_at TIMESTAMPTZ;
UPDATE outbox_events SET available_at = created_at;
ALTER TABLE outbox_events ALTER COLUMN available_at SET NOT NULL;

ALTER TABLE outbox_events ADD COLUMN parked_at TIMESTAMPTZ;

DROP INDEX idx_outbox_events_unpublished;
CREATE INDEX idx_outbox_events_pending ON outbox_events (available_at) WHERE published_at IS NULL AND parked_at IS NULL;
