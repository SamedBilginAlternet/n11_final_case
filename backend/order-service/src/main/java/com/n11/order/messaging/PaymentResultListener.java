package com.n11.order.messaging;

import com.n11.common.correlation.CorrelationId;
import com.n11.common.event.OrderCancelledEvent;
import com.n11.common.event.OrderConfirmedEvent;
import com.n11.common.event.PaymentFailedEvent;
import com.n11.common.event.PaymentSucceededEvent;
import com.n11.common.saga.SagaTopology;
import com.n11.order.domain.Order;
import com.n11.order.domain.OrderStatus;
import com.n11.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentResultListener {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher publisher;

    @Transactional
    @RabbitListener(queues = SagaTopology.Queue.ORDER_PAYMENT_SUCCEEDED)
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        withCorrelation(event.correlationId(), () -> {
            Order order = orderRepository.findById(event.orderId()).orElse(null);
            if (order == null) {
                log.warn("PaymentSucceeded for unknown orderId={}", event.orderId());
                return;
            }
            if (order.getStatus() == OrderStatus.CONFIRMED) {
                log.info("Order {} already CONFIRMED — ignoring duplicate", order.getId());
                return;
            }
            order.transitionTo(OrderStatus.CONFIRMED);
            orderRepository.save(order);
            log.info("Order {} → CONFIRMED (paymentId={})", order.getId(), event.paymentId());
            publisher.publishOrderConfirmed(OrderConfirmedEvent.of(
                    order.getId(), order.getUserId(), order.getUserEmail(), event.correlationId()));
        });
    }

    @Transactional
    @RabbitListener(queues = SagaTopology.Queue.ORDER_PAYMENT_FAILED)
    public void onPaymentFailed(PaymentFailedEvent event) {
        withCorrelation(event.correlationId(), () -> {
            Order order = orderRepository.findById(event.orderId()).orElse(null);
            if (order == null) {
                log.warn("PaymentFailed for unknown orderId={}", event.orderId());
                return;
            }
            if (order.getStatus() == OrderStatus.CANCELLED) {
                log.info("Order {} already CANCELLED — ignoring duplicate", order.getId());
                return;
            }
            order.transitionTo(OrderStatus.CANCELLED);
            order.setFailureReason(event.reason());
            orderRepository.save(order);
            log.warn("Order {} → CANCELLED (reason={})", order.getId(), event.reason());
            publisher.publishOrderCancelled(OrderCancelledEvent.of(
                    order.getId(), order.getUserId(), order.getUserEmail(), event.reason(),
                    order.getCouponCode(), event.correlationId()));
        });
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
