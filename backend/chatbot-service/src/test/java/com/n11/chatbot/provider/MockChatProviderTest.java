package com.n11.chatbot.provider;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockChatProviderTest {

    private final MockChatProvider provider = new MockChatProvider();

    @Test
    void answersKargoQuestion() {
        var reply = provider.complete(List.of(turn("Kargo ne kadar sürer?")), "system");
        assertThat(reply.content()).contains("Kargo");
    }

    @Test
    void answersIade() {
        var reply = provider.complete(List.of(turn("Ürünü iade etmek istiyorum")), "system");
        assertThat(reply.content()).contains("İade");
    }

    @Test
    void greetsBack() {
        var reply = provider.complete(List.of(turn("Merhaba")), "system");
        assertThat(reply.content().toLowerCase()).contains("merhaba");
    }

    @Test
    void fallsBackWhenUnknown() {
        var reply = provider.complete(List.of(turn("blabla random")), "system");
        assertThat(reply.content()).isNotBlank();
    }

    private ChatProvider.Turn turn(String content) {
        return new ChatProvider.Turn("user", content);
    }
}
