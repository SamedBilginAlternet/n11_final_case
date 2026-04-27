package com.n11.cart.pricing.strategy;

import com.n11.cart.domain.Campaign;
import com.n11.cart.domain.CampaignType;
import com.n11.cart.pricing.AppliedDiscount;
import com.n11.cart.pricing.DiscountKind;
import com.n11.cart.pricing.DiscountStrategy;
import com.n11.cart.pricing.QuoteContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * "Sepette %X indirim" — auto-applied campaign (no coupon code needed).
 *
 * Picks the highest-priority active PERCENT_OFF_CART campaign whose
 * min_cart_total threshold is satisfied. Multiple eligible percent-off
 * campaigns don't stack — that would let admins accidentally double-discount.
 */
@Component
public class PercentOffCartStrategy implements DiscountStrategy {

    @Override
    public int priority() {
        return 30;
    }

    @Override
    public Optional<AppliedDiscount> evaluate(QuoteContext ctx) {
        return ctx.activeCampaigns().stream()
                .filter(c -> c.getType() == CampaignType.PERCENT_OFF_CART)
                .filter(c -> c.getValue() != null && c.getValue().signum() > 0)
                .filter(c -> meetsMinimum(ctx.subtotal(), c.getMinCartTotal()))
                .findFirst()
                .map(c -> {
                    BigDecimal pct = c.getValue().movePointLeft(2);
                    BigDecimal amount = ctx.subtotal()
                            .multiply(pct)
                            .setScale(2, RoundingMode.HALF_UP);
                    return new AppliedDiscount(c.getCode(), c.getLabel(), DiscountKind.CAMPAIGN, amount);
                });
    }

    private boolean meetsMinimum(BigDecimal subtotal, BigDecimal min) {
        return min == null || subtotal.compareTo(min) >= 0;
    }
}
