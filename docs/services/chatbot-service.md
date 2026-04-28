# `chatbot-service`

**Bu doküman:** Müşteri destek asistanı. Provider-agnostic LLM çağrısı + product catalog
grounding.

**Port:** 8087
**DB:** `chatbotdb`
**Stack:** Spring Boot 3 + JPA + Flyway + RestClient
**External:** Groq API (default), Anthropic Claude API (opsiyonel), Mock provider

---

## 1. Sorumluluklar

| Concern | Endpoint | Erişim |
|---|---|---|
| Send message + get reply | `POST /api/chat` | Auth (JWT) |
| Session geçmişi | `GET /api/chat/sessions/{id}` | Auth |

User UI'daki sohbet penceresinden mesaj gönderir, asistan ürün catalog'una bakarak cevap verir.

---

## 2. Provider Strategy Pattern

```java
public interface ChatProvider {
    Reply complete(List<Turn> history, String systemPrompt);
    record Turn(String role, String content) { }
    record Reply(String content, Integer tokensUsed) { }
}
```

Üç implementation:

```java
@ConditionalOnProperty(prefix = "n11.chatbot", name = "provider", havingValue = "GROQ")
public class GroqChatProvider implements ChatProvider { ... }

@ConditionalOnProperty(prefix = "n11.chatbot", name = "provider", havingValue = "CLAUDE")
public class ClaudeChatProvider implements ChatProvider { ... }

@ConditionalOnProperty(prefix = "n11.chatbot", name = "provider", havingValue = "MOCK", matchIfMissing = true)
public class MockChatProvider implements ChatProvider { ... }
```

`CHATBOT_PROVIDER=GROQ|CLAUDE|MOCK` env ile seçilir. `matchIfMissing = true` Mock'a — fresh
clone'da hiç API key olmadan çalışır.

### Mock Provider

```java
@Component
public class MockChatProvider implements ChatProvider {
    @Override public Reply complete(List<Turn> history, String systemPrompt) {
        Turn last = history.get(history.size() - 1);
        if (last.content().toLowerCase().contains("merhaba")) {
            return new Reply("Merhaba! Bugün size nasıl yardımcı olabilirim?", 0);
        }
        return new Reply("Anladım. Size yardımcı olabileceğim başka bir konu var mı?", 0);
    }
}
```

Kullanım: bootcamp grader fresh clone, hiçbir API key olmadan UI demo eder. Trivial yanıt
ama akış (frontend → backend → DB session → response) tam.

### Groq Provider — OpenAI-Compatible

```java
@Component
@ConditionalOnProperty(...)
public class GroqChatProvider implements ChatProvider {
    private final ChatbotProperties props;
    private final RestClient client;
    
    public GroqChatProvider(ChatbotProperties props) {
        this.props = props;
        this.client = RestClient.builder()
                .baseUrl(props.groq().baseUrl())
                .defaultHeader("Authorization", "Bearer " + props.groq().apiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
    
    @Override
    public Reply complete(List<Turn> history, String systemPrompt) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        for (Turn t : history) messages.add(Map.of("role", t.role(), "content", t.content()));
        
        Map<String, Object> body = Map.of(
                "model", props.groq().model(),                // llama-3.3-70b-versatile
                "max_tokens", props.groq().maxTokens(),
                "temperature", props.groq().temperature(),
                "messages", messages
        );
        
        Map<String, Object> response = client.post()
                .uri("/openai/v1/chat/completions")           // Groq OpenAI-compatible endpoint
                .contentType(APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        
        return parseReply(response);
    }
}
```

Groq, OpenAI'ın `/v1/chat/completions` API'sini birebir uyumlu sunuyor. Bu yüzden ayrı bir
SDK gerektirmiyor — düz HTTP + Map.

### Niye Plain Map, Typed DTO Değil

Provider response'ları farklı şekiller döner. Map kullanmak typed-DTO'dan **daha esnek** —
yeni provider eklendiğinde ayrı bir parser yazmak yeter, response model'ini değiştirmek
gerek değil. Trade-off: type safety yok; runtime cast hataları parser'da yakalanır.

### Claude Provider

Anthropic Claude API farklı format (messages array structure) kullanır. Ayrı `ClaudeChatProvider`:
- `claude-sonnet-4-6` model.
- `max_tokens` zorunlu.
- System prompt **top-level field**, messages array içinde değil.

Her ikisi de aynı `ChatProvider` interface'ini implement eder — chat-service'in iş mantığı
provider-agnostic.

---

## 3. Catalog Grounding

User "iPhone 15 stokta var mı?" diye sorabilir. LLM doğrudan halüsinasyon yapmasın diye
catalog data'sıyla **grounded** olmalı:

```java
@Service
public class CatalogGrounder {
    private final RestClient productClient;
    
    public String fetchSnippet(String userMessage) {
        // Heuristic: extract product names from user message
        List<String> mentioned = simpleExtract(userMessage);
        if (mentioned.isEmpty()) return "";
        
        StringBuilder snippet = new StringBuilder();
        for (String name : mentioned) {
            try {
                List<AutocompleteSuggestion> matches = productClient.get()
                        .uri("/api/products/autocomplete?q=" + URLEncoder.encode(name, UTF_8) + "&limit=3")
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
                if (matches != null && !matches.isEmpty()) {
                    snippet.append("'" + name + "' için sonuçlar:\n");
                    matches.forEach(m -> snippet.append("- ").append(m.name())
                            .append(" (").append(m.categoryName()).append(")\n"));
                }
            } catch (Exception ex) {
                log.warn("Catalog fetch failed: {}", ex.getMessage());
            }
        }
        return snippet.toString();
    }
}
```

