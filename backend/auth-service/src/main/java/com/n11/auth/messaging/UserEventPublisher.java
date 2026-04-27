package com.n11.auth.messaging;

import com.n11.common.correlation.CorrelationId;
import com.n11.common.event.UserRegisteredEvent;
import com.n11.common.saga.SagaTopology;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishRegistered(Long userId, String email, String fullName) {
        String cid = MDC.get(CorrelationId.MDC_KEY);
        UserRegisteredEvent event = UserRegisteredEvent.of(userId, email, fullName, cid);
        rabbitTemplate.convertAndSend(SagaTopology.EXCHANGE, SagaTopology.RoutingKey.USER_REGISTERED, event);
        log.info("Published UserRegisteredEvent userId={} eventId={}", userId, event.eventId());
    }
}
