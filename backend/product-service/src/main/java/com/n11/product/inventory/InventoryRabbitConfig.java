package com.n11.product.inventory;

import com.n11.common.saga.SagaTopology;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * product-service publishes low-stock-report events <em>and</em> consumes
 * ORDER_CANCELLED so it can compensate the stock decrement issued during
 * checkout reservation.  Wires up the saga exchange + DLX in the same
 * pattern as cart-service / notification-service: durable queue with an
 * x-dead-letter-exchange routing failed messages to a parking-lot DLQ.
 */
@Configuration
public class InventoryRabbitConfig {

    private static final String DLX_ARG = "x-dead-letter-exchange";

    @Bean
    public TopicExchange sagaExchange() {
        return new TopicExchange(SagaTopology.EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange sagaDlxExchange() {
        return new TopicExchange(SagaTopology.DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue productOrderCancelledStockQueue() {
        return QueueBuilder.durable(SagaTopology.Queue.PRODUCT_ORDER_CANCELLED_STOCK)
                .withArgument(DLX_ARG, SagaTopology.DLX_EXCHANGE)
                .build();
    }

    @Bean
    public Binding bindProductOrderCancelledStock(Queue productOrderCancelledStockQueue,
                                                  TopicExchange sagaExchange) {
        return BindingBuilder.bind(productOrderCancelledStockQueue).to(sagaExchange)
                .with(SagaTopology.RoutingKey.ORDER_CANCELLED);
    }

    @Bean
    public Queue productOrderCancelledStockDlq() {
        return QueueBuilder.durable(SagaTopology.Queue.PRODUCT_ORDER_CANCELLED_STOCK_DLQ).build();
    }

    @Bean
    public Binding bindProductOrderCancelledStockDlq(Queue productOrderCancelledStockDlq,
                                                     TopicExchange sagaDlxExchange) {
        return BindingBuilder.bind(productOrderCancelledStockDlq).to(sagaDlxExchange)
                .with(SagaTopology.RoutingKey.ORDER_CANCELLED);
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
}
