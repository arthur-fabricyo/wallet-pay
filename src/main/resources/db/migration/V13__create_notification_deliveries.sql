CREATE TABLE notification_deliveries (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    user_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL,
    delivered_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_notification_deliveries_event_user_channel UNIQUE (event_id, user_id, channel)
);
