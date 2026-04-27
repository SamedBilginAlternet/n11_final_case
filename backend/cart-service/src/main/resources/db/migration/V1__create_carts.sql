CREATE TABLE carts (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE cart_items (
    id           BIGSERIAL PRIMARY KEY,
    cart_id      BIGINT      NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id   BIGINT      NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    image_url    VARCHAR(500),
    quantity     INTEGER     NOT NULL CHECK (quantity > 0),
    unit_price   NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0),
    currency     VARCHAR(3)  NOT NULL,
    added_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (cart_id, product_id)
);

CREATE INDEX ix_cart_items_cart ON cart_items (cart_id);
