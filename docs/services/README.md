# Per-Service Deep-Dive Guides

Her servis için ayrı detaylı doküman. Niye böyle tasarlandı, alternatifler ne, kod örnekleri,
deliberate omissions.

## Backend Services

| Servis | Doküman | Port | DB |
|---|---|---|---|
| API gateway | [`api-gateway.md`](api-gateway.md) | 8080 | — |
| Auth | [`auth-service.md`](auth-service.md) | 8081 | `authdb` |
| Product | [`product-service.md`](product-service.md) | 8082 | `productdb` |
| Cart | [`cart-service.md`](cart-service.md) | 8083 | `cartdb` |
| Order | [`order-service.md`](order-service.md) | 8084 | `orderdb` |
| Payment | [`payment-service.md`](payment-service.md) | 8085 | `paymentdb` |
| Notification | [`notification-service.md`](notification-service.md) | 8086 | `notificationdb` |
| Chatbot | [`chatbot-service.md`](chatbot-service.md) | 8087 | `chatbotdb` |
| Common (shared lib) | [`common.md`](common.md) | — | — |

## Frontend Apps

| App | Doküman | Port |
|---|---|---|
| Storefront (public) | [`frontend.md`](frontend.md) | 3000 |
| Admin panel | [`frontend-admin.md`](frontend-admin.md) | 3001 |

## Topical Guides (`../`)

Cross-cutting konseptler — birden fazla servisi etkileyen kararlar:

- [`messaging.md`](../messaging.md) — RabbitMQ topology, DLX, idempotency
- [`security.md`](../security.md) — JWT, refresh rotation, role-based access, rate limit
- [`caching.md`](../caching.md) — Redis namespace, TTL, eviction
- [`search.md`](../search.md) — PostgreSQL FTS, faceted filter
- [`recommendations.md`](../recommendations.md) — Co-purchase + Groq pipeline
- [`observability.md`](../observability.md) — Logs, traces, metrics
- [`saga.md`](../saga.md) — Choreography saga akışı
- [`architecture.md`](../architecture.md) — Yüksek seviye mimari diyagram
- [`developer-guide.md`](../developer-guide.md) — Felsefe + mimari kararlar (yüksek seviye)

## Okuma Sırası Önerileri

### "Bu projede yeniyim, ne yapayım?"

1. [`../architecture.md`](../architecture.md) — Genel resim.
2. [`../developer-guide.md`](../developer-guide.md) — Tasarım felsefesi.
3. [`common.md`](common.md) — Paylaşılan altyapı.
4. [`api-gateway.md`](api-gateway.md) — Trafik nasıl içeri girer.
5. [`auth-service.md`](auth-service.md) — Identity nasıl çalışır.
6. Saga zinciri sırası: [`order-service.md`](order-service.md) → [`payment-service.md`](payment-service.md) → [`cart-service.md`](cart-service.md) → [`notification-service.md`](notification-service.md).
7. Read-heavy yan kollar: [`product-service.md`](product-service.md), [`chatbot-service.md`](chatbot-service.md).
8. Frontend: [`frontend.md`](frontend.md), [`frontend-admin.md`](frontend-admin.md).

### "Bu özelliği eklemek istiyorum"

| Özellik | Önce oku |
|---|---|
| Yeni saga event | [`../messaging.md`](../messaging.md) — adım listesi var |
| Yeni admin endpoint | [`../security.md`](../security.md) — `@PreAuthorize` + `@EnableMethodSecurity` trap'i |
| Yeni cache | [`../caching.md`](../caching.md) — eviction patternleri |
| Yeni search filter | [`../search.md`](../search.md) — dynamic predicate uzatması |
| Yeni mail tipi | [`notification-service.md`](notification-service.md) — listener + template + idempotency |
| Yeni mikroservis | [`common.md`](common.md) → [`api-gateway.md`](api-gateway.md) → mevcut bir servisi template olarak kullan |

### "Şu hata oldu, nereden başlayayım?"

| Belirti | Bak |
|---|---|
| 401 / 403 | [`../security.md`](../security.md) |
| Mesaj kayıyor | [`../messaging.md`](../messaging.md), `.dlq` queue |
| Cache stale data | [`../caching.md`](../caching.md), eviction |
| Search yanlış sonuç | [`../search.md`](../search.md), Turkish stemmer + unaccent |
| Mail gitmedi | [`notification-service.md`](notification-service.md), correlationId log grep |
| Slow request | [`../observability.md`](../observability.md), Jaeger UI |
