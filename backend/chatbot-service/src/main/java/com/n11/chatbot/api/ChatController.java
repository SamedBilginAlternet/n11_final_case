package com.n11.chatbot.api;

import com.n11.chatbot.api.dto.ChatReply;
import com.n11.chatbot.api.dto.ChatRequest;
import com.n11.chatbot.api.dto.HistoryMessage;
import com.n11.chatbot.api.mapper.ChatMessageMapper;
import com.n11.chatbot.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Chatbot")
public class ChatController {

    private final ChatService chatService;
    private final ChatMessageMapper messageMapper;

    @Operation(summary = "Send a message to the assistant. Returns a sessionId — keep it for follow-ups.")
    @PostMapping
    public ChatReply send(@RequestBody @Valid ChatRequest request,
                          @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        String sessionId = (request.sessionId() == null || request.sessionId().isBlank())
                ? UUID.randomUUID().toString()
                : request.sessionId();
        var turn = chatService.send(sessionId, null, guestToken, request.message());
        return new ChatReply(turn.sessionId(), turn.reply(), turn.createdAt());
    }

    @Operation(summary = "Get full history for a session — caller must own it (X-Guest-Token match).")
    @GetMapping("/{sessionId}/history")
    public List<HistoryMessage> history(@PathVariable String sessionId,
                                        @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        return messageMapper.toDtos(chatService.history(sessionId, null, guestToken));
    }
}
