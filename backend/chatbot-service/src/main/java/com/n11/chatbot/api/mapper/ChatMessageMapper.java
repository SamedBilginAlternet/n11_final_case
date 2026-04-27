package com.n11.chatbot.api.mapper;

import com.n11.chatbot.api.dto.HistoryMessage;
import com.n11.chatbot.domain.ChatMessage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ChatMessageMapper {

    @Mapping(target = "createdAt", expression = "java(message.getCreatedAt() == null ? null : message.getCreatedAt().toString())")
    HistoryMessage toDto(ChatMessage message);

    List<HistoryMessage> toDtos(List<ChatMessage> messages);
}
