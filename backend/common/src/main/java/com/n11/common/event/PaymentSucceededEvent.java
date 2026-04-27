package com.n11.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentSucceededEvent(
        UUID eventId,
        Instant occurredAt,
        Long orderId,
        Long paymentId,
        String providerRef,
        BigDecimal amount,
        String currency,
        String correlationId
) {
    public static PaymentSucceededEvent of(Long orderId,
                                           Long paymentId,
                                           String providerRef,
                                           BigDecimal amount,
                                           String currency,
                                           String correlationId) {
        return new PaymentSucceededEvent(
                UUID.randomUUID(),
                Instant.now(),
                orderId,
                paymentId,
                providerRef,
                amount,
                currency,
                correlationId
        );
    }
}
