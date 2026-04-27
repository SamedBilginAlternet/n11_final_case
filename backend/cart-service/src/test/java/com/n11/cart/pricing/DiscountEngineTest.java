package com.n11.cart.pricing;

import com.n11.cart.domain.Campaign;
import com.n11.cart.domain.CampaignType;
import com.n11.cart.domain.Cart;
import com.n11.cart.domain.CartItem;
import com.n11.cart.domain.Coupon;
import com.n11.cart.domain.CouponType;
import com.n11.cart.pricing.strategy.BuyXPayYStrategy;
import com.n11.cart.pricing.strategy.CouponCodeStrategy;
import com.n11.cart.pricing.strategy.PercentOffCartStrategy;
import com.n11.cart.repository.CampaignRepository;
import com.n11.cart.repository.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscountEngineTest {

    @Mock CampaignRepository campaignRepository;
    @Mock CouponRepository couponRepository;

    private DiscountEngine engine;

    @BeforeEach
    void wire() {
        engine = new DiscountEngine(
                List.of(new BuyXPayYStrategy(), new PercentOffCartStrategy(), new CouponCodeStrategy()),
                campaignRepository,
                couponRepository);
    }

    @Test
    void emptyCart_returnsZeroQuote() {
        when(campaignRepository.findActiveAt(any(Instant.class))).thenReturn(List.of());
        Cart cart = Cart.builder().userId(1L).items(List.of()).build();

        Quote q = engine.quote(cart);

        assertThat(q.subtotal()).isEqualByComparingTo("0");
        assertThat(q.total()).isEqualByComparingTo("0");
        assertThat(q.discounts()).isEmpty();
    }

    @Test
    void multipleStrategiesAccumulateAdditively_appliedAgainstOriginalSubtotal() {
        Cart cart = cart(item(1L, 1, "100"), item(2L, 1, "200"), item(3L, 1, "300"), item(4L, 1, "400"));
        // subtotal = 1000

        when(campaignRepository.findActiveAt(any(Instant.class))).thenReturn(List.of(
                Campaign.builder().code("PCT5").label("Sepette %5")
                        .type(CampaignType.PERCENT_OFF_CART).priority(30)
                        .value(new BigDecimal("5")).active(true).build(),
                Campaign.builder().code("4AL3").label("4 al 3 öde")
                        .type(CampaignType.BUY_X_PAY_Y).priority(20)
                        .value(new BigDecimal("4")).payY(3).active(true).build()
        ));
        when(couponRepository.findByCodeIgnoreCase("KUPON100")).thenReturn(Optional.of(
                Coupon.builder().code("KUPON100").label("100 TL Kupon")
                        .type(CouponType.FIXED).value(new BigDecimal("100"))
                        .active(true).redemptions(0).build()
        ));
        cart.setCouponCode("KUPON100");

        Quote q = engine.quote(cart);

        // BuyXPayY (cheapest of 4 = 100) + Percent5 (1000 * 0.05 = 50) + Coupon100 = 250
        assertThat(q.subtotal()).isEqualByComparingTo("1000");
        assertThat(q.totalDiscount()).isEqualByComparingTo("250");
        assertThat(q.total()).isEqualByComparingTo("750");
        assertThat(q.discounts()).hasSize(3);
        // priority order: 20, 30, 50
        assertThat(q.discounts().get(0).code()).isEqualTo("4AL3");
        assertThat(q.discounts().get(1).code()).isEqualTo("PCT5");
        assertThat(q.discounts().get(2).code()).isEqualTo("KUPON100");
    }

    @Test
    void totalNeverGoesNegative_evenWhenCouponExceedsSubtotal() {
        Cart cart = cart(item(1L, 1, "30"));
        when(campaignRepository.findActiveAt(any(Instant.class))).thenReturn(List.of());
        when(couponRepository.findByCodeIgnoreCase("BIG500")).thenReturn(Optional.of(
                Coupon.builder().code("BIG500").label("500 TL Kupon")
                        .type(CouponType.FIXED).value(new BigDecimal("500"))
                        .active(true).redemptions(0).build()
        ));
        cart.setCouponCode("BIG500");

        Quote q = engine.quote(cart);

        assertThat(q.subtotal()).isEqualByComparingTo("30");
        assertThat(q.total()).isEqualByComparingTo("0");
        // FIXED coupon is clamped to subtotal in CouponCodeStrategy itself
        assertThat(q.totalDiscount()).isEqualByComparingTo("30");
    }

    @Test
    void unknownCouponCode_silentlyDropsTheLine() {
        Cart cart = cart(item(1L, 1, "100"));
        when(campaignRepository.findActiveAt(any(Instant.class))).thenReturn(List.of());
        when(couponRepository.findByCodeIgnoreCase("DELETED")).thenReturn(Optional.empty());
        cart.setCouponCode("DELETED");

        Quote q = engine.quote(cart);

        assertThat(q.discounts()).isEmpty();
        assertThat(q.total()).isEqualByComparingTo("100");
    }

    private Cart cart(CartItem... items) {
        Cart c = Cart.builder().userId(1L).build();
        for (CartItem i : items) {
            i.setCart(c);
            c.getItems().add(i);
        }
        return c;
    }

    private CartItem item(Long pid, int qty, String price) {
        return CartItem.builder()
                .productId(pid).productName("p" + pid)
                .quantity(qty).unitPrice(new BigDecimal(price))
                .currency("TRY").build();
    }
}
