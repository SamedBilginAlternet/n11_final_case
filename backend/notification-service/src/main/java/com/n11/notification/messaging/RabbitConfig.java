package com.n11.notification.messaging;

import com.n11.common.saga.SagaTopology;
import org.springframework.amqp.core.*;
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
    public Queue notificationFanoutQueue() {
        return QueueBuilder.durable(SagaTopology.Queue.NOTIFICATION_FANOUT).build();
    }

    @Bean
    public Binding bindUserRegistered(Queue notificationFanoutQueue, TopicExchange sagaExchange) {
        return BindingBuilder.bind(notificationFanoutQueue).to(sagaExchange)
                .with(SagaTopology.RoutingKey.USER_REGISTERED);
    }

    @Bean
    public Binding bindOrderConfirmed(Queue notificationFanoutQueue, TopicExchange sagaExchange) {
        return BindingBuilder.bind(notificationFanoutQueue).to(sagaExchange)
                .with(SagaTopology.RoutingKey.ORDER_CONFIRMED);
    }

    @Bean
    public Binding bindOrderCancelled(Queue notificationFanoutQueue, TopicExchange sagaExchange) {
        return BindingBuilder.bind(notificationFanoutQueue).to(sagaExchange)
                .with(SagaTopology.RoutingKey.ORDER_CANCELLED);
    }

    @Bean
    public Binding bindPaymentFailed(Queue notificationFanoutQueue, TopicExchange sagaExchange) {
        return BindingBuilder.bind(notificationFanoutQueue).to(sagaExchange)
                .with(SagaTopology.RoutingKey.PAYMENT_FAILED);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
