package com.n11.common.event;

import java.time.Instant;
import java.util.UUID;

public record OrderCancelledEvent(
        UUID eventId,
        Instant occurredAt,
        Long orderId,
        Long userId,
        String userEmail,
        String reason,
        String correlationId
) {
    public static OrderCancelledEvent of(Long orderId,
                                         Long userId,
                                         String userEmail,
                                         String reason,
                                         String correlationId) {
        return new OrderCancelledEvent(UUID.randomUUID(), Instant.now(), orderId, userId, userEmail, reason, correlationId);
    }
}
