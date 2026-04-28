package com.n11.common.event;

import java.time.Instant;
import java.util.UUID;

public record OrderShippedEvent(
        UUID eventId,
        Instant occurredAt,
        Long orderId,
        Long userId,
        String userEmail,
        String carrier,
        String trackingNumber,
        String correlationId
) {
    public static OrderShippedEvent of(Long orderId,
                                       Long userId,
                                       String userEmail,
                                       String carrier,
                                       String trackingNumber,
                                       String correlationId) {
        return new OrderShippedEvent(
                UUID.randomUUID(),
                Instant.now(),
                orderId,
                userId,
                userEmail,
                carrier,
                trackingNumber,
                correlationId);
    }
}
