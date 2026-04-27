-- User shipping address book. Multiple addresses per user; one (or zero)
-- can be marked default. Order-service snapshots address fields onto each
-- order at checkout time, so editing/deleting an address here never mutates
-- past orders' shipping records.

CREATE TABLE addresses (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title           VARCHAR(60)  NOT NULL,        -- "Ev", "Ofis", etc.
    recipient_name  VARCHAR(120) NOT NULL,
    phone           VARCHAR(32)  NOT NULL,
    line1           VARCHAR(255) NOT NULL,
    city            VARCHAR(80)  NOT NULL,
    district        VARCHAR(80),
    postal_code     VARCHAR(16),
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_addresses_user ON addresses(user_id);

-- Only one default per user. Partial unique index — Postgres lets is_default
-- be FALSE on multiple rows but only one row can carry TRUE.
CREATE UNIQUE INDEX uq_addresses_one_default_per_user
    ON addresses(user_id) WHERE is_default;
