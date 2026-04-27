-- Snapshot the coupon code on the order so the cancellation saga can release
-- the redemption with the same key, even if the cart is gone or has been
-- mutated since checkout.
ALTER TABLE orders ADD COLUMN coupon_code VARCHAR(40);
