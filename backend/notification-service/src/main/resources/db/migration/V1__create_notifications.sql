-- Audit trail for every dispatched notification.  Lets us debug "why didn't I
-- get the kargo mail" complaints without asking RabbitMQ to replay events,
-- and (because order_id + kind is unique) makes the consumer idempotent: a
-- redelivered RabbitMQ message hits the unique constraint and is silently
-- skipped.

CREATE TABLE notifications (
    id            BIGSERIAL PRIMARY KEY,
    order_id      BIGINT       NOT NULL,
    user_id       BIGINT       NOT NULL,
    recipient     VARCHAR(160) NOT NULL,
    kind          VARCHAR(40)  NOT NULL,
    subject       VARCHAR(255) NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    error         TEXT,
    correlation_id VARCHAR(64),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_notifications_order_kind ON notifications(order_id, kind);
CREATE INDEX ix_notifications_recipient ON notifications(recipient);
CREATE INDEX ix_notifications_created_at ON notifications(created_at DESC);
