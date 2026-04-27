package com.n11.cart.pricing;

import com.n11.cart.domain.Campaign;
import com.n11.cart.domain.Cart;
import com.n11.cart.domain.CartItem;
import com.n11.cart.domain.Coupon;
import com.n11.cart.repository.CampaignRepository;
import com.n11.cart.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates the discount Strategy Chain over a {@link Cart} → {@link Quote}.
 *
 * <h3>Order of operations</h3>
 * <ol>
 *   <li>Sum line totals → {@code subtotal}.</li>
 *   <li>Resolve cart's coupon code → {@link Coupon} (or null).</li>
 *   <li>Load all currently-valid campaigns (active + within window).</li>
 *   <li>Run every {@link DiscountStrategy} in priority order. Each call is
 *       independent — strategies don't see each other's output, so a
 *       50%-off coupon and a 5%-cart-wide campaign both compute against the
 *       original subtotal. This is intentional: the receipt UI lists each
 *       discount on its own line and the totals are easy to reason about.</li>
 *   <li>{@code totalDiscount = Σ amounts}, {@code total = max(subtotal − totalDiscount, 0)}.</li>
 * </ol>
 *
 * <h3>Why pre-load campaigns + coupon in this class</h3>
 * Strategies stay pure (no DB calls in tests) and we avoid the "20 strategies
 * each running 1 query" footgun. One query for active campaigns, one for the
 * coupon, regardless of how many strategy beans exist.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DiscountEngine {

    private final List<DiscountStrategy> strategies;
    private final CampaignRepository campaignRepository;
    private final CouponRepository couponRepository;

    @Transactional(readOnly = true)
    public Quote quote(Cart cart) {
        BigDecimal subtotal = subtotalOf(cart.getItems());
        Coupon coupon = resolveCoupon(cart.getCouponCode());
        List<Campaign> campaigns = campaignRepository.findActiveAt(Instant.now());

        QuoteContext ctx = new QuoteContext(
                cart.getUserId(),
                cart.getItems(),
                subtotal,
                cart.getCouponCode(),
                coupon,
                campaigns);

        List<AppliedDiscount> applied = strategies.stream()
                .sorted(Comparator.comparingInt(DiscountStrategy::priority))
                .map(s -> s.evaluate(ctx))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        BigDecimal totalDiscount = applied.stream()
                .map(AppliedDiscount::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal total = subtotal.subtract(totalDiscount);
        if (total.signum() < 0) total = BigDecimal.ZERO;

        log.debug("Quote userId={} subtotal={} discount={} total={} applied={}",
                cart.getUserId(), subtotal, totalDiscount, total, applied.size());

        return new Quote(subtotal, applied, totalDiscount, total);
    }

    private BigDecimal subtotalOf(List<CartItem> items) {
        return items.stream()
                .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Coupon resolveCoupon(String code) {
        if (code == null || code.isBlank()) return null;
        return couponRepository.findByCodeIgnoreCase(code).orElse(null);
    }
}
