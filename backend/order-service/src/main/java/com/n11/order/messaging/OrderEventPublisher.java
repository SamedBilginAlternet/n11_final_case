package com.n11.order.messaging;

import com.n11.common.event.OrderCancelledEvent;
import com.n11.common.event.OrderConfirmedEvent;
import com.n11.common.event.OrderCreatedEvent;
import com.n11.common.saga.SagaTopology;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishOrderCreated(OrderCreatedEvent event) {
        rabbitTemplate.convertAndSend(SagaTopology.EXCHANGE, SagaTopology.RoutingKey.ORDER_CREATED, event);
        log.info("Published OrderCreated orderId={} eventId={}", event.orderId(), event.eventId());
    }

    public void publishOrderConfirmed(OrderConfirmedEvent event) {
        rabbitTemplate.convertAndSend(SagaTopology.EXCHANGE, SagaTopology.RoutingKey.ORDER_CONFIRMED, event);
        log.info("Published OrderConfirmed orderId={} eventId={}", event.orderId(), event.eventId());
    }

    public void publishOrderCancelled(OrderCancelledEvent event) {
        rabbitTemplate.convertAndSend(SagaTopology.EXCHANGE, SagaTopology.RoutingKey.ORDER_CANCELLED, event);
        log.info("Published OrderCancelled orderId={} eventId={}", event.orderId(), event.eventId());
    }
}
