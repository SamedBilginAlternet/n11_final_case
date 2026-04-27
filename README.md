# n11 Final Case — E-Ticaret

Spring Boot 3.3 / Java 21 **7 mikroservis** (auth, product, cart, order, payment,
**chatbot (Groq / Claude)** ve gateway), RabbitMQ üzerinde **choreography saga**, JWT auth,
Iyzico ödeme entegrasyonu, **n11 magenta** temalı React + Vite + Tailwind frontend
(component-driven, mock-data backed) ve **GitHub Actions → DigitalOcean droplet + GHCR**
deploy boru hattı (her deploy'da Slack bildirimi).

| | |
|---|---|
| **Backend** | Spring Boot 3.3.4, Java 21, Spring Cloud Gateway 2023.0.3, JPA + Flyway + PostgreSQL 16 |
| **Mesajlaşma** | RabbitMQ 3.13 — topic exchange, durable queues, JSON message converter |
| **Auth** | JJWT 0.12.6 HS256, BCrypt, gateway-relayed Bearer header |
| **Ödeme** | iyzipay-java 2.0.65 (sandbox/prod toggle); offline `MockPaymentGateway` fallback |
| **AI Asistan** | Pluggable provider — **Groq** (free, OpenAI-compatible, default) / **Anthropic Claude** / **Mock**; ürün katalog grounding ile RAG |
| **Frontend** | React 18, Vite 5, Tailwind 3 (n11 magenta tema), react-router 6, axios, react-hot-toast; floating sticky chatbot |
| **DevOps** | Docker Compose, Jib, GitHub Actions, **DigitalOcean droplet** (SSH deploy), **GHCR** (free image registry), Caddy reverse proxy + auto-TLS, Slack webhook (yalnızca CI/CD deploy bildirimi) |
| **Test** | JUnit 5 + Mockito + Testcontainers (PostgreSQL) |

## İçindekiler

- [Mimari](#mimari)
- [Servisler](#servisler)
- [Saga Akışı](#saga-akışı)
- [Çalıştırma](#çalıştırma)
- [Test](#test)
- [API Dokümantasyonu](#api-dokümantasyonu)
- [CI/CD](#cicd)
- [Deployment](#deployment)
- [Klasör Yapısı](#klasör-yapısı)

> Detaylı dokümantasyon `docs/` altındadır:
> - [`docs/architecture.md`](docs/architecture.md) — mimari diyagram + tasarım kararları
> - [`docs/saga.md`](docs/saga.md) — saga waterfall, compensation, idempotency
> - [`docs/cicd.md`](docs/cicd.md) — GitHub Actions ↔ Jenkins karşılaştırması
> - [`docs/deployment.md`](docs/deployment.md) — DigitalOcean droplet playbook (free-tier friendly, ücretsiz Groq AI ile)

## Mimari

```
browser → Caddy (TLS) → frontend (nginx) → api-gateway → { auth, product, cart, order, payment, chatbot }
                                                  │
                                                  ├─► PostgreSQL   (per-service DB)
                                                  ├─► RabbitMQ     (saga.exchange + saga.exchange.dlx / topic)
                                                  ├─► Redis        (cache: product detail, categories, coupons, campaigns)
                                                  └─► Groq/Claude  (chatbot-service AI)
```

Detaylı diyagram: [`docs/architecture.md`](docs/architecture.md).

## Servisler

| Servis | Port | DB | Görev |
|---|---|---|---|
| **api-gateway** | 8080 | — | Public giriş, JWT relay, aggregated Swagger UI |
| **auth-service** | 8081 | `authdb` | `register`, `login`, `users/me`, JWT issuance |
| **product-service** | 8082 | `productdb` | Pagination + search + categories + `/autocomplete` (header search bar) + ratings |
| **cart-service** | 8083 | `cartdb` | Sepet CRUD, `OrderConfirmed` consumer (sepeti temizler) |
| **order-service** | 8084 | `orderdb` | Checkout, state machine, saga publisher + payment-event consumer |
| **payment-service** | 8085 | `paymentdb` | `OrderCreated` consumer, Iyzico, `Payment*` publisher |
| **chatbot-service** | 8087 | `chatbotdb` | `POST /api/chat` — Groq / Claude provider, oturum geçmişi, ürün katalog grounding |

Her servis kendi `pom.xml`'i, kendi Flyway migration set'i ve kendi DB'si ile bağımsız
olarak deploy edilebilir.

## Saga Akışı

İki choreography saga var (Slack runtime bildirimi yok — Slack sadece CI deploy için):

1. **CheckoutSaga (mutluyolcu)** — `order.created` → ödeme → `payment.succeeded` →
   `order.confirmed` → cart-service sepeti temizler.
2. **CheckoutSaga (compensation)** — ödeme reddedildiğinde `payment.failed` →
   `order.cancelled`; sipariş `CANCELLED` durumuna düşer ve müşteri tekrar deneyebilir.

ASCII waterfall + idempotency notları: [`docs/saga.md`](docs/saga.md).

## Çalıştırma

```bash
cp .env.example .env
docker compose up --build
```

| Servis | URL |
|---|---|
| Frontend | http://localhost:3000 |
| API Gateway | http://localhost:8080 |
| Aggregated Swagger UI | http://localhost:8080/swagger-ui.html |
| RabbitMQ Management | http://localhost:15672 (`guest` / `guest`) |
| PostgreSQL | localhost:5432 (`n11` / `n11pw`) |
| Redis (cache) | localhost:6379 — `redis-cli KEYS 'product:*'` / `'cart:*'` |

`/api/categories` ile sample veriyi doğrulayabilirsiniz; `http/` dizininde IntelliJ /
VS Code REST Client için hazır request collection mevcut (`auth.http`,
`products.http`, `cart-and-checkout.http`).

### Cache (Redis)

`product-service` ve `cart-service` Redis-backed `@Cacheable` ile sıcak okuma yollarını
DB'den çekmiyor. Hit rate hedefi: bir gezinme oturumunda **>%90 cache hit** kategoriler için,
**>%70 hit** ürün detayları için.

**Cache topology** (`*Service/.../config/CacheConfig.java`):

| Service | Cache adı | TTL | Anahtar | Eviction |
|---|---|---|---|---|
| product | `categories`           | 1h  | `'all'` | TTL only (admin değiştirirse 1 saat içinde) |
| product | `products:byId`        | 5m  | `productId` | TTL only |
| product | `products:bySlug`      | 5m  | `slug`      | TTL only |
| product | `products:autocomplete`| 1m  | `q.lower():capped-limit` | TTL only |
| cart    | `coupons:byCode`       | 60s | `code.toUpperCase()` | TTL **+ saga `reserveOne`/`releaseOne` evict** |
| cart    | `campaigns:active`     | 60s | `'all'` | TTL only |

**Production-grade detaylar**:
- **Serialization**: anahtarlar `StringRedisSerializer` (Redis-CLI'de okunabilir),
  değerler `GenericJackson2JsonRedisSerializer` JSON. JDK binary serialization
  yerine JSON çünkü class rename'lerine dirençli + log'da görülebilir.
- **Polymorphic type validator**: Jackson default-typing açıkken sadece
  `com.n11.{product|cart}.*` + `java.util` / `time` / `math` paketleri whitelist
  — gadget chain'lere kapalı.
- **Saga eviction**: `CouponRepository.reserveOne` / `releaseOne` üzerinde
  `@CacheEvict` — sepet quote'undan order checkout'a geçişte saga sayacı
  bumple, cache anında silinir. Stale "kupon hâlâ var" gösterimi yok.
- **Search results cached değil**: filter cardinality (categoryId × q × page × sort)
  patlıyor, hit rate düşük olur, memory israfı.

**Doğrulama**:

```bash
# Önce kategorileri çek (cache miss → DB)
curl -s http://localhost:8080/api/categories > /dev/null

# Redis'te ne var?
docker exec n11-final-case-redis-1 redis-cli KEYS 'product:*'
# product:categories::all
# product:products:byId::1   (eğer ürün detayı çağırıldıysa)

# Cache'in gerçekten devrede olduğunu time'la doğrula:
for i in 1 2 3; do
  time curl -s -o /dev/null http://localhost:8080/api/products/slug/iphone-15
done
# 1. çağrı: ~50-100ms (DB)
# 2. ve 3. çağrı: ~5-15ms (Redis hit)
```

`docker compose down -v` Redis volume'unu da siler — yeni `up` cold cache ile başlar.

### Iyzico

Varsayılan olarak `IYZICO_ENABLED=false` ile **MockPaymentGateway** çalışır — hiçbir dış
servise istek gitmez, başarılı bir `MOCK-XXXXX` referansı üretir.

Sandbox testi için:

```bash
IYZICO_ENABLED=true \
IYZICO_API_KEY=sandbox-... \
IYZICO_SECRET_KEY=sandbox-... \
IYZICO_BASE_URL=https://sandbox-api.iyzipay.com \
docker compose up --build
```

### Sosyal Giriş (Google / GitHub)

Varsayılan olarak iki provider de **kapalı** — sadece e-posta + şifre login çalışır. Açmak için
provider başına client-id/secret ekleyin; Spring Security yalnızca dolu olanları aktive eder.

**1. OAuth uygulamalarını oluşturun**

| Provider | Konsol | Authorized redirect URI |
|---|---|---|
| Google | [console.cloud.google.com](https://console.cloud.google.com) → APIs & Services → Credentials → OAuth 2.0 Client | `${PUBLIC_HOST}/api/auth/oauth2/callback/google` |
| GitHub | Settings → Developer settings → OAuth Apps → New OAuth App | `${PUBLIC_HOST}/api/auth/oauth2/callback/github` |

`PUBLIC_HOST` değişkeni:
- Local docker-compose: `http://localhost:8080`
- DigitalOcean droplet: `https://yourdomain.com` (veya HTTP üzerinde `http://<ip>`)

**2. `.env` dosyasına ekleyin**

```bash
FRONTEND_BASE_URL=http://localhost:3000      # prod'da https://yourdomain.com
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
GITHUB_CLIENT_ID=...
GITHUB_CLIENT_SECRET=...
docker compose up --build
```

**3. Akış**

1. `/login` sayfasında "Google ile Giriş" butonu → tarayıcı `/api/auth/oauth2/authorize/google`'a navigate olur
2. Spring Security PKCE+state ile Google'a yönlendirir
3. Google → `/api/auth/oauth2/callback/google` → kullanıcı (provider, subject) ile var ise eşlenir, yoksa e-posta'ya göre link'lenir, yoksa yeni `password_hash IS NULL` user yaratılır
4. Auth-service kendi mevcut HS256 JWT'sini issue eder
5. Tarayıcı `${FRONTEND_BASE_URL}/auth/callback#token=...`'a yönlendirilir (token URL fragment'ında — server log'larında değil)
6. Frontend `/auth/callback` route'u token'ı yakalar → `/api/users/me` ile kullanıcıyı çeker → ana sayfaya gider

GitHub'ın `email` alanı public değilse `user:email` scope'u ile `/user/emails`'tan birincil verified e-posta otomatik çekilir (`GitHubEmailAwareUserService`).

### Kampanya & Kupon Motoru

`cart-service` içinde **Strategy + Chain** desenleri ile kurulu, sepete eklenen
her ürün veya kupon değişikliğinde sepet özetini yeniden hesaplayan bir
indirim motoru var. Kalıp net şu şekilde çalışır:

```
Cart  →  DiscountEngine  →  Quote
                  │
                  ├─ subtotal = Σ unitPrice × qty
                  ├─ Coupon (cart.coupon_code → DB lookup)
                  ├─ Active Campaigns (DB, validFrom/Until içinde)
                  │
                  └─ for each DiscountStrategy in priority order:
                        strategy.evaluate(QuoteContext) → AppliedDiscount?

  totalDiscount = Σ AppliedDiscount.amount
  total         = max(subtotal − totalDiscount, 0)
```

Her strateji `@Component` — Spring otomatik toplar, `priority()` küçüğe göre
sıralar, sırayla çağırır. Yeni bir kampanya tipi eklemek tek dosyalık bir
değişiklik (yeni `DiscountStrategy` impl'i + DB tarafında yeni
`CampaignType`).

**Mevcut 3 strateji**

| Priority | Strategy | Açıklama |
|---|---|---|
| 20 | `BuyXPayYStrategy` | "X al Y öde" — kart-bazlı; sepetteki birimler ucuza göre sıralanır, her tam X grubunda en ucuz (X−Y) birim hediye olur. 8 ürün varsa iki tam grup → en ucuz 2 birim hediye. |
| 30 | `PercentOffCartStrategy` | "Sepette %X indirim" — `min_cart_total` eşiğini geçen en yüksek priority kampanya seçilir; iki yüzde kampanyası üst üste binmez. |
| 50 | `CouponCodeStrategy` | Kullanıcının `KUPON100` gibi girdiği kod. `FIXED` (mutlak TL) veya `PERCENT`. FIXED tutar subtotal'i geçerse subtotal'e clamp olur (negatif total yok). |

**Discount stacking**: stratejiler ek (additive) — her biri *orijinal*
subtotal'e karşı hesaplanır, sonuçlar toplanır. Sıra final'i etkilemez.
Bu özellikle compounding bug'ları engeller (5% + 10% = 15%, 14.5% değil).

**Coupon kalıcı state'i + saga rezervasyonu**

Kupon kullanım sayacı `coupons.redemptions` ile tutuluyor; cap'e ulaşınca
strateji sessizce drop ediyor. Race-safe rezervasyon iki aşamada:

1. Kullanıcı `/api/cart/coupon { code }` çağırır → `cart.coupon_code` set
   olur. **DB sayacı henüz değişmez** — terk edilen sepetler kuponu yakmaz.
2. `/api/orders/checkout` saga'sını başlatır:
   - order-service `OrderCreated` event'ine `couponCode`'u koyar
   - cart-service `CART_ORDER_CREATED_COUPON` queue'sünden tüketir
   - **Atomik UPDATE**: `redemptions = redemptions + 1 WHERE redemptions < max_redemptions`
   - `coupon_redemptions` tablosuna `(coupon_id, order_id)` UNIQUE constraint'li satır insert edilir
3. Ödeme reddedilirse:
   - order-service `OrderCancelled` event'ine `couponCode`'u koyar (compensation key olarak)
   - cart-service `CART_ORDER_CANCELLED_COUPON` queue'sünden tüketir
   - Redemption row silinir, `redemptions` decrement (zerolanır, negatif gitmez)

Bu tasarım iki yarış senaryosunu kapatır:
- **At-least-once duplicate delivery**: aynı `OrderCreated` iki kez gelse, ikincisi `(coupon_id, order_id)` UNIQUE'a takılır, sayaç değiştirmez.
- **Concurrent checkout for the last slot**: iki paralel UPDATE → biri 1 row affected, diğeri 0 row affected; 0 alan log'lar ve siparişi tam fiyatla devam ettirir.

**Compensation idempotent**: aynı `OrderCancelled` iki kez gelse, ikinci pass `findByOrderId` dönmez ve no-op olur.

**Demo data** — `V4__seed_pricing.sql` 3 kampanya + 3 kupon insert eder:

| Code | Tip | Detay |
|---|---|---|
| `PCT5_OVER_500` | %5 | Sepette 500 TL ve üzeri |
| `PCT10_OVER_2K` | %10 | Sepette 2.000 TL ve üzeri (priority 31, PCT5 ile birlikte değil) |
| `BUY4_PAY3` | 4 al 3 öde | Sepet geneli, kategori filtresiz |
| `KUPON100` | FIXED 100 TL | Min sepet 300 TL, 1.000 kullanım |
| `KUPON10` | %10 | Cap-free |
| `YENI50` | FIXED 50 TL | **1 kullanım** — race senaryosu için |

**Hızlı doğrulama**:

```bash
# JWT al
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@n11.local","password":"alice123"}' | jq -r .accessToken)

# 4 ürün ekle, BUY4_PAY3 + PCT5 trigger olmalı
for pid in 1 2 3 4; do
  curl -s -X POST localhost:8080/api/cart/items \
    -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    -d "{\"productId\":$pid,\"quantity\":1}"
done

# Kupon ekle
curl -s -X POST localhost:8080/api/cart/coupon \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"code":"KUPON100"}' | jq '.discounts, .totalDiscount, .totalAmount'
```

Kod tabanı pointers:
- Strategy SPI: `backend/cart-service/src/main/java/com/n11/cart/pricing/DiscountStrategy.java`
- Engine: `…/pricing/DiscountEngine.java`
- 3 implementasyon: `…/pricing/strategy/`
- Saga listener: `…/messaging/CouponSagaListener.java`
- Şema: `…/db/migration/V3__pricing_engine.sql`, `V4__seed_pricing.sql`, `V5__coupon_redemptions.sql`

### Chatbot AI provider

Varsayılan `CHATBOT_PROVIDER=MOCK` (anahtarsız çalışır). Üç seçenek:

| Provider | Maliyet | Konfigürasyon |
|---|---|---|
| `MOCK` | ücretsiz | yok — Türkçe template yanıtlar |
| `GROQ` | **ücretsiz** rate-limited | [console.groq.com](https://console.groq.com) → API key → `GROQ_API_KEY=...` |
| `CLAUDE` | ücretli | Anthropic console → `ANTHROPIC_API_KEY=...` |

```bash
# Free Groq (önerilen)
CHATBOT_PROVIDER=GROQ \
GROQ_API_KEY=gsk_... \
docker compose up --build
```

## Test

```bash
# Tüm modüller (parent POM)
mvn -f backend/pom.xml verify

# Tek servis
mvn -f backend/pom.xml -pl auth-service -am test

# Frontend
cd frontend && npm test
```

Backend test stratejisi:
- **Unit**: domain (state machine), service layer (mock'lu).
- **Integration**: `@SpringBootTest` + Testcontainers PostgreSQL (auth-service, product-service).

## API Dokümantasyonu

Her servis kendi `/v3/api-docs` ve `/swagger-ui.html` endpoint'ini açar. Gateway
**aggregated Swagger UI** sunar:

- http://localhost:8080/swagger-ui.html → drop-down'dan servis seç (`auth`, `products`, `cart`,
  `orders`, `payments`).

JWT'yi kullanmak için Swagger'da "Authorize" → `Bearer <token>` olarak yapıştır.

## CI/CD

| Workflow | Tetikleyici | Görev |
|---|---|---|
| [`backend.yml`](.github/workflows/backend.yml) | `push`/`pr` (backend changes) | 8 modül için matrix `mvn verify` |
| [`frontend.yml`](.github/workflows/frontend.yml) | `push`/`pr` (frontend changes) | npm ci → lint → vitest → vite build → dist artifact |
| [`deploy.yml`](.github/workflows/deploy.yml) | `push main` veya `v*` tag | Jib + Docker → GHCR; SSH ile droplet'e deploy; **her run sonunda Slack bildirimi** |

Jenkins ile karşılaştırma: [`docs/cicd.md`](docs/cicd.md) — aynı pipeline'ın `Jenkinsfile`
karşılığı + adım adım kavram eşleştirmesi.

## Deployment

[`docs/deployment.md`](docs/deployment.md) — DigitalOcean droplet üzerinde **free-tier
friendly** deploy boru hattı:
- **GHCR** (free) → backend Jib + frontend Docker images
- **Caddy** ile auto-TLS (Let's Encrypt) reverse proxy
- `appleboy/scp-action` + `appleboy/ssh-action` ile droplet'e push
- `infra/digitalocean/setup-droplet.sh` Ubuntu droplet'i tek komutla bootstrap eder
- main'e her push (veya `v*` tag) → image build → SSH deploy → **Slack bildirimi**
- DigitalOcean yeni hesap kredisi (60 gün $200) sayesinde ilk dönem ücretsiz, sonrası ~$4-6/ay

Slack bildirimi her workflow sonunda tetiklenir (`secrets.SLACK_WEBHOOK_URL`).

## Klasör Yapısı

```
n11_final_case/
├── backend/                    Maven multi-module parent
│   ├── common/                 paylaşılan event DTO'ları, saga topology, JwtParser, CorrelationId filter
│   ├── api-gateway/            Spring Cloud Gateway
│   ├── auth-service/           kayıt + login + JWT
│   ├── product-service/        katalog + pagination + autocomplete + ratings
│   ├── cart-service/           sepet CRUD + saga consumer
│   ├── order-service/          checkout + saga publisher + payment-event consumer
│   ├── payment-service/        Iyzico + saga participant
│   ├── chatbot-service/        Groq / Claude / Mock — Türkçe AI asistan
│   └── Dockerfile              tüm servisler için tek multi-stage build (ARG SERVICE)
├── frontend/                   React + Vite + Tailwind + React Router 6
│   ├── src/{api,components,pages,state,utils,data}
│   └── Dockerfile + nginx.conf
├── infra/
│   ├── postgres-init/          local docker-compose multi-DB initdb script
│   └── digitalocean/           droplet bootstrap, prod compose, Caddyfile, .env.example
├── http/                       IntelliJ/VSCode REST Client requests
├── docs/                       architecture, saga, cicd, deployment
├── .github/workflows/          backend.yml, frontend.yml, deploy.yml (Slack notify)
├── docker-compose.yml          tek komutla full stack (yerel geliştirme)
└── .env.example
```

---

> Bu repo n11 TalentHub bootcamp final case projesi olarak yazılmıştır.
> Choreography saga + atomic commits + clean code önceliklidir.
