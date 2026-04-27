-- Per-user product wishlist. We store productId only; the catalog data
-- (name, price, image) is fetched live from product-service so prices stay
-- accurate — a wishlist that shows yesterday's price is worse than no
-- wishlist.
--
-- UNIQUE (user_id, product_id) makes "toggle" trivially idempotent:
--   * already there → DELETE
--   * not there     → INSERT (upsert-on-conflict-do-nothing also works)

CREATE TABLE wishlist_items (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    product_id  BIGINT      NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_wishlist_user_product UNIQUE (user_id, product_id)
);

CREATE INDEX idx_wishlist_user ON wishlist_items(user_id, created_at DESC);
