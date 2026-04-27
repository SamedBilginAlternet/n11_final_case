package com.n11.cart.pricing.strategy;

import com.n11.cart.domain.Campaign;
import com.n11.cart.domain.CampaignType;
import com.n11.cart.domain.CartItem;
import com.n11.cart.pricing.AppliedDiscount;
import com.n11.cart.pricing.QuoteContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BuyXPayYStrategyTest {

    private final BuyXPayYStrategy strategy = new BuyXPayYStrategy();

    @Test
    void fourItemsThreePriced_cheapestGoesFree() {
        // 4 items: 50, 60, 70, 80 — cheapest (50) goes free
        Optional<AppliedDiscount> result = strategy.evaluate(ctx(
                buy4Pay3(),
                item(1L, 1, "50"),
                item(2L, 1, "60"),
                item(3L, 1, "70"),
                item(4L, 1, "80")
        ));

        assertThat(result).isPresent();
        assertThat(result.get().amount()).isEqualByComparingTo("50.00");
    }

    @Test
    void eightItems_twoFullGroupsTwoCheapestFree() {
        // 8 items, two groups of 4 → cheapest 2 go free
        Optional<AppliedDiscount> result = strategy.evaluate(ctx(
                buy4Pay3(),
                item(1L, 1, "10"),
                item(2L, 1, "20"),
                item(3L, 1, "30"),
                item(4L, 1, "40"),
                item(5L, 1, "50"),
                item(6L, 1, "60"),
                item(7L, 1, "70"),
                item(8L, 1, "80")
        ));

        assertThat(result).isPresent();
        assertThat(result.get().amount()).isEqualByComparingTo("30.00"); // 10 + 20
    }

    @Test
    void fewerThanXItems_noDiscount() {
        Optional<AppliedDiscount> result = strategy.evaluate(ctx(
                buy4Pay3(),
                item(1L, 3, "100")  // 3 units total
        ));

        assertThat(result).isEmpty();
    }

    @Test
    void singleProductWithEnoughQuantity_treatsEachUnitSeparately() {
        // 4 units of the same product at 25 each → cheapest unit (25) is free
        Optional<AppliedDiscount> result = strategy.evaluate(ctx(
                buy4Pay3(),
                item(1L, 4, "25")
        ));

        assertThat(result).isPresent();
        assertThat(result.get().amount()).isEqualByComparingTo("25.00");
    }

    @Test
    void malformedCampaign_skipped() {
        Campaign broken = Campaign.builder()
                .code("BROKEN")
                .type(CampaignType.BUY_X_PAY_Y)
                .value(new BigDecimal("3"))
                .payY(5) // pay > buy → invalid
                .active(true)
                .build();
        Optional<AppliedDiscount> result = strategy.evaluate(ctx(broken,
                item(1L, 1, "10"), item(2L, 1, "10"), item(3L, 1, "10"), item(4L, 1, "10")));

        assertThat(result).isEmpty();
    }

    private Campaign buy4Pay3() {
        return Campaign.builder()
                .code("4AL3ÖDE")
                .label("4 al 3 öde")
                .type(CampaignType.BUY_X_PAY_Y)
                .value(new BigDecimal("4"))
                .payY(3)
                .priority(20)
                .active(true)
                .build();
    }

    private CartItem item(Long productId, int qty, String price) {
        return CartItem.builder()
                .productId(productId)
                .productName("p" + productId)
                .quantity(qty)
                .unitPrice(new BigDecimal(price))
                .currency("TRY")
                .build();
    }

    private QuoteContext ctx(Campaign campaign, CartItem... items) {
        BigDecimal subtotal = List.of(items).stream()
                .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new QuoteContext(1L, List.of(items), subtotal, null, null, List.of(campaign));
    }
}
