package com.n11.cart.messaging;

import com.n11.common.saga.SagaTopology;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology owned by cart-service.
 *
 * <p>Primary path</p>
 * <pre>
 *   saga.exchange (topic, durable)
 *     ├─ order.confirmed   → cart.order-confirmed.q          → OrderConfirmedListener
 *     ├─ order.created     → cart.order-created.coupon.q     → CouponSagaListener.reserve
 *     └─ order.cancelled   → cart.order-cancelled.coupon.q   → CouponSagaListener.release
 * </pre>
 *
 * <p>Dead-letter path</p>
 * Every primary queue is configured with {@code x-dead-letter-exchange =
 * saga.exchange.dlx}. When a consumer rejects a message without requeue (or
 * the broker times it out), the broker republishes it to the DLX with the
 * <em>original</em> routing key. Each primary queue has a matching {@code .dlq}
 * bound to the DLX with that same key — so failures stay parked next to the
 * step they failed on, ready for inspection in RabbitMQ Management UI or
 * manual replay.
 */
@Configuration
public class RabbitConfig {

    private static final String DLX_ARG = "x-dead-letter-exchange";

    // -------------------------------------------------------------------- exchanges

    @Bean
    public TopicExchange sagaExchange() {
        return new TopicExchange(SagaTopology.EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange sagaDlxExchange() {
        return new TopicExchange(SagaTopology.DLX_EXCHANGE, true, false);
    }

    // -------------------------------------------------------------------- primary queues

    @Bean
    public Queue cartOrderConfirmedQueue() {
        return primaryQueue(SagaTopology.Queue.CART_ORDER_CONFIRMED);
    }

    @Bean
    public Binding bindCartOrderConfirmed(Queue cartOrderConfirmedQueue, TopicExchange sagaExchange) {
        return BindingBuilder.bind(cartOrderConfirmedQueue).to(sagaExchange)
                .with(SagaTopology.RoutingKey.ORDER_CONFIRMED);
    }

    // Coupon reservation saga: separate queue from CART_ORDER_CONFIRMED so the
    // 'clear cart' and 'reserve coupon' consumers can fail / retry independently.
    @Bean
    public Queue cartOrderCreatedCouponQueue() {
        return primaryQueue(SagaTopology.Queue.CART_ORDER_CREATED_COUPON);
    }

    @Bean
    public Binding bindCartOrderCreatedCoupon(Queue cartOrderCreatedCouponQueue, TopicExchange sagaExchange) {
        return BindingBuilder.bind(cartOrderCreatedCouponQueue).to(sagaExchange)
                .with(SagaTopology.RoutingKey.ORDER_CREATED);
    }

    @Bean
    public Queue cartOrderCancelledCouponQueue() {
        return primaryQueue(SagaTopology.Queue.CART_ORDER_CANCELLED_COUPON);
    }

    @Bean
    public Binding bindCartOrderCancelledCoupon(Queue cartOrderCancelledCouponQueue, TopicExchange sagaExchange) {
        return BindingBuilder.bind(cartOrderCancelledCouponQueue).to(sagaExchange)
                .with(SagaTopology.RoutingKey.ORDER_CANCELLED);
    }

    // -------------------------------------------------------------------- dead-letter queues
    // Each DLQ is durable and has NO further DLX — it's a terminal parking lot.
    // Bindings use the same routing key as the primary queue so the broker's
    // 'republish to DLX with original key' default lands in the right .dlq.

    @Bean
    public Queue cartOrderConfirmedDlq() {
        return QueueBuilder.durable(SagaTopology.Queue.CART_ORDER_CONFIRMED_DLQ).build();
    }

    @Bean
    public Binding bindCartOrderConfirmedDlq(Queue cartOrderConfirmedDlq, TopicExchange sagaDlxExchange) {
        return BindingBuilder.bind(cartOrderConfirmedDlq).to(sagaDlxExchange)
                .with(SagaTopology.RoutingKey.ORDER_CONFIRMED);
    }

    @Bean
    public Queue cartOrderCreatedCouponDlq() {
        return QueueBuilder.durable(SagaTopology.Queue.CART_ORDER_CREATED_COUPON_DLQ).build();
    }

    @Bean
    public Binding bindCartOrderCreatedCouponDlq(Queue cartOrderCreatedCouponDlq, TopicExchange sagaDlxExchange) {
        return BindingBuilder.bind(cartOrderCreatedCouponDlq).to(sagaDlxExchange)
                .with(SagaTopology.RoutingKey.ORDER_CREATED);
    }

    @Bean
    public Queue cartOrderCancelledCouponDlq() {
        return QueueBuilder.durable(SagaTopology.Queue.CART_ORDER_CANCELLED_COUPON_DLQ).build();
    }

    @Bean
    public Binding bindCartOrderCancelledCouponDlq(Queue cartOrderCancelledCouponDlq, TopicExchange sagaDlxExchange) {
        return BindingBuilder.bind(cartOrderCancelledCouponDlq).to(sagaDlxExchange)
                .with(SagaTopology.RoutingKey.ORDER_CANCELLED);
    }

    // -------------------------------------------------------------------- shared infra

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory cf, MessageConverter converter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(cf);
        factory.setMessageConverter(converter);
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(8);
        return factory;
    }

    // -------------------------------------------------------------------- helpers

    private static Queue primaryQueue(String name) {
        return QueueBuilder.durable(name)
                .withArgument(DLX_ARG, SagaTopology.DLX_EXCHANGE)
                .build();
    }
}
