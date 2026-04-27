package com.n11.cart.pricing;

import java.math.BigDecimal;

/**
 * One row in the receipt's "discounts" section.
 *
 * @param code   stable identifier (campaign code, coupon code) for the strategy
 * @param label  human-readable line for the cart UI
 * @param kind   CAMPAIGN or COUPON — drives UI grouping and saga reservation
 * @param amount strictly positive — how much was subtracted from subtotal
 */
public record AppliedDiscount(
        String code,
        String label,
        DiscountKind kind,
        BigDecimal amount
) {
    public AppliedDiscount {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Discount amount must be > 0");
        }
    }
}
