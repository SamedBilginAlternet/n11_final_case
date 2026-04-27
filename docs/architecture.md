# Mimari

## Genel Bakış

```
                                ┌────────────────────────┐
              browser ──────►   │  frontend (nginx)      │  :3000
                                │  React + Vite + TW     │
                                └──────────┬─────────────┘
                                           │  /api/*  (proxied)
                                ┌──────────▼─────────────┐
                                │   api-gateway          │  :8080
                                │   Spring Cloud Gateway │
                                └──┬─────┬─────┬─────┬───┘
                                   │     │     │     │
              ┌────────────────────┘     │     │     └─────────────┐
              ▼                          ▼     ▼                   ▼
    ┌─────────────────┐    ┌──────────────────────┐    ┌─────────────────┐    ┌──────────────────┐
    │  auth-service   │    │   product-service    │    │  cart-service   │    │  order-service   │
    │     :8081       │    │       :8082          │    │     :8083       │    │      :8084       │
    │   (authdb)      │    │     (productdb)      │    │   (cartdb)      │    │    (orderdb)     │
    └─────────────────┘    └──────────┬───────────┘    └────────┬────────┘    └────────┬─────────┘
                                      │                         │                       │
                                      └─── REST ◄──────────────┘                       │
                                                                                        │
              ┌─────────────────┐    ┌──────────────────────┐                            │
              │ chatbot-service │    │   payment-service    │ ◄──────────────────────────┘
              │     :8087       │    │       :8085          │
              │  (chatbotdb)    │    │     (paymentdb)      │
              │  Groq / Claude  │    │  Iyzico integration  │
              └────────┬────────┘    └──────────┬───────────┘
                       │                        │
                       │ REST → product-service │
                       ▼                        ▼
                ┌─────────────────────────────────────────────────────────┐
                │            RabbitMQ — saga.exchange (topic)             │  :5672 / :15672
                └─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                       PostgreSQL 16 (per-service DB)   :5432            │
└─────────────────────────────────────────────────────────────────────────┘
```

## Servisler

| Servis | Port | DB | Sorumluluk |
|---|---|---|---|
| api-gateway | 8080 | — | Public giriş, route table, JWT relay (Authorization header passthrough), aggregated Swagger UI |
| auth-service | 8081 | authdb | `register`, `login`, `users/me`, JWT issuance |
| product-service | 8082 | productdb | Pagination + search + category listing + `/autocomplete` + ratings |
| cart-service | 8083 | cartdb | Sepet CRUD, `OrderConfirmed` consumer (sepeti boşaltma) |
| order-service | 8084 | orderdb | Checkout, sipariş yaşam döngüsü, `OrderCreated` publisher, payment event consumer, `OrderConfirmed/OrderCancelled` publisher |
| payment-service | 8085 | paymentdb | `OrderCreated` consumer, Iyzico integration, `PaymentSucceeded/PaymentFailed` publisher |
| chatbot-service | 8087 | chatbotdb | `POST /api/chat` — Groq / Claude / Mock provider, ürün katalog grounding ile RAG |

> **Slack runtime bildirimi yok.** Saga, Slack'e ping atmaz — `order.confirmed` ve
> `order.cancelled` event'leri RabbitMQ topic exchange'e yayınlanır ve gelecekte
> bağlanacak consumer'lar (e-posta, in-app, analytics) için orada bekler. Slack sadece
> CI/CD deploy bildirimleri için (`deploy.yml` içinde) kullanılır.

## Tasarım Kararları

- **Per-service DB.** Her mikro hizmet kendi şemasına sahip; yabancı anahtar bağımlılığı yok.
- **JWT, gateway'de değil servislerde doğrulanır.** Gateway sadece `Authorization` header'ını
  iletir; her resource server kendi JWT secret'ini doğrular. Bu sayede servis-to-servis
  REST çağrıları (örn. order-service → cart-service) aynı JWT'yi taşıyarak kullanıcı bağlamını
  korur.
- **Saga choreography.** Merkezi orchestrator yok — her servis kendi domain event'ini publish
  ediyor, ilgilendiği event'leri consume ediyor. Bu, deployment ölçeklenebilirliği ve
  sorumluluk ayrımı açısından çok daha temiz.
- **`afterCommit` event publishing.** Order olayları, ana DB transaction commit olmadan
  asla yayınlanmıyor (`TransactionSynchronization`). Bu, aynı RabbitMQ mesajının kaybolan
  veya hayalet olmayan tek bir kopyasının yayınlanmasını sağlar.
- **Idempotency.** Saga consumer'ları, order zaten CONFIRMED/CANCELLED durumdaysa duplicate
  event'leri sessizce yok sayar — at-least-once teslimat altında güvenli.
- **Pluggable AI provider.** `chatbot-service` `MOCK | GROQ | CLAUDE` provider'larını
  `@ConditionalOnProperty` ile seçer; default Groq (ücretsiz).

## Correlation ID

Her HTTP request `X-Correlation-Id` header üretir veya forward eder; `CorrelationIdFilter` MDC'ye
set eder ve `logback` deseninde `[%X{correlationId:-}]` olarak basılır. Saga event'lerinde de
correlationId payload field olarak taşınır — bir `POST /api/orders/checkout` çağrısı 4 servisin
log'unda aynı id ile aranabilir.