Sonuç system prompt'a inject edilir:

```
You are an n11 customer support assistant. Be friendly and concise. Answer in Turkish.

Catalog reference:
'iphone' için sonuçlar:
- iPhone 15 Pro Max (Telefon)
- iPhone 15 (Telefon)

User message: iPhone 15 stokta var mı?
```

LLM "iPhone 15 sayfasına gidip stoğa bakmanı öneririm" gibi grounded cevap üretir, halüsinasyon
yok.

### Niye Heuristic Extraction, Embeddings Değil

İdeal: user mesajını embedding'e çevir + catalog'da semantic search. Bizim için:
- Embedding model ekstra provider (OpenAI/Voyage), ekstra config + cost.
- Bootcamp scope'unda heuristic "kelime eşleşmesi" yeterli.
- Kullanıcılar genelde direkt ürün adı yazıyor (UI hint: "iPhone 15 hakkında..." default
  prompt).

---

## 4. Session History

```sql
CREATE TABLE chatbot_sessions (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_message_at TIMESTAMPTZ
);

CREATE TABLE chatbot_messages (
    id BIGSERIAL PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES chatbot_sessions(id),
    role VARCHAR(20) NOT NULL,        -- USER, ASSISTANT, SYSTEM
    content TEXT NOT NULL,
    tokens_used INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

User'ın sohbet geçmişi tutulur. Yeni mesaj gönderdiğinde **son N mesaj** prompt'a context
olarak verilir → multi-turn conversation.

### Niye N=10, niye unbounded değil

LLM context window sınırı + token maliyeti. Geçmiş büyüdükçe:
- Maliyet doğrusal artar.
- Eski mesajlar yeni soruyla alaka yitirir.
- 10 turn ~yarım saatlik konuşma = pratik upper bound.

`ChatService.complete`:
```java
List<ChatMessage> recent = messageRepository.findTop10BySessionIdOrderByCreatedAtAsc(sessionId);
List<Turn> turns = recent.stream().map(m -> new Turn(m.getRole().name().toLowerCase(), m.getContent())).toList();
turns.add(new Turn("user", request.message()));   // current
Reply reply = provider.complete(turns, systemPrompt);
```

---

## 5. Frontend Integration

`ChatBubbleButton` — sağ alt köşede yüzen ikon (Sparkles + bot icon, framer-motion ile
breathing pulse).

```jsx
<motion.div
    animate={{ scale: [1, 1.05, 1] }}
    transition={{ duration: 2.5, repeat: Infinity }}
    className="...">
    <Bot className="text-white" />
</motion.div>
```

`ChatPanel` — açıldığında full chat UI (mesaj listesi + input + send button + 3-dot typing
indicator).

Detay: [`docs/services/frontend.md`](frontend.md).

---

## 6. SecurityConfig

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean public SecurityFilterChain filterChain(...) {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()       // chat endpoint'leri auth
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

`/api/chat` JWT zorunlu. Anonim user chatbot kullanamaz — niye:
- Session history user_id'ye bağlı.
- Token quota per-user fairness.
- Spam koruması (random user botların kullanması engelli).

Trade-off: ziyaretçi-mode chatbot UX'i mahveder. Açmak gerekirse public endpoint + IP-based
rate limit.

---

## 7. Bilinçli Olarak Yapmadıklarımız

- **Streaming response**: LLM output'u token-by-token stream etmek (UX upgrade — typing
  effect). Gerekiyorsa SSE veya WebSocket. Şu an blocking.
- **Function calling**: LLM'in "stoğu sorgula" deyip backend tool'a delegate etmesi (Anthropic
  tool-use, OpenAI functions). Şu an grounding'de pre-fetch yapıyoruz, post-hoc tool call
  yapmıyoruz.
- **Cross-session context**: User farklı sessionda devam ettirmek isterse global memory yok.
- **Moderation**: User'ın küfür içeren mesajını LLM'e geçirmeden filtrele — yok. Production'da
  Anthropic moderation API veya Perspective API ile pre-filter mantıklı.
- **Per-user token budget**: Aşırı kullanım kontrolsuz. Future: günlük token cap.

---

## 8. Klasör Yapısı

```
backend/chatbot-service/
├── pom.xml
└── src/main/java/com/n11/chatbot/
    ├── ChatbotApplication.java
    ├── api/
    │   ├── ChatController.java
    │   └── dto/
    ├── config/
    │   ├── SecurityConfig.java
    │   └── ChatbotProperties.java         # provider + per-provider config
    ├── domain/
    │   ├── ChatSession.java
    │   ├── ChatMessage.java
    │   └── Role.java                      # USER, ASSISTANT, SYSTEM
    ├── grounding/
    │   └── CatalogGrounder.java
    ├── provider/
    │   ├── ChatProvider.java              # interface
    │   ├── GroqChatProvider.java          # @ConditionalOnProperty
    │   ├── ClaudeChatProvider.java
    │   └── MockChatProvider.java
    ├── repository/
    │   ├── ChatSessionRepository.java
    │   └── ChatMessageRepository.java
    └── service/
        └── ChatService.java
```

---

## İlgili Dokümanlar

- [`docs/services/product-service.md`](product-service.md) — Catalog data tüketici
- [`docs/recommendations.md`](../recommendations.md) — Aynı Groq/Claude pattern, farklı use-case
- [`docs/services/frontend.md`](frontend.md) — Chat UI
