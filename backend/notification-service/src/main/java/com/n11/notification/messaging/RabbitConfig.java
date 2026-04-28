package com.n11.notification.messaging;

import com.n11.common.saga.SagaTopology;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology owned by notification-service.
 *
 * <pre>
 *   saga.exchange (topic, durable)
 *     ├─ order.confirmed   → notification.order-confirmed.q   → OrderConfirmedNotifier
 *     ├─ order.shipped     → notification.order-shipped.q     → OrderShippedNotifier
 *     └─ order.delivered   → notification.order-delivered.q   → OrderDeliveredNotifier
 * </pre>
 *
 * Mirrors the cart-service DLX pattern: each primary queue has
 * x-dead-letter-exchange = saga.exchange.dlx + a matching .dlq parking lot
 * bound on the same routing key.  A mail send that fails after retries
 * lands in its .dlq for manual inspection rather than blocking the queue
 * or losing the event.
 */
@Configuration
public class RabbitConfig {

    private static final String DLX_ARG = "x-dead-letter-exchange";

    @Bean
    public TopicExchange sagaExchange() {
        return new TopicExchange(SagaTopology.EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange sagaDlxExchange() {
        return new TopicExchange(SagaTopology.DLX_EXCHANGE, true, false);
    }

    // ------------------------------------------------------ order.confirmed

    @Bean
    public Queue notificationOrderConfirmedQueue() {
        return primaryQueue(SagaTopology.Queue.NOTIFICATION_ORDER_CONFIRMED);
    }

    @Bean
    public Binding bindNotificationOrderConfirmed(Queue notificationOrderConfirmedQueue,
                                                  TopicExchange sagaExchange) {
        return BindingBuilder.bind(notificationOrderConfirmedQueue).to(sagaExchange)
                .with(SagaTopology.RoutingKey.ORDER_CONFIRMED);
    }

    @Bean
    public Queue notificationOrderConfirmedDlq() {
        return QueueBuilder.durable(SagaTopology.Queue.NOTIFICATION_ORDER_CONFIRMED_DLQ).build();
    }

    @Bean
    public Binding bindNotificationOrderConfirmedDlq(Queue notificationOrderConfirmedDlq,
                                                     TopicExchange sagaDlxExchange) {
        return BindingBuilder.bind(notificationOrderConfirmedDlq).to(sagaDlxExchange)
                .with(SagaTopology.RoutingKey.ORDER_CONFIRMED);
    }

    // ------------------------------------------------------ order.shipped

    @Bean
    public Queue notificationOrderShippedQueue() {
        return primaryQueue(SagaTopology.Queue.NOTIFICATION_ORDER_SHIPPED);
    }

    @Bean
    public Binding bindNotificationOrderShipped(Queue notificationOrderShippedQueue,
                                                TopicExchange sagaExchange) {
        return BindingBuilder.bind(notificationOrderShippedQueue).to(sagaExchange)
                .with(SagaTopology.RoutingKey.ORDER_SHIPPED);
    }

    @Bean
    public Queue notificationOrderShippedDlq() {
        return QueueBuilder.durable(SagaTopology.Queue.NOTIFICATION_ORDER_SHIPPED_DLQ).build();
    }

    @Bean
    public Binding bindNotificationOrderShippedDlq(Queue notificationOrderShippedDlq,
                                                   TopicExchange sagaDlxExchange) {
        return BindingBuilder.bind(notificationOrderShippedDlq).to(sagaDlxExchange)
                .with(SagaTopology.RoutingKey.ORDER_SHIPPED);
    }

    // ------------------------------------------------------ order.delivered

    @Bean
    public Queue notificationOrderDeliveredQueue() {
        return primaryQueue(SagaTopology.Queue.NOTIFICATION_ORDER_DELIVERED);
    }

    @Bean
    public Binding bindNotificationOrderDelivered(Queue notificationOrderDeliveredQueue,
                                                  TopicExchange sagaExchange) {
        return BindingBuilder.bind(notificationOrderDeliveredQueue).to(sagaExchange)
                .with(SagaTopology.RoutingKey.ORDER_DELIVERED);
    }

    @Bean
    public Queue notificationOrderDeliveredDlq() {
        return QueueBuilder.durable(SagaTopology.Queue.NOTIFICATION_ORDER_DELIVERED_DLQ).build();
    }

    @Bean
    public Binding bindNotificationOrderDeliveredDlq(Queue notificationOrderDeliveredDlq,
                                                     TopicExchange sagaDlxExchange) {
        return BindingBuilder.bind(notificationOrderDeliveredDlq).to(sagaDlxExchange)
                .with(SagaTopology.RoutingKey.ORDER_DELIVERED);
    }

    // ------------------------------------------------------ shared

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
        factory.setMaxConcurrentConsumers(4);
        return factory;
    }

    private static Queue primaryQueue(String name) {
        return QueueBuilder.durable(name)
                .withArgument(DLX_ARG, SagaTopology.DLX_EXCHANGE)
                .build();
    }
}
