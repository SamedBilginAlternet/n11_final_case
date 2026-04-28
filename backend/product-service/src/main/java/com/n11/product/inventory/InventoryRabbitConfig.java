package com.n11.product.inventory;

import com.n11.common.saga.SagaTopology;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * product-service is only a *publisher* on the saga exchange, never a
 * consumer.  This config wires up the topic-exchange + RabbitTemplate so
 * the scheduled stock scanner can convertAndSend.
 *
 * <p>No queues declared here — notification-service owns its own queue
 * + binding for low-stock-report consumption.</p>
 */
@Configuration
public class InventoryRabbitConfig {

    @Bean
    public TopicExchange sagaExchange() {
        return new TopicExchange(SagaTopology.EXCHANGE, true, false);
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
