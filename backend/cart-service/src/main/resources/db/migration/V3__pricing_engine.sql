-- Cart-level coupon code (nullable; user attaches/removes).
ALTER TABLE carts ADD COLUMN coupon_code VARCHAR(40);

-- Discount campaigns automatically applied by the pricing engine.
-- type drives which Strategy bean evaluates the row at quote-time.
CREATE TABLE campaigns (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(60)  NOT NULL UNIQUE,
    label           VARCHAR(160) NOT NULL,
    type            VARCHAR(40)  NOT NULL,
    priority        INTEGER      NOT NULL DEFAULT 100,
    -- Generic numeric value: percent (5 = 5%) or buy-X
    value           NUMERIC(12,2),
    -- BUY_X_PAY_Y campaigns: pay_y = how many you pay for in each group of buy_x
    pay_y           INTEGER,
    -- Activation thresholds
    min_cart_total  NUMERIC(12,2),
    valid_from      TIMESTAMPTZ,
    valid_until     TIMESTAMPTZ,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Coupons are user-entered codes (KUPON100, KUPON10).
CREATE TABLE coupons (
    id               BIGSERIAL PRIMARY KEY,
    code             VARCHAR(40)  NOT NULL UNIQUE,
    label            VARCHAR(160) NOT NULL,
    -- FIXED = absolute TL off, PERCENT = % off subtotal
    type             VARCHAR(20)  NOT NULL,
    value            NUMERIC(12,2) NOT NULL CHECK (value > 0),
    min_cart_total   NUMERIC(12,2),
    max_redemptions  INTEGER,
    redemptions      INTEGER      NOT NULL DEFAULT 0,
    valid_from       TIMESTAMPTZ,
    valid_until      TIMESTAMPTZ,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    -- Race guard: redemptions never exceeds the cap
    CONSTRAINT chk_coupon_redemptions CHECK (max_redemptions IS NULL OR redemptions <= max_redemptions)
);

CREATE INDEX ix_coupons_active_code ON coupons (active, code);
