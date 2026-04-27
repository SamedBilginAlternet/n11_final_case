package com.n11.chatbot.service;

import com.n11.chatbot.domain.ChatMessage;
import com.n11.chatbot.domain.ChatSession;
import com.n11.chatbot.domain.MessageRole;
import com.n11.chatbot.grounding.CatalogGrounding;
import com.n11.chatbot.provider.ChatProvider;
import com.n11.chatbot.repository.ChatMessageRepository;
import com.n11.chatbot.repository.ChatSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock ChatSessionRepository sessionRepository;
    @Mock ChatMessageRepository messageRepository;
    @Mock ChatProvider provider;
    @Mock CatalogGrounding grounding;

    @InjectMocks ChatService service;

    @Test
    void persistsUserAndAssistantMessages() {
        when(sessionRepository.findById("s1")).thenReturn(Optional.empty());
        when(sessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> inv.getArgument(0));

        when(messageRepository.findBySessionIdOrderByCreatedAtAsc("s1"))
                .thenReturn(List.of(persisted("Merhaba", MessageRole.USER)));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            if (m.getId() == null) m.setId(1L);
            if (m.getCreatedAt() == null) m.setCreatedAt(Instant.now());
            return m;
        });

        when(grounding.snapshotForPrompt()).thenReturn("(catalog)");
        when(provider.complete(any(), anyString()))
                .thenReturn(new ChatProvider.Reply("Merhaba, nasıl yardımcı olabilirim?", 12));

        var turn = service.send("s1", null, "guest-1", "Merhaba");

        assertThat(turn.sessionId()).isEqualTo("s1");
        assertThat(turn.reply()).contains("Merhaba");

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());
        List<ChatMessage> saved = captor.getAllValues();
        assertThat(saved).extracting(ChatMessage::getRole)
                .contains(MessageRole.USER, MessageRole.ASSISTANT);
    }

    private ChatMessage persisted(String content, MessageRole role) {
        return ChatMessage.builder()
                .id(System.nanoTime())
                .sessionId("s1")
                .role(role)
                .content(content)
                .createdAt(Instant.now())
                .build();
    }
}
