package com.n11.common.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId,
        List<Map<String, String>> details
) {
    public static ApiError of(int status, String error, String message, String path, String correlationId) {
        return new ApiError(Instant.now(), status, error, message, path, correlationId, List.of());
    }

    public static ApiError withDetails(int status, String error, String message, String path,
                                       String correlationId, List<Map<String, String>> details) {
        return new ApiError(Instant.now(), status, error, message, path, correlationId, details);
    }
}
