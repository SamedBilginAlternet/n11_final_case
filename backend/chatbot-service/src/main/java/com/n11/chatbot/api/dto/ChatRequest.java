package com.n11.chatbot.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        String sessionId,
        @NotBlank @Size(max = 2000) String message
) {}
