package com.n11.cart.pricing.strategy;

import com.n11.cart.domain.Campaign;
import com.n11.cart.domain.CampaignType;
import com.n11.cart.pricing.AppliedDiscount;
import com.n11.cart.pricing.DiscountKind;
import com.n11.cart.pricing.QuoteContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PercentOffCartStrategyTest {

    private final PercentOffCartStrategy strategy = new PercentOffCartStrategy();

    @Test
    void returnsCorrectPercentageRoundedHalfUp() {
        Campaign c = Campaign.builder()
                .code("PCT5")
                .label("Sepette %5 indirim")
                .type(CampaignType.PERCENT_OFF_CART)
                .priority(30)
                .value(new BigDecimal("5"))
                .active(true)
                .build();

        Optional<AppliedDiscount> result = strategy.evaluate(ctx(new BigDecimal("123.45"), c));

        assertThat(result).isPresent();
        assertThat(result.get().kind()).isEqualTo(DiscountKind.CAMPAIGN);
        assertThat(result.get().amount()).isEqualByComparingTo("6.17"); // 123.45 * 0.05 = 6.1725 → 6.17 HALF_UP
        assertThat(result.get().code()).isEqualTo("PCT5");
    }

    @Test
    void skipsCampaignsBelowMinimumCartTotal() {
        Campaign c = Campaign.builder()
                .code("BIG10")
                .label("500 TL üstü %10")
                .type(CampaignType.PERCENT_OFF_CART)
                .value(new BigDecimal("10"))
                .minCartTotal(new BigDecimal("500"))
                .active(true)
                .build();

        Optional<AppliedDiscount> result = strategy.evaluate(ctx(new BigDecimal("499.99"), c));

        assertThat(result).isEmpty();
    }

    @Test
    void skipsNonPercentCampaignsInTheList() {
        Campaign other = Campaign.builder()
                .code("BUY3")
                .type(CampaignType.BUY_X_PAY_Y)
                .value(new BigDecimal("4"))
                .payY(3)
                .active(true)
                .build();

        Optional<AppliedDiscount> result = strategy.evaluate(ctx(new BigDecimal("100"), other));

        assertThat(result).isEmpty();
    }

    @Test
    void emptyCampaignListProducesNoDiscount() {
        Optional<AppliedDiscount> result = strategy.evaluate(ctx(new BigDecimal("100")));
        assertThat(result).isEmpty();
    }

    private QuoteContext ctx(BigDecimal subtotal, Campaign... campaigns) {
        return new QuoteContext(1L, List.of(), subtotal, null, null, List.of(campaigns));
    }
}
