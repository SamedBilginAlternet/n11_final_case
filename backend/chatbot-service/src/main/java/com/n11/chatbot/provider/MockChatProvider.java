package com.n11.chatbot.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
@ConditionalOnProperty(prefix = "n11.chatbot", name = "provider", havingValue = "MOCK", matchIfMissing = true)
@Slf4j
public class MockChatProvider implements ChatProvider {

    public MockChatProvider() {
        log.info("MockChatProvider active (n11.chatbot.provider=MOCK)");
    }

    @Override
    public Reply complete(List<Turn> history, String systemPrompt) {
        String last = history.isEmpty() ? "" : history.get(history.size() - 1).content().toLowerCase(Locale.forLanguageTag("tr"));
        String reply;
        if (last.contains("kargo")) {
            reply = "Kargo süreci sipariş onaylandıktan sonra 1-3 iş günü içinde tamamlanır. " +
                    "Ücretsiz kargo etiketi olan ürünlerde teslimat ücretsizdir.";
        } else if (last.contains("iade") || last.contains("değişim")) {
            reply = "İade hakkın 14 gün içinde geçerli — sipariş detayından 'İade et' adımını takip edebilirsin.";
        } else if (last.contains("merhaba") || last.contains("selam")) {
            reply = "Merhaba! Sana nasıl yardımcı olabilirim? Ürün önerisi, kampanyalar veya sipariş takibi için sorabilirsin.";
        } else if (last.contains("kampanya") || last.contains("indirim")) {
            reply = "Bugün için 'Peşin Fiyatına 6 Taksit' ve 400 TL'ye varan n11 Bonus kampanyaları geçerli. " +
                    "Detayları ana sayfadaki banner'dan görebilirsin.";
        } else if (last.isBlank()) {
            reply = "Sana nasıl yardımcı olabilirim?";
        } else {
            reply = "Bu konuda yardımcı olmaya çalışayım: kampanyalar, sipariş durumu ya da ürün önerisi konularında soru sorabilirsin.";
        }
        return new Reply(reply, reply.length() / 4);
    }
}
