package com.n11.cart.messaging;

import com.n11.cart.domain.Coupon;
import com.n11.cart.domain.CouponRedemption;
import com.n11.cart.repository.CouponRedemptionRepository;
import com.n11.cart.repository.CouponRepository;
import com.n11.common.correlation.CorrelationId;
import com.n11.common.event.OrderCancelledEvent;
import com.n11.common.event.OrderCreatedEvent;
import com.n11.common.saga.SagaTopology;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Choreography saga consumer that turns the cart-service coupon ledger into
 * the correct state for every {@code OrderCreated} / {@code OrderCancelled}
 * delivered by RabbitMQ.
 *
 * <h3>Reservation flow ({@link OrderCreatedEvent})</h3>
 * <ol>
 *   <li>If the event has no couponCode → no-op.</li>
 *   <li>Atomic UPDATE: {@code redemptions = redemptions + 1} guarded by
 *       {@code redemptions < max_redemptions}. If the row count is 0 the
 *       coupon was already exhausted, made inactive, or deleted between
 *       quote-time and order-creation: log a warning and stop. The order
 *       proceeds at full price (cart's quote was stale; we do not roll
 *       the order back, that would be a bigger compensation than the
 *       discount itself).</li>
 *   <li>Insert the redemption row with the unique (coupon_id, order_id)
 *       constraint. A duplicate delivery hits the constraint, we swallow
 *       the violation and decrement back the counter we just bumped, so
 *       the ledger and counter stay consistent.</li>
 * </ol>
 *
 * <h3>Compensation flow ({@link OrderCancelledEvent})</h3>
 * <ol>
 *   <li>Look up redemption by orderId. If absent → either the order had no
 *       coupon, or compensation already ran. Either way: no-op.</li>
 *   <li>Atomic decrement of {@code coupons.redemptions} (floors at zero) +
 *       delete the redemption row. Both inside the same TX so a duplicate
 *       OrderCancelled finds nothing on the second pass.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CouponSagaListener {

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository redemptionRepository;

    @Transactional
    @RabbitListener(queues = SagaTopology.Queue.CART_ORDER_CREATED_COUPON)
    public void onOrderCreated(OrderCreatedEvent event) {
        if (event.couponCode() == null || event.couponCode().isBlank()) {
            return;
        }
        withCorrelation(event.correlationId(), () -> reserve(event));
    }

    @Transactional
    @RabbitListener(queues = SagaTopology.Queue.CART_ORDER_CANCELLED_COUPON)
    public void onOrderCancelled(OrderCancelledEvent event) {
        if (event.couponCode() == null || event.couponCode().isBlank()) {
            return;
        }
        withCorrelation(event.correlationId(), () -> release(event));
    }

    private void reserve(OrderCreatedEvent event) {
        String code = event.couponCode();
        Coupon coupon = couponRepository.findByCodeIgnoreCase(code).orElse(null);
        if (coupon == null) {
            log.warn("OrderCreated coupon={} not found — skipping reservation orderId={}", code, event.orderId());
            return;
        }

        // Already reserved (duplicate delivery)? Treat as success without touching the counter.
        if (redemptionRepository.findByCouponIdAndOrderId(coupon.getId(), event.orderId()).isPresent()) {
            log.debug("Coupon {} already reserved for orderId={} — duplicate delivery", code, event.orderId());
            return;
        }

        int updated = couponRepository.reserveOne(code);
        if (updated == 0) {
            log.warn("Coupon {} could not be reserved (cap reached / inactive) for orderId={}", code, event.orderId());
            return;
        }

        try {
            redemptionRepository.save(CouponRedemption.builder()
                    .couponId(coupon.getId())
                    .orderId(event.orderId())
                    .userId(event.userId())
                    .build());
            log.info("Reserved coupon {} for orderId={} userId={}", code, event.orderId(), event.userId());
        } catch (DataIntegrityViolationException ex) {
            // Race: another concurrent OrderCreated for the same orderId got there first.
            // Roll the counter back to keep books even.
            couponRepository.releaseOne(code);
            log.warn("Concurrent reservation collision on coupon={} orderId={} — counter rolled back", code, event.orderId());
        }
    }

    private void release(OrderCancelledEvent event) {
        String code = event.couponCode();
        var redemption = redemptionRepository.findByOrderId(event.orderId()).orElse(null);
        if (redemption == null) {
            log.debug("OrderCancelled orderId={} — no redemption to release (idempotent)", event.orderId());
            return;
        }
        couponRepository.releaseOne(code);
        redemptionRepository.delete(redemption);
        log.info("Released coupon {} from orderId={} (compensation)", code, event.orderId());
    }

    private void withCorrelation(String cid, Runnable runnable) {
        if (cid != null) MDC.put(CorrelationId.MDC_KEY, cid);
        try {
            runnable.run();
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }
}
