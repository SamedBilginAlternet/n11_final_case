package com.n11.cart.pricing;

import com.n11.cart.domain.Campaign;
import com.n11.cart.domain.CartItem;
import com.n11.cart.domain.Coupon;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read-only input passed to every {@link DiscountStrategy#evaluate}.
 * Strategies must not mutate it; they return zero or one {@link AppliedDiscount}.
 *
 * The engine resolves the coupon (if cart has a code) ahead of time and
 * filters campaigns to active+within-window ones, so each strategy can stay
 * focused on the math.
 */
public record QuoteContext(
        Long userId,
        List<CartItem> items,
        BigDecimal subtotal,
        String couponCode,
        Coupon coupon,
        List<Campaign> activeCampaigns
) {}
