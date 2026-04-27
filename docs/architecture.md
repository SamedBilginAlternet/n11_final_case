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
    └────────┬────────┘    └──────────┬───────────┘    └────────┬────────┘    └────────┬─────────┘
             │                        │                         │                       │
             │                        └─── REST ◄──────────────┘                       │
             │                                                                          │
             │                                         ┌────────────────────────────────┘
             │                                         ▼
             │                              ┌──────────────────────┐
             │                              │  payment-service     │  :8085
             │                              │     (paymentdb)      │
             │                              │  Iyzico integration  │
             │                              └──────────┬───────────┘
             │                                         │
             ▼                                         ▼
                ┌─────────────────────────────────────────────────────────┐
                │                 RabbitMQ — saga.exchange                │  :5672 / :15672
                │                  (topic, durable)                       │
                └────────┬──────────────────────────────────┬─────────────┘
                         │                                  │
                         ▼                                  ▼
                ┌─────────────────────┐         ┌────────────────────────┐
                │ notification-service│         │       Slack            │
                │       :8086         │ ───────►│   incoming webhook     │
                └─────────────────────┘         └────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                       PostgreSQL 16 (per-service DB)   :5432            │
└─────────────────────────────────────────────────────────────────────────┘
```

## Servisler

| Servis | Port | DB | Sorumluluk |
|---|---|---|---|
| api-gateway | 8080 | — | Public giriş, route table, JWT relay (Authorization header passthrough), aggregated Swagger UI |
| auth-service | 8081 | authdb | `register`, `login`, `users/me`, JWT issuance ve `UserRegistered` saga publisher |
| product-service | 8082 | productdb | Pagination + search + category listing |
| cart-service | 8083 | cartdb | Sepet CRUD, `OrderConfirmed` consumer (sepeti boşaltma) |
| order-service | 8084 | orderdb | Checkout, sipariş yaşam döngüsü, `OrderCreated` publisher, payment event consumer, `OrderConfirmed/OrderCancelled` publisher |
| payment-service | 8085 | paymentdb | `OrderCreated` consumer, Iyzico integration, `PaymentSucceeded/PaymentFailed` publisher |
| notification-service | 8086 | — | Saga olaylarını fanout queue ile dinler, Slack webhook'a yazar |

## Tasarım Kararları

- **Per-service DB.** Her mikro hizmet kendi şemasına sahip; yabancı anahtar bağımlılığı yok.
- **JWT, gateway'de değil servislerde doğrulanır.** Gateway sadece `Authorization` header'ını
  iletir; her resource server kendi JWT secret'ini doğrular. Bu sayede servis-to-servis
  REST çağrıları (örn. order-service → cart-service) aynı JWT'yi taşıyarak kullanıcı bağlamını
  korur.
- **Saga choreography.** Merkezi orchestrator yok — her servis kendi domain event'ini publish
  ediyor, ilgilendiği event'leri consume ediyor. Bu, deployment ölçeklenebilirliği ve
  sorumluluk ayrımı açısından çok daha temiz.
- **`afterCommit` event publishing.** Order ve User olayları, ana DB transaction commit
  olmadan asla yayınlanmıyor (`TransactionSynchronization`). Bu, aynı RabbitMQ mesajının
  kaybolan veya hayalet olmayan tek bir kopyasının yayınlanmasını sağlar.
- **Idempotency.** Saga consumer'ları, order zaten CONFIRMED/CANCELLED durumdaysa duplicate
  event'leri sessizce yok sayar — at-least-once teslimat altında güvenli.

## Correlation ID

Her HTTP request `X-Correlation-Id` header üretir veya forward eder; `CorrelationIdFilter` MDC'ye
set eder ve `logback` deseninde `[%X{correlationId:-}]` olarak basılır. Saga event'lerinde de
correlationId payload field olarak taşınır — yani bir `POST /api/orders/checkout` çağrısı 8 servisin
log'unda aynı id ile aranabilir.
