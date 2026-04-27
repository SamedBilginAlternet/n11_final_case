package com.n11.chatbot.api.dto;

import com.n11.chatbot.domain.MessageRole;

public record HistoryMessage(
        Long id,
        MessageRole role,
        String content,
        String createdAt
) {}
