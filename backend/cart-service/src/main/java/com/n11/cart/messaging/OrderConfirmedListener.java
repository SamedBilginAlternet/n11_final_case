package com.n11.cart.messaging;

import com.n11.cart.service.CartService;
import com.n11.common.correlation.CorrelationId;
import com.n11.common.event.OrderConfirmedEvent;
import com.n11.common.saga.SagaTopology;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderConfirmedListener {

    private final CartService cartService;

    @RabbitListener(queues = SagaTopology.Queue.CART_ORDER_CONFIRMED)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        if (event.correlationId() != null) {
            MDC.put(CorrelationId.MDC_KEY, event.correlationId());
        }
        try {
            log.info("OrderConfirmed received orderId={} userId={} — clearing cart",
                    event.orderId(), event.userId());
            cartService.clear(event.userId());
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }
}
