package com.n11.order.messaging;

import com.n11.common.saga.SagaTopology;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
    public Queue orderPaymentResultQueue() {
        return QueueBuilder.durable(SagaTopology.Queue.ORDER_PAYMENT_RESULT).build();
    }

    @Bean
    public Binding bindPaymentSucceeded(Queue orderPaymentResultQueue, TopicExchange sagaExchange) {
        return BindingBuilder.bind(orderPaymentResultQueue).to(sagaExchange)
                .with(SagaTopology.RoutingKey.PAYMENT_SUCCEEDED);
    }

    @Bean
    public Binding bindPaymentFailed(Queue orderPaymentResultQueue, TopicExchange sagaExchange) {
        return BindingBuilder.bind(orderPaymentResultQueue).to(sagaExchange)
                .with(SagaTopology.RoutingKey.PAYMENT_FAILED);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(cf);
        template.setMessageConverter(converter);
        template.setExchange(SagaTopology.EXCHANGE);
        return template;
    }
}
