package com.n11.cart.messaging;

import com.n11.common.saga.SagaTopology;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
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

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleMessageListenerContainer cartListenerContainer(ConnectionFactory cf, MessageConverter converter) {
        SimpleMessageListenerContainer c = new SimpleMessageListenerContainer(cf);
        c.setQueueNames(SagaTopology.Queue.CART_ORDER_CONFIRMED);
        c.setMessageConverter(converter);
        c.setConcurrentConsumers(2);
        c.setMaxConcurrentConsumers(8);
        return c;
    }
}
