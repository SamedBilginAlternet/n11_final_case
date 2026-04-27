package com.n11.payment.messaging;

import com.n11.common.event.PaymentFailedEvent;
import com.n11.common.event.PaymentSucceededEvent;
import com.n11.common.saga.SagaTopology;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishSucceeded(PaymentSucceededEvent event) {
        rabbitTemplate.convertAndSend(SagaTopology.EXCHANGE, SagaTopology.RoutingKey.PAYMENT_SUCCEEDED, event);
        log.info("Published PaymentSucceeded paymentId={} orderId={}", event.paymentId(), event.orderId());
    }

    public void publishFailed(PaymentFailedEvent event) {
        rabbitTemplate.convertAndSend(SagaTopology.EXCHANGE, SagaTopology.RoutingKey.PAYMENT_FAILED, event);
        log.info("Published PaymentFailed paymentId={} orderId={} reason={}",
                event.paymentId(), event.orderId(), event.reason());
    }
}
