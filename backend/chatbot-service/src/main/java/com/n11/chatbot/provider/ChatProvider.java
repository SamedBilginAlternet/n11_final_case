package com.n11.chatbot.provider;

import java.util.List;

public interface ChatProvider {

    Reply complete(List<Turn> history, String systemPrompt);

    record Turn(String role, String content) {}

    record Reply(String content, Integer tokensUsed) {}
}
