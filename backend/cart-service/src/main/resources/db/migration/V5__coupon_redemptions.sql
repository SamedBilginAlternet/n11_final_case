-- Per-order coupon redemption ledger. (coupon_id, order_id) is unique so the
-- saga reservation is idempotent under at-least-once delivery: a duplicate
-- OrderCreated event hits the constraint and is treated as a no-op.
--
-- The compensation step deletes the row and decrements coupons.redemptions —
-- a duplicate OrderCancelled finds nothing to delete and silently succeeds.
CREATE TABLE coupon_redemptions (
    id          BIGSERIAL PRIMARY KEY,
    coupon_id   BIGINT      NOT NULL REFERENCES coupons(id) ON DELETE RESTRICT,
    order_id    BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    redeemed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (coupon_id, order_id)
);

CREATE INDEX ix_coupon_redemptions_order ON coupon_redemptions (order_id);
