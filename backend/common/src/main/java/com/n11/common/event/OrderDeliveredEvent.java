package com.n11.common.event;

import java.time.Instant;
import java.util.UUID;

public record OrderDeliveredEvent(
        UUID eventId,
        Instant occurredAt,
        Long orderId,
        Long userId,
        String userEmail,
        String correlationId
) {
    public static OrderDeliveredEvent of(Long orderId, Long userId, String userEmail, String correlationId) {
        return new OrderDeliveredEvent(UUID.randomUUID(), Instant.now(), orderId, userId, userEmail, correlationId);
    }
}
