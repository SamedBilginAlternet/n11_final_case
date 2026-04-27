package com.n11.payment.messaging;

import com.n11.common.correlation.CorrelationId;
import com.n11.common.event.OrderCreatedEvent;
import com.n11.common.saga.SagaTopology;
import com.n11.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCreatedListener {

    private final PaymentService paymentService;

    @RabbitListener(queues = SagaTopology.Queue.PAYMENT_ORDER_CREATED)
    public void onOrderCreated(OrderCreatedEvent event) {
        if (event.correlationId() != null) {
            MDC.put(CorrelationId.MDC_KEY, event.correlationId());
        }
        try {
            log.info("OrderCreated received orderId={} amount={} {}",
                    event.orderId(), event.totalAmount(), event.currency());
            paymentService.process(event);
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }
}
