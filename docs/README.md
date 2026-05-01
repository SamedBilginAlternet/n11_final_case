# Documentation Index

Bu klasör projenin **niye böyle** tasarlandığını anlatan dokümanları içerir. Kod ne yapıyor —
git grep / Swagger gösterir. Burada **alternatifler ne, niye reddedildi, hangi kuralları
korumak zorundasın** sorularına yanıt var.

## Üç Seviye Doküman

```
┌─────────────────────────────────────────────────────────────┐
│  1. HIGH-LEVEL — "Bu sistem nedir, ne yapar?"               │
│     architecture.md, developer-guide.md                     │
└─────────────────────────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────────────────────┐
│  2. TOPICAL — "Şu konsept (cache, mesajlaşma) nasıl çalışır?"│
│     messaging.md, security.md, caching.md, search.md,       │
│     recommendations.md, observability.md, saga.md           │
└─────────────────────────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────────────────────┐
│  3. PER-SERVICE — "Şu servis ne, kodu nerede?"              │
│     services/auth-service.md, services/cart-service.md, ... │
└─────────────────────────────────────────────────────────────┘
```

## Yeni Mi Başlıyorsun?

```
1. ../README.md            (proje başlangıç + servis listesi)
2. architecture.md         (yüksek seviye diyagram + kararlar)
3. developer-guide.md      (felsefe + tüm "neden" ler bir arada)
4. services/README.md      (per-service okuma sırası önerileri)
5. saga.md                 (akışın somut bir örneği)
```

## Topic Index

| Konu | Doküman | Bu konuda |
|---|---|---|
| Mimari | [`architecture.md`](architecture.md) | Yüksek seviye diyagram, servis boundary'leri |
| Felsefe + kararlar | [`developer-guide.md`](developer-guide.md) | "Sade > akıllı", per-service-DB, JWT, vb. |
| Saga akışı | [`saga.md`](saga.md) | ASCII waterfall, idempotency, compensation |
| **Auth akışları** | [`auth-flows.md`](auth-flows.md) | **3 login yolu (email, Google, telefon-OTP) sequence + onboarding + checkout email gate** |
| **Secrets yönetimi** | [`secrets-management.md`](secrets-management.md) | **Infisical + sync-env.sh + machine identity + rotation** |
| RabbitMQ | [`messaging.md`](messaging.md) | Exchange topology, DLX, routing key, idempotency, publish-after-commit |
| Güvenlik | [`security.md`](security.md) | JWT HS256, refresh rotation + reuse detection, role-based access, rate limit |
| Cache | [`caching.md`](caching.md) | Redis namespace, TTL strategy, eviction patterns, polymorphic type validator |
| Search | [`search.md`](search.md) | PostgreSQL FTS, tsvector, faceted filter |
| AI Öneriler | [`recommendations.md`](recommendations.md) | Co-purchase + Groq re-rank pipeline |
| Observability | [`observability.md`](observability.md) | Correlation ID, Jaeger tracing, Micrometer metrics, **Sentry error tracking + replay + sourcemap upload** |
| CI/CD | [`cicd.md`](cicd.md) | GitHub Actions ↔ Jenkins karşılaştırması |
| Deployment | [`deployment.md`](deployment.md) | DigitalOcean droplet + GHCR |

## Per-Service Deep-Dives

[`services/README.md`](services/README.md) — index. Her servis için ayrı doküman:

- [`services/api-gateway.md`](services/api-gateway.md)
- [`services/auth-service.md`](services/auth-service.md)
- [`services/product-service.md`](services/product-service.md)
- [`services/cart-service.md`](services/cart-service.md)
- [`services/order-service.md`](services/order-service.md)
- [`services/payment-service.md`](services/payment-service.md)
- [`services/notification-service.md`](services/notification-service.md)
- [`services/chatbot-service.md`](services/chatbot-service.md)
- [`services/common.md`](services/common.md)
- [`services/frontend.md`](services/frontend.md)
- [`services/frontend-admin.md`](services/frontend-admin.md)

## "Bu Hata Oldu, Nereden Başlayayım?"

| Belirti | İlgili doküman |
|---|---|
| 401 / 403 / oturum bozuluyor | [`security.md`](security.md) |
| Telefon login çalışmıyor / OTP gelmiyor | [`auth-flows.md`](auth-flows.md), [`secrets-management.md`](secrets-management.md) |
| `.env` değişti ama prod'a yansımıyor | [`secrets-management.md`](secrets-management.md) |
| Mail gitmedi / RabbitMQ mesajı kayıp | [`messaging.md`](messaging.md), [`services/notification-service.md`](services/notification-service.md) |
| Cache stale veri dönüyor | [`caching.md`](caching.md) |
| Search yanlış sonuç | [`search.md`](search.md) |
| AI öneri boş veya saçma | [`recommendations.md`](recommendations.md) |
| Slow request / latency | [`observability.md`](observability.md) — Jaeger UI |
| Saga complete olmuyor | [`saga.md`](saga.md) — correlationId grep |
| CI fail | [`cicd.md`](cicd.md) |

## "Bunu Ekleyeceğim, Nereye?"

| Eklenecek | Önce oku |
|---|---|
| Yeni saga event | [`messaging.md` §11](messaging.md#11-yeni-event-eklemek--adım-listesi) |
| Yeni admin endpoint | [`security.md`](security.md) — `@PreAuthorize` + `@EnableMethodSecurity` |
| Yeni cache name | [`caching.md`](caching.md) — eviction stratejileri |
| Yeni mail tipi | [`services/notification-service.md`](services/notification-service.md) |
| Yeni filter veya sort | [`search.md`](search.md) |
| Yeni mikroservis | [`services/common.md`](services/common.md) → [`services/api-gateway.md`](services/api-gateway.md) |
| Frontend yeni sayfa | [`services/frontend.md`](services/frontend.md) veya [`services/frontend-admin.md`](services/frontend-admin.md) |
