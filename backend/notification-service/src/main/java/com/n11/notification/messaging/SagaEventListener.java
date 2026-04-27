package com.n11.notification.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.n11.common.saga.SagaTopology;
import com.n11.notification.slack.SlackNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SagaEventListener {

    private final SlackNotifier slack;
    private final ObjectMapper mapper;

    @RabbitListener(queues = SagaTopology.Queue.NOTIFICATION_FANOUT)
    public void onEvent(Message message) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        try {
            JsonNode event = mapper.readTree(message.getBody());
            String text = format(routingKey, event);
            if (text != null) {
                slack.send(text);
                log.info("Slack notified routingKey={}", routingKey);
            }
        } catch (Exception ex) {
            log.warn("Failed to handle event {}: {}", routingKey, ex.getMessage());
        }
    }

    private String format(String routingKey, JsonNode e) {
        return switch (routingKey) {
            case SagaTopology.RoutingKey.USER_REGISTERED ->
                    ":wave: New user registered: *%s* (id=%d)".formatted(e.path("email").asText(""), e.path("userId").asLong());
            case SagaTopology.RoutingKey.ORDER_CONFIRMED ->
                    ":white_check_mark: Order *#%d* confirmed for %s".formatted(e.path("orderId").asLong(), e.path("userEmail").asText(""));
            case SagaTopology.RoutingKey.ORDER_CANCELLED ->
                    ":x: Order *#%d* cancelled — %s".formatted(e.path("orderId").asLong(), e.path("reason").asText("unknown"));
            case SagaTopology.RoutingKey.PAYMENT_FAILED ->
                    ":warning: Payment failed for order *#%d* — %s".formatted(e.path("orderId").asLong(), e.path("reason").asText("unknown"));
            default -> null;
        };
    }
}
