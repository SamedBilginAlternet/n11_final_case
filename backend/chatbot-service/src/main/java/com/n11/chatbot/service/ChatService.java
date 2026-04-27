package com.n11.chatbot.service;

import com.n11.chatbot.domain.ChatMessage;
import com.n11.chatbot.domain.ChatSession;
import com.n11.chatbot.domain.MessageRole;
import com.n11.chatbot.grounding.CatalogGrounding;
import com.n11.chatbot.provider.ChatProvider;
import com.n11.chatbot.repository.ChatMessageRepository;
import com.n11.chatbot.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private static final int HISTORY_WINDOW = 16;
    private static final String SYSTEM_PROMPT_BASE = """
            Sen n11 e-ticaret platformunun alışveriş asistanısın.
            Türkçe, kısa ve samimi yanıt ver. Kampanya, kargo, iade ve ürün önerileriyle yardımcı ol.
            Kesin emin değilsen kibarca uyar. n11 dışındaki sitelere yönlendirme.
            """;

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatProvider provider;
    private final CatalogGrounding grounding;

    @Transactional
    public ChatTurn send(String sessionId, Long userId, String guestToken, String userMessage) {
        ChatSession session = sessionRepository.findById(sessionId).orElseGet(() -> sessionRepository.save(
                ChatSession.builder().id(sessionId).userId(userId).guestToken(guestToken).build()));

        messageRepository.save(ChatMessage.builder()
                .sessionId(session.getId())
                .role(MessageRole.USER)
                .content(userMessage)
                .build());

        List<ChatMessage> history = messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        List<ChatProvider.Turn> window = recentWindow(history);

        String systemPrompt = SYSTEM_PROMPT_BASE + "\n\n" + grounding.snapshotForPrompt();
        ChatProvider.Reply reply = provider.complete(window, systemPrompt);

        ChatMessage assistant = messageRepository.save(ChatMessage.builder()
                .sessionId(session.getId())
                .role(MessageRole.ASSISTANT)
                .content(reply.content())
                .tokensUsed(reply.tokensUsed())
                .build());

        session.setUpdatedAt(java.time.Instant.now());
        sessionRepository.save(session);

        log.info("Chat turn sessionId={} userMsgLen={} assistantMsgLen={}",
                sessionId, userMessage.length(), reply.content().length());

        return new ChatTurn(session.getId(), assistant.getContent(), assistant.getCreatedAt().toString());
    }

    /**
     * Returns the full message log of a session, but only to the identity that
     * owns it. Ownership is matched on (userId) for authed sessions or
     * (guestToken) for anonymous ones — the same identifier the session was
     * created with via {@link #send}.
     *
     * <p>404 — not 403 — is returned for both 'session does not exist' and
     * 'session belongs to someone else'. Same anti-enumeration policy used in
     * the payment service: a non-owner can't even confirm whether a sessionId
     * is in use.</p>
     */
    public List<ChatMessage> history(String sessionId, Long callerUserId, String callerGuestToken) {
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));

        boolean ownsAsUser = callerUserId != null && Objects.equals(callerUserId, session.getUserId());
        boolean ownsAsGuest = callerGuestToken != null && !callerGuestToken.isBlank()
                && Objects.equals(callerGuestToken, session.getGuestToken());

        if (!ownsAsUser && !ownsAsGuest) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    private List<ChatProvider.Turn> recentWindow(List<ChatMessage> history) {
        int from = Math.max(0, history.size() - HISTORY_WINDOW);
        List<ChatProvider.Turn> turns = new ArrayList<>();
        for (int i = from; i < history.size(); i++) {
            ChatMessage m = history.get(i);
            if (m.getRole() == MessageRole.SYSTEM) continue;
            turns.add(new ChatProvider.Turn(
                    m.getRole() == MessageRole.USER ? "user" : "assistant",
                    m.getContent()));
        }
        return turns;
    }

    public record ChatTurn(String sessionId, String reply, String createdAt) {}
}
