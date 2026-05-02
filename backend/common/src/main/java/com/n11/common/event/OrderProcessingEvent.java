package com.n11.common.event;

import java.time.Instant;
import java.util.UUID;

public record OrderProcessingEvent(
        UUID eventId,
        Instant occurredAt,
        Long orderId,
        Long userId,
        String userEmail,
        String correlationId
) {
    public static OrderProcessingEvent of(Long orderId, Long userId, String userEmail, String correlationId) {
        return new OrderProcessingEvent(UUID.randomUUID(), Instant.now(), orderId, userId, userEmail, correlationId);
    }
}
