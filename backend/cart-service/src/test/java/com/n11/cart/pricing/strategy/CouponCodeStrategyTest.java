package com.n11.cart.pricing.strategy;

import com.n11.cart.domain.Coupon;
import com.n11.cart.domain.CouponType;
import com.n11.cart.pricing.AppliedDiscount;
import com.n11.cart.pricing.DiscountKind;
import com.n11.cart.pricing.QuoteContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CouponCodeStrategyTest {

    private final CouponCodeStrategy strategy = new CouponCodeStrategy();

    @Test
    void fixedCouponSubtractsAbsoluteAmount() {
        Coupon c = baseCoupon().code("KUPON100").type(CouponType.FIXED).value(new BigDecimal("100")).build();

        Optional<AppliedDiscount> result = strategy.evaluate(ctx(new BigDecimal("250"), c));

        assertThat(result).isPresent();
        assertThat(result.get().kind()).isEqualTo(DiscountKind.COUPON);
        assertThat(result.get().amount()).isEqualByComparingTo("100");
    }

    @Test
    void fixedCouponClampsToSubtotal_neverOverDiscounts() {
        Coupon c = baseCoupon().type(CouponType.FIXED).value(new BigDecimal("500")).build();

        Optional<AppliedDiscount> result = strategy.evaluate(ctx(new BigDecimal("250"), c));

        assertThat(result).isPresent();
        assertThat(result.get().amount()).isEqualByComparingTo("250"); // capped
    }

    @Test
    void percentCouponRoundsHalfUp() {
        Coupon c = baseCoupon().type(CouponType.PERCENT).value(new BigDecimal("15")).build();

        Optional<AppliedDiscount> result = strategy.evaluate(ctx(new BigDecimal("123.45"), c));

        assertThat(result).isPresent();
        assertThat(result.get().amount()).isEqualByComparingTo("18.52"); // 123.45 * 0.15 = 18.5175 → 18.52
    }

    @Test
    void inactiveCouponSkipped() {
        Coupon c = baseCoupon().type(CouponType.FIXED).value(new BigDecimal("50")).active(false).build();

        Optional<AppliedDiscount> result = strategy.evaluate(ctx(new BigDecimal("250"), c));
        assertThat(result).isEmpty();
    }

    @Test
    void expiredCouponSkipped() {
        Coupon c = baseCoupon().type(CouponType.FIXED).value(new BigDecimal("50"))
                .validUntil(Instant.now().minus(1, ChronoUnit.DAYS)).build();

        Optional<AppliedDiscount> result = strategy.evaluate(ctx(new BigDecimal("250"), c));
        assertThat(result).isEmpty();
    }

    @Test
    void redemptionCapReachedSkipped() {
        Coupon c = baseCoupon().type(CouponType.FIXED).value(new BigDecimal("50"))
                .maxRedemptions(10).redemptions(10).build();

        Optional<AppliedDiscount> result = strategy.evaluate(ctx(new BigDecimal("250"), c));
        assertThat(result).isEmpty();
    }

    @Test
    void belowMinCartTotalSkipped() {
        Coupon c = baseCoupon().type(CouponType.FIXED).value(new BigDecimal("50"))
                .minCartTotal(new BigDecimal("300")).build();

        Optional<AppliedDiscount> result = strategy.evaluate(ctx(new BigDecimal("250"), c));
        assertThat(result).isEmpty();
    }

    @Test
    void noCouponInContext_noDiscount() {
        Optional<AppliedDiscount> result = strategy.evaluate(ctx(new BigDecimal("100"), null));
        assertThat(result).isEmpty();
    }

    private Coupon.CouponBuilder baseCoupon() {
        return Coupon.builder()
                .code("X")
                .label("Kupon")
                .active(true)
                .redemptions(0);
    }

    private QuoteContext ctx(BigDecimal subtotal, Coupon coupon) {
        return new QuoteContext(1L, List.of(), subtotal, coupon == null ? null : coupon.getCode(), coupon, List.of());
    }
}
