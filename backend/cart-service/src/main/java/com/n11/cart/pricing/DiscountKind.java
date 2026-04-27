package com.n11.cart.pricing;

public enum DiscountKind {
    /** Automatically applied campaign (cart-wide percent, BUY_X_PAY_Y, etc). */
    CAMPAIGN,
    /** User-entered coupon code. */
    COUPON
}
