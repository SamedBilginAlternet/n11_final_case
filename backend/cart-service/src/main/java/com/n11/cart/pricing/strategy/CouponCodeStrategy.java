package com.n11.cart.pricing.strategy;

import com.n11.cart.domain.Coupon;
import com.n11.cart.domain.CouponType;
import com.n11.cart.pricing.AppliedDiscount;
import com.n11.cart.pricing.DiscountKind;
import com.n11.cart.pricing.DiscountStrategy;
import com.n11.cart.pricing.QuoteContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;

/**
 * User-entered code. The cart already stores the code; the engine resolved it
 * to a {@link Coupon} entity in {@link QuoteContext}. We re-validate the time
 * window + redemption cap on every quote because:
 *  - the cart may have been opened hours ago
 *  - the saga reserves at order-creation, not at quote — so an admin pulling a
 *    coupon mid-flow must instantly stop applying it.
 *
 * Returns empty (silently) instead of throwing so a stale coupon doesn't
 * 500 the cart load — UI just shows total without the discount and the user
 * can DELETE /api/cart/coupon manually.
 */
@Component
public class CouponCodeStrategy implements DiscountStrategy {

    @Override
    public int priority() {
        return 50;
    }

    @Override
    public Optional<AppliedDiscount> evaluate(QuoteContext ctx) {
        Coupon coupon = ctx.coupon();
        if (coupon == null) return Optional.empty();
        if (!coupon.isValidAt(Instant.now())) return Optional.empty();
        if (!meetsMinimum(ctx.subtotal(), coupon.getMinCartTotal())) return Optional.empty();

        BigDecimal amount = switch (coupon.getType()) {
            case FIXED -> coupon.getValue().min(ctx.subtotal());
            case PERCENT -> ctx.subtotal()
                    .multiply(coupon.getValue().movePointLeft(2))
                    .setScale(2, RoundingMode.HALF_UP);
        };
        if (amount.signum() <= 0) return Optional.empty();

        return Optional.of(new AppliedDiscount(
                coupon.getCode(),
                coupon.getLabel(),
                DiscountKind.COUPON,
                amount));
    }

    private boolean meetsMinimum(BigDecimal subtotal, BigDecimal min) {
        return min == null || subtotal.compareTo(min) >= 0;
    }
}
