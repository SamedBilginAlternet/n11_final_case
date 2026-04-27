package com.n11.common.event;

import java.time.Instant;
import java.util.UUID;

public record UserRegisteredEvent(
        UUID eventId,
        Instant occurredAt,
        Long userId,
        String email,
        String fullName,
        String correlationId
) {
    public static UserRegisteredEvent of(Long userId, String email, String fullName, String correlationId) {
        return new UserRegisteredEvent(UUID.randomUUID(), Instant.now(), userId, email, fullName, correlationId);
    }
}
