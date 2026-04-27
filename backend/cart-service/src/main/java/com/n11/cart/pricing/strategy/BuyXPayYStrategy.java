package com.n11.cart.pricing.strategy;

import com.n11.cart.domain.Campaign;
import com.n11.cart.domain.CampaignType;
import com.n11.cart.domain.CartItem;
import com.n11.cart.pricing.AppliedDiscount;
import com.n11.cart.pricing.DiscountKind;
import com.n11.cart.pricing.DiscountStrategy;
import com.n11.cart.pricing.QuoteContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * "X al Y öde" — buy {@code value} units, pay for {@code pay_y}, the cheapest
 * (X − Y) units in each group are free.
 *
 * Algorithm: flatten the cart into a per-unit list (one entry per unit, not
 * per CartItem), sort by unitPrice ascending, then for every consecutive
 * group of X units mark the cheapest (X − Y) as free and sum their prices.
 * This is the standard "favor the customer" interpretation used by major
 * marketplaces — gives the buyer the best result the rule allows.
 *
 * No category filter (intentional simplification); any 4+ items in the cart
 * trigger the campaign. The discount accumulates across multiple full groups
 * (8 items → two groups of 4 → two cheapest go free).
 */
@Component
public class BuyXPayYStrategy implements DiscountStrategy {

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public Optional<AppliedDiscount> evaluate(QuoteContext ctx) {
        return ctx.activeCampaigns().stream()
                .filter(c -> c.getType() == CampaignType.BUY_X_PAY_Y)
                .filter(this::isWellFormed)
                .findFirst()
                .flatMap(c -> apply(c, ctx.items()));
    }

    private boolean isWellFormed(Campaign c) {
        if (c.getValue() == null || c.getPayY() == null) return false;
        int buyX = c.getValue().intValueExact();
        int payY = c.getPayY();
        return buyX > 0 && payY > 0 && payY < buyX;
    }

    private Optional<AppliedDiscount> apply(Campaign campaign, List<CartItem> items) {
        int buyX = campaign.getValue().intValueExact();
        int payY = campaign.getPayY();

        List<BigDecimal> units = new ArrayList<>();
        for (CartItem item : items) {
            for (int i = 0; i < item.getQuantity(); i++) {
                units.add(item.getUnitPrice());
            }
        }
        if (units.size() < buyX) return Optional.empty();

        units.sort(Comparator.naturalOrder());

        int fullGroups = units.size() / buyX;
        int freeUnitsPerGroup = buyX - payY;
        int freeUnits = fullGroups * freeUnitsPerGroup;

        BigDecimal totalFree = BigDecimal.ZERO;
        for (int i = 0; i < freeUnits; i++) {
            totalFree = totalFree.add(units.get(i));
        }
        if (totalFree.signum() <= 0) return Optional.empty();

        return Optional.of(new AppliedDiscount(
                campaign.getCode(),
                campaign.getLabel(),
                DiscountKind.CAMPAIGN,
                totalFree));
    }
}
