package com.n11.chatbot.api.dto;

public record ChatReply(
        String sessionId,
        String reply,
        String createdAt
) {}
