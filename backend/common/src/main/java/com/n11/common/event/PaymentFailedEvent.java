package com.n11.common.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentFailedEvent(
        UUID eventId,
        Instant occurredAt,
        Long orderId,
        Long paymentId,
        String reason,
        String correlationId
) {
    public static PaymentFailedEvent of(Long orderId,
                                        Long paymentId,
                                        String reason,
                                        String correlationId) {
        return new PaymentFailedEvent(
                UUID.randomUUID(),
                Instant.now(),
                orderId,
                paymentId,
                reason,
                correlationId
        );
    }
}
