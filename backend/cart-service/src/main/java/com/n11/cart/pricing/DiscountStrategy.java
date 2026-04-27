package com.n11.cart.pricing;

import java.util.Optional;

/**
 * Strategy + Chain hybrid: every Spring bean implementing this interface is
 * picked up by {@link DiscountEngine}, sorted by {@link #priority()} ascending,
 * and asked to evaluate the cart in turn. Strategies are pure functions of
 * {@link QuoteContext} → optional {@link AppliedDiscount}; they must not
 * mutate state and must never return a zero-amount discount (the
 * {@code AppliedDiscount} compact constructor enforces this).
 *
 * <p>Adding a new campaign type is a one-class change: implement this
 * interface, mark {@code @Component}, the engine picks it up automatically.</p>
 */
public interface DiscountStrategy {

    /**
     * Lower runs first. Convention:
     *   10..29 = line-level / per-product (BUY_X_PAY_Y)
     *   30..49 = cart-wide automatic (PERCENT_OFF_CART)
     *   50..69 = user-entered coupons
     */
    int priority();

    Optional<AppliedDiscount> evaluate(QuoteContext context);
}
