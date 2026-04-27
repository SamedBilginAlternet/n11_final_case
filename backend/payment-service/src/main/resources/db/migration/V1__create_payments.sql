CREATE TABLE payments (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    status          VARCHAR(20) NOT NULL,
    amount          NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    currency        VARCHAR(3) NOT NULL,
    provider_ref    VARCHAR(80),
    failure_reason  VARCHAR(500),
    attempt         INTEGER NOT NULL DEFAULT 1,
    correlation_id  VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_payments_order  ON payments (order_id);
CREATE INDEX ix_payments_status ON payments (status);
