-- Sample campaigns + coupons so the engine has something to chew on out of the box.
-- Idempotent: ON CONFLICT DO NOTHING — re-running migrations on a partially seeded
-- DB is safe.

INSERT INTO campaigns (code, label, type, priority, value, pay_y, min_cart_total, active)
VALUES
    ('PCT5_OVER_500',  'Sepette %5 indirim (500 TL ve üzeri)', 'PERCENT_OFF_CART', 30,    5, NULL, 500.00, TRUE),
    ('PCT10_OVER_2K',  'Sepette %10 indirim (2.000 TL ve üzeri)', 'PERCENT_OFF_CART', 31, 10, NULL, 2000.00, TRUE),
    ('BUY4_PAY3',      '4 al 3 öde — sepetteki en ucuz 1 ürün hediye', 'BUY_X_PAY_Y', 20,  4,    3, NULL, TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO coupons (code, label, type, value, min_cart_total, max_redemptions, redemptions, active)
VALUES
    ('KUPON100', '100 TL kupon', 'FIXED',  100.00,  300.00, 1000, 0, TRUE),
    ('KUPON10',  '%10 kupon',    'PERCENT',  10.00,    NULL,  500, 0, TRUE),
    ('YENI50',   'Yeni üye 50 TL', 'FIXED',  50.00,    NULL,    1, 0, TRUE)
ON CONFLICT (code) DO NOTHING;
