package com.n11.cart.messaging;

import com.n11.common.saga.SagaTopology;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange sagaExchange() {
        return new TopicExchange(SagaTopology.EXCHANGE, true, false);
    }

    @Bean
    public Queue cartOrderConfirmedQueue() {
        return QueueBuilder.durable(SagaTopology.Queue.CART_ORDER_CONFIRMED)
                .withArgument("x-dead-letter-exchange", SagaTopology.EXCHANGE + ".dlx")
                .build();
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
        return QueueBuilder.durable(SagaTopology.Queue.CART_ORDER_CREATED_COUPON)
                .withArgument("x-dead-letter-exchange", SagaTopology.EXCHANGE + ".dlx")
                .build();
    }

    @Bean
    public Binding bindCartOrderCreatedCoupon(Queue cartOrderCreatedCouponQueue, TopicExchange sagaExchange) {
        return BindingBuilder.bind(cartOrderCreatedCouponQueue).to(sagaExchange)
                .with(SagaTopology.RoutingKey.ORDER_CREATED);
    }

    @Bean
    public Queue cartOrderCancelledCouponQueue() {
        return QueueBuilder.durable(SagaTopology.Queue.CART_ORDER_CANCELLED_COUPON)
                .withArgument("x-dead-letter-exchange", SagaTopology.EXCHANGE + ".dlx")
                .build();
    }

    @Bean
    public Binding bindCartOrderCancelledCoupon(Queue cartOrderCancelledCouponQueue, TopicExchange sagaExchange) {
        return BindingBuilder.bind(cartOrderCancelledCouponQueue).to(sagaExchange)
                .with(SagaTopology.RoutingKey.ORDER_CANCELLED);
    }

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
}
