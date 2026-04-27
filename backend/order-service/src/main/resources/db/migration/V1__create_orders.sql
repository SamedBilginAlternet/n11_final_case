CREATE TABLE orders (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    user_email      VARCHAR(160) NOT NULL,
    status          VARCHAR(30)  NOT NULL,
    total_amount    NUMERIC(12,2) NOT NULL CHECK (total_amount >= 0),
    currency        VARCHAR(3)   NOT NULL,
    failure_reason  VARCHAR(500),
    correlation_id  VARCHAR(64),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_orders_user   ON orders (user_id);
CREATE INDEX ix_orders_status ON orders (status);

CREATE TABLE order_items (
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id   BIGINT NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    quantity     INTEGER NOT NULL CHECK (quantity > 0),
    unit_price   NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0)
);

CREATE INDEX ix_order_items_order ON order_items (order_id);
