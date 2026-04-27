package com.n11.common.event;

import java.time.Instant;
import java.util.UUID;

public record OrderConfirmedEvent(
        UUID eventId,
        Instant occurredAt,
        Long orderId,
        Long userId,
        String userEmail,
        String correlationId
) {
    public static OrderConfirmedEvent of(Long orderId, Long userId, String userEmail, String correlationId) {
        return new OrderConfirmedEvent(UUID.randomUUID(), Instant.now(), orderId, userId, userEmail, correlationId);
    }
}
