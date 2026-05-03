CREATE TABLE outbox_events (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(100) NOT NULL,   -- e.g. "Order", "Inventory"
    aggregate_id    UUID        NOT NULL,    -- e.g. the order's UUID
    event_type      VARCHAR(100) NOT NULL,   -- e.g. "ORDER_PLACED"
    payload         JSONB       NOT NULL,    -- full event body
    published       BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

-- The outbox poller's ONLY query: unpublished events, oldest first
-- Partial index means this index is tiny — only unpublished rows are indexed
-- Once an event is published, it falls out of this index automatically
CREATE INDEX idx_outbox_unpublished
    ON outbox_events(created_at ASC)
    WHERE published = FALSE;

-- Cleanup job index: delete events older than 7 days that are published
CREATE INDEX idx_outbox_cleanup
    ON outbox_events(published_at)
    WHERE published = TRUE;