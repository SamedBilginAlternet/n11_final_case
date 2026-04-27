package com.n11.cart.pricing;

import java.math.BigDecimal;
import java.util.List;

/**
 * Result of running the discount engine over a cart.
 *
 * <pre>
 *   subtotal  = Σ unitPrice * qty                  (before any discount)
 *   discounts = strategies that produced a non-zero amount
 *   total     = max(subtotal - Σ discounts.amount, 0)
 * </pre>
 *
 * The engine never mutates the cart — it returns this snapshot, the caller
 * decides whether to persist anything.
 */
public record Quote(
        BigDecimal subtotal,
        List<AppliedDiscount> discounts,
        BigDecimal totalDiscount,
        BigDecimal total
) {}
