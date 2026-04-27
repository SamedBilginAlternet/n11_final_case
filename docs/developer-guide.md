# Developer Guide

Bu doküman bu projedeki **kararların gerekçelerini** anlatır. Kod ne yapıyor — `git grep` veya
Swagger gösterir. Bu doküman **niye böyle yapılıyor**, **alternatifler ne ve niye reddedildi**,
ve bir özelliği değiştirirken **hangi kuralları korumak zorundasın**.

Yeni başlayan biri için sıralı okuma: bölüm 1 → 2 → 3. Belirli bir servise odaklanan biri için:
[Servisler](#3-servisler-deep-dive) bölümünden ilgili alt başlığı.

---

## 0. İçindekiler

1. [Tasarım Felsefesi](#1-tasarım-felsefesi)
2. [Mimari Kararlar](#2-mimari-kararlar) — saga, per-service DB, JWT, cache, security
3. [Servisler Deep Dive](#3-servisler-deep-dive)
4. [Cross-Cutting Concerns](#4-cross-cutting-concerns) — common modülü, correlation ID, error handling
5. [Test Stratejisi](#5-test-stratejisi)
6. [Bilinçli Olarak Yapmadıklarımız](#6-bilinçli-olarak-yapmadıklarımız)
7. [Bir Şeyi Değiştirirken](#7-bir-şeyi-değiştirirken-okuma-listesi)

---

## 1. Tasarım Felsefesi

Üç düstura oturuyor.

### "Sade > akıllı"

Her servis tek bir şey yapar. State machine basit. Saga choreography (orchestrator yok).
JWT HS256 (RS256+JWKS değil). Redis tek node (cluster değil). PostgreSQL per-service.

Bunların hepsi **bilinçli regression**: gerçek bir bootcamp / case study scope'unda
"production-grade" görünmek için Kubernetes operator + Kafka + gRPC + OpenTelemetry collector
gibi katmanlar koymak komik durur. Tutarlı, anlaşılır, **bir mühendisin tek başına bakım
yapabileceği** bir sistem hedefimiz.

### "Defaults work, env overrides scale"

Yeni clone → `cp .env.example .env && docker compose up --build` → tüm sistem çalışır.
Hiçbir secret'a, üçüncü-parti hesaba, kuruluma gerek yok.

- Iyzico → `IYZICO_ENABLED=false` ile MockPaymentGateway
- Chatbot → `CHATBOT_PROVIDER=MOCK` ile template yanıtlar
- OAuth2 → tüm provider client-id'leri boşsa SecurityConfig oauth2Login'i skip
- Slack → webhook env yoksa CI deploy notify step'i no-op

Production'a geçişte sadece env'ler doldurulur, kod değişmez.

### "Never trust the client, always re-quote on the server"

Cart total, kupon değeri, ürün fiyatı, kullanıcı kimliği — hepsi **server-side hesaplanır**.

- `POST /api/orders/checkout` body'si boş — userId JWT'den, items + total cart-service'in DB'sinden gelir.
- Cart quantity client'tan gelir ama `@Min(1)` + stock check ile validate edilir.
- Kupon kodu client'tan gelir ama `Coupon.isValidAt()` server'da expiry/cap kontrol eder.
- Payment.userId == auth.userId — Bob, Alice'in payment'ını okuyamaz (404 döner, 403 değil).

Kullanıcının edit edebileceği tek state: gönderdiği request body'sindeki `productId`,
`quantity`, `couponCode`, `email`, `password`. Geri kalan her field **JWT'den
veya DB'den** gelir.

---

## 2. Mimari Kararlar

### 2.1 Choreography saga (orchestrator yok)

Sipariş hayat döngüsü 4 servisi içeriyor: **cart → order → payment → cart (clear)**.

**Seçilen**: choreography. Her servis kendi domain event'ini publish eder (`order.created`,
`payment.succeeded`, ...). Diğer servisler ilgilendiklerini bağımsız queue'lardan tüketir.
Merkezi bir "saga manager" yok.

**Reddedilen alternatif**: orchestration (Camunda, Spring Statemachine). Bir merkezi koordinatör
adımları sırayla yönetir.

**Neden choreography**:
- Servisler **bağımsız deploy edilebilir** — order-service'i restart etmek payment'ı etkilemez.
- Eklenecek consumer'lar (notification-service, analytics) **mevcut servislere dokunmadan**
  yeni queue binding'i ile dinler.
- 4 step için orchestrator bürokrasi.
- Distributed transaction yok zaten — eventual consistency'yi açıkça kabul ettik.

**Trade-off**:
- Akışı izlemek için correlationId loglarına bakmak gerekir (orchestration'da tek state machine var).
- Her consumer **idempotent** olmak zorunda — at-least-once delivery'de duplicate event normal.

İdempotency garantileri:
- `OrderStatus.transitionTo()` state machine — geçersiz transition `IllegalStateException`.
- `coupon_redemptions` tablosunda `(coupon_id, order_id)` UNIQUE — duplicate reservation hit'te no-op.
- Postgres'te atomik `UPDATE coupons SET redemptions=redemptions+1 WHERE redemptions<max` — race-safe sayaç.

Detay: [`docs/saga.md`](saga.md).

### 2.2 Per-service database

7 servisin her birinin kendi PostgreSQL şeması var (`authdb`, `productdb`, `cartdb`, ...).
Foreign key cross-service yok. Service A'nın B'nin tablosuna doğrudan SQL ile erişimi yok.

**Neden**:
- **Bounded context** sınırı net. `Order.userId` sadece bir Long — auth-service silindiğinde
  order-service çalışmaya devam eder, sadece e-mail çözümlemesi başarısız olur.
- Ölçeklendirme: yarın product-service'in ihtiyaç duyduğu read replica auth-service'i etkilemez.
- Migration izolasyonu: cart-service `V5__coupon_redemptions.sql` uygularken order-service
  Flyway state'i bağımsız.

**Trade-off**:
- Cross-service join'ler REST üzerinden yapılır (örn. cart-service'in product detayına ihtiyacı
  ProductClient ile). Network hop var, transaction sınırı yok.
- Order'ın items[] alanı, OrderItem entity'sinde **product snapshot** olarak tutulur (`product_name`,
  `unit_price`). Product fiyatı sonradan değişse bile order'daki fiyat kararından dönülmez.

### 2.3 JWT — HS256, gateway'de değil servislerde doğrulanır

**Seçilen**: HS256 imzalı JWT, paylaşılan `JWT_SECRET` env. Her servis common modülündeki
`JwtAuthenticationFilter` ile **kendi başına** doğrular.

**Reddedilen**:
- RS256 + JWKS endpoint: enterprise pattern ama key management overhead var.
- Session cookie: stateless arayüz değil, sticky session veya Redis session store gerek.
- Gateway-only validation: gateway downed ise tüm sistem patlar; ayrıca service-to-service
  REST çağrıları (order → cart) JWT'yi forward edebiliyor — bağımsız doğrulama bunu doğal kılar.

**Trade-off**:
- `JWT_SECRET` rotasyonu tüm servisleri restart gerektirir (RS256'da public-key cache invalidate
  yeterliydi).
- 7 servis aynı secret'ı bilmek zorunda — env var sızıntısı tüm token'ları compromise eder.

Common'daki `JwtParser` (parse-only) ve auth-service'teki `JwtTokenProvider` (issue + parse)
bilinçli olarak ayrı sınıflar — issuer'ın token'ı imzalama yetkisi var, consumer'ların yok.

### 2.4 Cache strategy (Redis)

**Cache'lenenler**:
- `categories` (1h TTL) — admin değişikliği nadir.
- `products:byId` / `products:bySlug` (5m) — fiyat / stok drift'i kabul edilebilir.
- `products:autocomplete` (1m) — feels live, typeahead RTT azaltır.
- `coupons:byCode` (60s + saga `@CacheEvict`).
- `campaigns:active` (60s).

**Cache'lenmeyenler**:
- Search results (`/api/products?categoryId=&q=&page=&sort=`) — filter cardinality patlar.
  `categoryId × q × page × size × sort` kombinasyonları → hit rate çok düşük, memory israfı.
- Cart / Order — kullanıcı bazlı, sürekli yazma. Cache'lemenin maliyeti faydadan büyük.
- User profile (`/users/me`) — auth-service çağrılır ama JWT zaten kullanıcı bilgisinin çoğunu
  taşıyor. UI direkt JWT'den okuyabilir.

**Cache invalidation stratejisi**:
- TTL → admin tarafından mutasyon olmadan değişen state için yeterli.
- `@CacheEvict` → saga `reserveOne` / `releaseOne` gibi **uygulama-içi** mutasyonlarda anında
  invalidate. Stale "kupon hâlâ kullanılabilir" gösterimi yok.

Cache write'lar `RedisCacheManager` üzerinden idempotent — concurrent write'lar last-writer-wins.
Coupon counter race'i cache değil DB'de çözülür: atomik `UPDATE … WHERE redemptions < max`.

### 2.5 Security defense-in-depth

Her servis kendi `SecurityConfig`'ine sahip — implicit "default Spring Security" yok.

| Servis | Policy | Niye |
|---|---|---|
| auth-service | JWT (issuer kendisi) + login rate-limit | Issuer; brute-force koruması |
| product-service | **Explicit** `permitAll` | Public catalog, defense-in-depth (yarın bir dep deny default getirirse fark eder) |
| cart-service | JWT zorunlu | Kullanıcı kendi sepetine erişir |
| order-service | JWT zorunlu | Kullanıcı kendi siparişine (DB query'sinde `findByIdAndUserId`) |
| payment-service | JWT + `assertOwnerOrAdmin` | KVKK/GDPR — başkasının ödemesini görmemeli |
| chatbot-service | `permitAll` + rate-limit | Anonim chat tasarımı, LLM cost abuse |
| api-gateway | Sadece routing — auth downstream'de | Tek noktaya bağlı kalmamak |

Common'daki paylaşılan filtreler:
- `JwtAuthenticationFilter` — bearer token parse + `SecurityContext` set.
- `TokenBucketRateLimitFilter` — per-identity (X-Guest-Token > X-Forwarded-For > remoteAddr) rate limit.
- `CorrelationIdFilter` — request başına `X-Correlation-Id` üretir / forward eder, MDC'ye koyar.

Her servisin SecurityConfig'i common'daki bu sınıfları **constructor-injected `@Bean`** olarak
inşa eder — `@Component` değil, çünkü her servis kendi `JwtParser` veya rate-limit predicate'ini
sağlar (config sızdırmadan).

Detaylar: [Bölüm 4](#4-cross-cutting-concerns) ve [security audit notları](#)
README'nin ilgili bölümünde.

---

## 3. Servisler Deep Dive

Her alt başlık şu şablonu izler:
- **Sorumluluk** (1 cümle)
- **Niye bu kapsam**
- **Kritik tasarım kararları**
- **Dosya haritası** (en önemli 5-10 dosya)

### 3.1 `common` modülü

**Sorumluluk**: paylaşılan event tipleri, saga topology sabitleri, JWT primitives, korelasyon
ID filter, rate-limit filter.

**Niye bir common modülü var?**
Servisler arası **kontrat** olan tipler tek yerde olmak zorunda — yoksa `OrderCreatedEvent`
publisher'da bir field eklenir, consumer eski versiyonu deserialize eder, NPE.

Burada barınan şeyler **stateless contract** veya **stateless utility**: enum, record, filter
sınıfı. Hiçbir `@Service` yok, hiçbir `@Repository` yok.

**Tehlikeli olur**:
- Burada hiçbir DB entity OLMAZ. Service B'nin Service A'nın entity'sini import etmesi sızıntıdır.
- Hiçbir business logic OLMAZ. `RegistrationService` common'da olamaz; sadece auth-service'te
  yaşar.
- `@Component` `@Configuration` sınıfları olmamalı. Common kullanıcısı her servis kendi
  `@Bean` ile wire eder. Otherwise transitive autowiring sürpriz olur.

**Kritik tasarım**:
- `spring-boot-starter-security` ve `spring-boot-starter-web` `<optional>true</optional>` —
  common'ı kullanan ama security/web istemeyen servis (yarın eklenecek bir batch worker)
  transitive bağımlı kalmaz.
- Event record'ları immutable, Jackson + Java records seamless deserialize.
- `SagaTopology` exchange/routing-key/queue isimleri **single source of truth**. Hardcoded
  string yok.

**Dosya haritası**:
```
common/src/main/java/com/n11/common/
├── event/                          # Saga payload record'ları
│   ├── OrderCreatedEvent.java      # contains userId, totalAmount, items[], couponCode
│   ├── OrderCancelledEvent.java
│   ├── OrderConfirmedEvent.java
│   ├── PaymentSucceededEvent.java
│   ├── PaymentFailedEvent.java
│   └── OrderItemPayload.java
├── saga/SagaTopology.java          # exchange + DLX + routing keys + queue isimleri
├── security/
│   ├── JwtParser.java              # parse-only, secret + issuer ctor
│   ├── JwtAuthenticationFilter.java # @Component değil — servisler @Bean ile inşa eder
│   ├── TokenBucketRateLimitFilter.java # path predicate ctor parametresi
│   └── AuthenticatedUser.java      # principal record (userId, email, role)
├── correlation/
│   ├── CorrelationId.java          # MDC key + header sabit
│   └── CorrelationIdFilter.java    # her HTTP request'e X-Correlation-Id koyar/forward eder
└── web/ApiError.java               # standard error response shape
```

### 3.2 `api-gateway`

**Sorumluluk**: tek public giriş noktası — frontend'den gelen `/api/*` çağrılarını uygun
downstream servise yönlendirir.

**Niye bir gateway?** Frontend tek bir base URL bilir (`http://localhost:8080/api`).
Downstream port'ları (8081, 8082, ...) public'e açılmaz. CORS tek yerde yönetilir.

**Reddedilen alternatif**: nginx reverse proxy. Spring Cloud Gateway tercih edildi çünkü:
- Route definitions YAML'da, Spring profile'larıyla environment-aware.
- Aggregated Swagger UI — tüm servisleri tek `/swagger-ui.html`'de göster.
- Spring Boot ekosistemi (actuator, micrometer) gateway'e eklenmesi sıfır iş.
- Java + Spring stack zaten bir kere deploy ediyoruz; ikinci runtime (nginx) eklemek karışıklık.

**Kritik tasarım**:
- **Auth yapmaz** — `Authorization: Bearer <token>` header'ını forward eder, downstream
  doğrular. Bu sayede gateway stateless, horizontal-scale kolay.
- **CORS allowlist** env-driven. Eski wildcard + credentials kombinasyonu CSRF benzeri saldırı
  vector'ü idi (commit `a28d714`).
- **Actuator exposure** kısıtlanmış (`health,info`). `/actuator/gateway/routes` topology
  sızdırırdı.
- Aggregated docs route'ları — her downstream'in `/v3/api-docs`'unu gateway altında
  `/aggregated-docs/<service>` path'ine bağlar.

**Dosya haritası**:
```
api-gateway/src/main/resources/application.yml
  → spring.cloud.gateway.routes (her servis için bir route)
  → globalcors.cors-configurations (env-driven allowlist)
  → springdoc.swagger-ui.urls (aggregated UI)
api-gateway/src/main/java/com/n11/gateway/GatewayApplication.java
```

Bu kadar — gateway'de Java kodu yazmadık. Sadece YAML config + main class.

### 3.3 `auth-service`

**Sorumluluk**: kullanıcı kayıt, login, kendi profilini okuma, JWT mint, OAuth2 social login.

**Niye ayrı bir servis?** Identity ayrı bir bounded context. User entity, password hash,
OAuth provider linkleme — hepsi authdb'de. Cart-service Bob'un email'ini bilmek ister ama
password hash'ini görmesin.

**Kritik tasarım kararları**:

- **JWT issuer auth-service'in tekeli**. `JwtTokenProvider.issue()` sadece bu servis. Diğer
  servisler `JwtParser` (parse-only) kullanır.
- **OAuth2 social login Spring Security OAuth2 Client ile**, Keycloak ile değil. Tek
  feature için 1 GB RAM'lik IDP overkill (önceki tartışma).
- **Token URL fragment'ında redirect**: `OAuth2LoginSuccessHandler` browser'ı `${frontend}/auth/callback#token=...`'a yönlendirir. Fragment server log'larına düşmez,
  cookie cookie-flag drama'ları yok.
- **Login + register rate-limit** common'daki `TokenBucketRateLimitFilter` ile, 10/dk per IP.
  Brute force / credential stuffing koruması.
- **OAuth2 user upsert link-by-email**: aynı email ile şifreli hesap varsa Google ile login
  bağlantı kurar (ikinci hesap yaratmaz). Yeni email'se yeni user, `password_hash IS NULL`.

**Niye HS256 secret paylaşılıyor?** Demo / bootcamp scope. Production'da RS256 + JWKS:
auth-service `/.well-known/jwks.json` expose eder, diğerleri public key ile doğrular. Bu
projede 7 servisin JWT_SECRET env'ini paylaşması yeterli — env override prod'da kolayca
yapılabilir.

**Dosya haritası**:
```
auth-service/src/main/java/com/n11/auth/
├── api/AuthController.java                       # /register, /login
├── api/UserController.java                       # /users/me
├── service/RegistrationService.java              # User + bcrypt hash
├── service/AuthenticationService.java            # bcrypt verify + JWT issue
├── service/SocialLoginService.java               # OAuth user upsert + link-by-email
├── security/JwtTokenProvider.java                # issue() + parse() (issue burada tek)
├── security/OAuth2LoginSuccessHandler.java       # JWT mint + frontend redirect
├── security/GitHubEmailAwareUserService.java     # /user/emails fallback
├── config/SecurityConfig.java                    # JWT chain + login rate limit + oauth2Login conditional
├── config/JwtProperties.java                     # n11.jwt.{secret, issuer, accessTtlMinutes}
└── config/SocialLoginProperties.java             # n11.social-login.{google, github, frontendBaseUrl}
```

### 3.4 `product-service`

**Sorumluluk**: katalog — pagination, search, kategori, autocomplete.

**Niye bu API?** n11.com'un homepage'inde gördüğün her ürün listesi, kategori bar, search
sonucu — bu servisten geliyor.

**Niye public?** Anonim browsing modern e-commerce'de standart — Trendyol, Amazon, n11.com
hepsi. Kullanıcıya "hesap aç da göstereyim" demek conversion öldürür.

**Kritik tasarım**:

- **JPQL `cast(:q as string)`** — Postgres null parameter type inference bug'ı (commit `0b035de`).
  `:q is null OR lower(p.name) LIKE lower(:q)` runtime'da short-circuit olsa da SQL planner
  tüm branch'leri tip-kontrol eder. `:q` null bind edildiğinde bytea varsayılır →
  `lower(bytea) does not exist`. Cast → `cast(? as text)` → planner mutlu.
- **`stringtype=unspecified` JDBC URL**'de — yukarıdaki bug için ek savunma. Tüm 6 servis (commit `9dae232`).
- **`@Cacheable` 4 ayrı cache name** ile per-method TTL: categories 1h, byId/bySlug 5m,
  autocomplete 1m. Search results kasıtlı cache'lenmiyor (cardinality).
- **Read-only API** — `POST /products` yok. Admin paneli olmadan ürünler `Flyway` seed
  migration'larıyla geliyor. Production'da admin servisi eklenirse cache `@CacheEvict`
  bekler.

**Dosya haritası**:
```
product-service/src/main/java/com/n11/product/
├── api/ProductController.java         # paginated list, byId, bySlug, autocomplete
├── api/CategoryController.java        # @Cacheable 'categories'
├── service/ProductQueryService.java   # @Cacheable on findById/Slug/autocomplete
├── domain/Product.java + Category.java
├── repository/ProductRepository.java  # JPQL with cast(:q as string)
└── config/CacheConfig.java            # Redis topology, JSON serializer
└── config/SecurityConfig.java         # explicit permitAll
```

### 3.5 `cart-service`

**Sorumluluk**: sepet CRUD + indirim motoru (kampanya & kupon) + saga consumer'ları (kupon rezervasyon, order-confirmed → cart clear).

**Niye burada bu kadar şey?** Sepet, alışverişin kalbi. Discount engine ayrı servis olarak
extract edilebilirdi (`pricing-service`) ama mevcut scope'ta tek tüketici cart. Üçüncü
tüketici (örn. order-service refund recalc) çıkana kadar **YAGNI**. Sınırı doğru
zamanda çiz.

**Kritik tasarım**:

- **Discount engine — Strategy + Chain pattern**. `DiscountStrategy` SPI, `@Component`'ler
  (PercentOffCart, BuyXPayY, CouponCode), `DiscountEngine` priority-sorted çağırır.
  Yeni kampanya tipi = tek dosyalık değişiklik. Detay: README "Kampanya & Kupon Motoru".
- **Discounts additive against original subtotal** — compounding değil. Compounding tutarlılık
  sorunu yaratır (5%+10% = 15% mi 14.5% mi?). Receipt UI net.
- **Coupon reservation saga**:
  - User cart'a coupon code attach → `cart.coupon_code` set, **DB sayacı değişmez** (terk edilen sepetler kuponu yakmaz).
  - Order checkout → `order.created` event → cart-service consumer atomik `UPDATE coupons SET redemptions=redemptions+1 WHERE redemptions < max` → `coupon_redemptions` UNIQUE insert.
  - Payment fail → `order.cancelled` → release: `redemptions--` + redemption row delete.
  - Race senaryoları idempotent: duplicate event `(coupon_id, order_id)` UNIQUE'a takılır.
- **Cart-service ProductClient ile product-service'e REST** çağırır. Product detayı **cart_item
  içine snapshot edilir** (`product_name`, `unit_price`). Fiyat sonradan değişse bile
  sepetteki fiyat sabit — kullanıcı gördüğü fiyatı görme hakkına sahip.
- **`@Cacheable` `coupons:byCode` + `campaigns:active`** her quote'da DB hop'unu önler.
  Saga `reserveOne` / `releaseOne` üzerinde `@CacheEvict` — sayaç değişikliği TTL'siz yansır.

**Dosya haritası**:
```
cart-service/src/main/java/com/n11/cart/
├── api/CartController.java                   # /cart, /cart/items, /cart/coupon
├── service/CartService.java                  # quoteAndMap her read'de DiscountEngine'i çağırır
├── pricing/
│   ├── DiscountStrategy.java                 # SPI
│   ├── DiscountEngine.java                   # orchestration
│   ├── Quote.java + AppliedDiscount.java     # value records
│   └── strategy/
│       ├── PercentOffCartStrategy.java
│       ├── BuyXPayYStrategy.java
│       └── CouponCodeStrategy.java
├── domain/{Cart, CartItem, Coupon, Campaign, CouponRedemption}.java
├── repository/CouponRepository.java          # @Cacheable + @CacheEvict on reserveOne/releaseOne
├── messaging/
│   ├── RabbitConfig.java                     # 3 queue + 3 DLQ + DLX
│   ├── OrderConfirmedListener.java           # cart clear
│   └── CouponSagaListener.java               # reserve/release saga
├── client/ProductClient.java                 # 2s connect / 5s read timeout
└── config/{SecurityConfig, CacheConfig}.java
```

### 3.6 `order-service`

**Sorumluluk**: checkout, sipariş yaşam döngüsü, saga publisher (`OrderCreated`,
`OrderConfirmed`, `OrderCancelled`), payment event consumer.

**Kritik tasarım**:

- **Empty checkout body** — `POST /api/orders/checkout` body almaz. userId+email JWT'den,
  cart server-side fetch (CartClient → cart-service), totalAmount + items snapshot olarak
  Order'a kopyalanır. Client manipülasyon vector'ü sıfır.
- **State machine `transitionTo()`** — geçersiz transition `IllegalStateException`. Duplicate
  event delivery'de PENDING → CONFIRMED → CONFIRMED yapamaz, listener idempotency check'i
  sessizce no-op'lar.
- **`afterCommit` event publishing** — Order DB transaction commit etmeden RabbitMQ'ya
  yayın yok. `TransactionSynchronization.afterCommit()` ile garanti. Event kaybı veya hayalet
  event yok.
- **Coupon code Order entity'sine snapshot** — sepet sonradan mutasyon olsa bile cancellation
  saga'sının doğru kupon'u release edebilmesi için.

**Dosya haritası**:
```
order-service/src/main/java/com/n11/order/
├── api/OrderController.java                  # findByIdAndUserId — IDOR-safe
├── service/CheckoutService.java              # afterCommit publishOrderCreated
├── messaging/
│   ├── PaymentResultListener.java            # payment.{succeeded,failed} → state transition
│   └── OrderEventPublisher.java
├── domain/Order.java                         # transitionTo() state machine
└── client/CartClient.java                    # Authorization header forward
```

### 3.7 `payment-service`

**Sorumluluk**: ödeme — saga consumer (`OrderCreated` → charge), Iyzico entegrasyonu.

**Kritik tasarım**:

- **Pluggable gateway** — `IYZICO_ENABLED=true/false`. False ise `MockPaymentGateway`
  `MOCK-XXXXX` referansı döner. Demo / CI için zorunlu — gerçek Iyzico key'i olmadan da
  uçtan uca akış doğrulanabilir.
- **`PAYMENT_FAILURE_RATE`** env — Mock gateway'de %X başarısızlık simüle eder. Compensation
  saga'yı manuel test etmek için.
- **Per-attempt persistence** — `Payment` entity'si her charge denemesi için bir row.
  Aynı orderId için birden fazla payment olabilir (retry, manual recharge senaryoları).
  `findByOrderIdOrderByCreatedAtAsc` history endpoint'i bunu döner.
- **Ownership check** — `byOrder` ve `byId` endpoint'leri `assertOwnerOrAdmin`: payment.userId
  caller.userId'ye eşit veya ROLE_ADMIN, aksi halde 404 (403 değil — non-owner'a row varlığı
  bilgisi sızdırmamak için).

**Dosya haritası**:
```
payment-service/src/main/java/com/n11/payment/
├── api/PaymentController.java                # ownership-checked read
├── service/PaymentService.java               # OrderCreated consume → charge → Payment* publish
├── gateway/
│   ├── PaymentGateway.java                   # interface
│   ├── IyzicoPaymentGateway.java             # @ConditionalOnProperty(iyzico.enabled=true)
│   └── MockPaymentGateway.java               # @ConditionalOnProperty(iyzico.enabled=false)
└── config/SecurityConfig.java                # JWT chain
```

### 3.8 `chatbot-service`

**Sorumluluk**: alışveriş asistanı — `POST /api/chat`, oturum geçmişi, RAG (catalog grounding).

**Niye anonim akış?** Misafir alışveriş — UX prensibi. Kullanıcı login öncesi chatbot'a
"bu kategoride ne var?" sorabilmeli.

**Kritik tasarım**:

- **Pluggable provider** — `MOCK | GROQ | CLAUDE`. `@ConditionalOnProperty` ile
  `ChatProvider` bean seçilir. Default MOCK (template responses), key'siz çalışır.
- **Catalog grounding** — `CatalogGrounding.snapshotForPrompt()` her LLM çağrısında system
  prompt'a kategori + featured ürün listesi ekler. RAG light — vector DB yok, ürün sayısı
  küçük, her seferinde tüm liste prompt'a sığar.
- **Rate limit `POST /api/chat`** — common'daki TokenBucketRateLimitFilter, 20/dk per
  identity (X-Guest-Token > X-Forwarded-For > remoteAddr). LLM cost abuse koruması.
- **Session ownership** — history endpoint X-Guest-Token match şart. Session yaratıldığında
  guestToken DB'ye kaydedilir (`chat_sessions.guest_token`), history'de match check.

**Dosya haritası**:
```
chatbot-service/src/main/java/com/n11/chatbot/
├── api/ChatController.java                   # POST /chat, GET /chat/{sessionId}/history
├── service/ChatService.java                  # session upsert + provider call + history
├── provider/
│   ├── ChatProvider.java                     # interface
│   ├── MockProvider.java                     # @ConditionalOnProperty(provider=MOCK)
│   ├── GroqProvider.java                     # @ConditionalOnProperty(provider=GROQ)
│   └── AnthropicProvider.java                # @ConditionalOnProperty(provider=CLAUDE)
├── grounding/CatalogGrounding.java           # ProductClient + snapshot for prompt
├── domain/{ChatSession, ChatMessage}.java
└── config/SecurityConfig.java                # permitAll + rate limit
```

---

## 4. Cross-Cutting Concerns

### 4.1 Correlation ID

Her HTTP request `CorrelationIdFilter` tarafından `X-Correlation-Id` header üretir veya
forward eder. MDC'ye `correlationId` key'i ile koyulur, `logback-spring.xml` desenindeki
`%X{correlationId:-}` yazdırır.

Saga event'leri payload field olarak da `correlationId` taşır — RabbitMQ üzerinden geçen
mesajda da MDC restore edilir. Sonuç: bir `POST /api/orders/checkout` çağrısı 4 servisin
log'unda **aynı id** ile aranır.

```
2026-04-27 17:23:01 INFO [c0ffee...] order-service - Order created id=42
2026-04-27 17:23:01 INFO [c0ffee...] payment-service - Charging order=42
2026-04-27 17:23:02 INFO [c0ffee...] order-service - Order 42 → CONFIRMED
2026-04-27 17:23:02 INFO [c0ffee...] cart-service - OrderConfirmed received userId=7
```

Distributed tracing (OpenTelemetry, Jaeger) eklemenin **maliyetinin yarısı bu** — bu kadar
sade tutuldu.

### 4.2 Error handling

Her servis bir `GlobalExceptionHandler` (`@RestControllerAdvice`) ile bilinen hataları
`ApiError` shape'ine map eder:

```json
{ "timestamp": "...", "status": 404, "error": "Not Found",
  "message": "Product not found: 99", "path": "/api/products/99" }
```

`ResponseStatusException` Spring'in built-in'i — controller'dan throw edilince Spring
status code + message'ı yukarıdaki formata otomatik çevirir.

Custom exception'lar (örn. `InsufficientStockException`, `ProductLookupException`) handler
içinde bilinçli mapping ile spesifik HTTP code'a karşılık gelir.

### 4.3 Validation

`@Valid` + jakarta validation annotations (`@NotNull`, `@Min`, `@Email`, `@Size`).
Validation hatası → `MethodArgumentNotValidException` → `GlobalExceptionHandler` 400 + field
error mesajları.

Önemli: `@Min(1)` quantity'de — negatif veya sıfır miktar input edilemez. Server-side ek
business rule check'leri service layer'da (örn. `targetQuantity > product.stock`).

### 4.4 Eventual consistency boundary'leri

Bu sistemde **strong consistency** sadece tek bir DB transaction içinde:
- Cart write + ItemList: aynı transaction (cart-service).
- Order insert + initial transition: aynı transaction (order-service).
- Coupon UPDATE + redemption INSERT: aynı transaction (cart-service).

**Eventual consistency** servisler arası:
- Order CONFIRMED → cart cleared: bu **mesaj queue üzerinden**, milisaniyeler farkı.
- Coupon reserved → cache evicted: aynı transaction içinde Spring `@CacheEvict` ama farklı
  consumer'lar farklı zamanda görür.

Eventual consistency penceresinde tutarsız UX olabilir mi? Genelde hayır — kullanıcı
checkout sonrası `/orders` sayfasına geçer (200ms), o esnada saga zaten tamamlanmıştır.
Edge case: `/cart` çok hızlı yenilenirse 1 saniye için "boş sepet" yerine "eski sepet"
görür. Acceptable.

---

## 5. Test Stratejisi

### Unit tests (Mockito)

Her servisin business logic class'ları (CartService, DiscountEngine, AuthenticationService,
PaymentService) `@ExtendWith(MockitoExtension.class)` ile mock'lar arasında izole edilir.

Strateji testleri (PercentOffCartStrategyTest, vb.) Mockito'suz — DiscountStrategy pure
function. Doğrudan `new PercentOffCartStrategy().evaluate(ctx)` çağrılır.

### Integration tests (Testcontainers)

`@SpringBootTest + @ServiceConnection PostgreSQLContainer` ile her servis için bir IT:
- `AuthFlowIT` — register → login → /me akışı.
- `ProductCatalogIT` — full HTTP üzerinden /products, /categories, /autocomplete.

Niye Testcontainers ve Embedded H2 değil? PostgreSQL-spesifik feature kullanıyoruz (JSON
column tipleri yok ama ON CONFLICT, uppercase'sensitive index, NUMERIC precision). H2'de
yeşil olan test prod'da kırmızı olabilir.

### Niye E2E testi yok?

Tüm 7 servisi + Postgres + RabbitMQ + Redis ayağa kaldıran bir E2E test pahalı (>60s build
time) ve flaky olur. Onun yerine:
- README'de manuel curl smoke test'leri var.
- Production'da Slack deploy notification + actuator health check yeterli.
- Saga akışı `docs/saga.md` içinde idempotency case-by-case açıklanmış.

Eklenecek olursa: `cypress` veya `playwright` ile frontend'i tıklayan E2E. Ama bootcamp
scope dışı.

### Cache test profili

`product-service/src/test/resources/application-test.yml`:
```yaml
spring:
  cache:
    type: none
  autoconfigure:
    exclude: [RedisAutoConfiguration, RedisRepositoriesAutoConfiguration]
```

`@ActiveProfiles("test")` test class'ında bu profile'ı aktive eder. CacheConfig
`@ConditionalOnProperty(spring.cache.type=redis)` ile skip, Spring Boot
`NoOpCacheManager` döner. CI'da Redis container gerekmez.

### Frontend testleri (vitest)

14 test:
- `format.test.js` — TRY locale formatlama, null safety
- `CountdownTimer.test.jsx` — render + zero clamp
- `guestCart.test.js` — 10 case (add, increment, clamp, update, remove, clear, merge, empty-merge)

Eksiklikler: AuthContext, CartContext, CartPage component testi yok. Coverage düşük (cycle
sınırı yok; ürün detayı çağrısı, kupon uygulama, login/register form akışı test edilmiyor).
Production'da büyük eksiklik, demo için kabul edilebilir.

---

## 6. Bilinçli Olarak Yapmadıklarımız

Bunlar üzerinde **karar verildi** — eklenmedi, çünkü scope abartısı veya value/cost dengesi
düşük.

| Eklemediğimiz | Niye |
|---|---|
| **Keycloak / Auth0** | Sadece social login için 1 GB RAM IDP overkill — Spring Security OAuth2 Client yeterli |
| **gRPC service-to-service** | REST + JSON Java ekosisteminin defaults'ı, debug edilebilir |
| **Kafka** | Exactly-once veya yüksek throughput'a ihtiyaç yok — RabbitMQ at-least-once + idempotent consumer yeterli |
| **Inventory service** | Stock product entity'sinde — ayrı reservation servisi scope dışı |
| **Distributed tracing (OpenTelemetry, Jaeger)** | Correlation ID logback ile yeterli. Tracing eklenmek isterse `Micrometer Tracing` autoconfig var, env override |
| **Circuit breaker (Resilience4j)** | Tek-instance demo, downstream'in patlaması test ortamında simüle edilebiliyor; gerçek prod risk değil |
| **API versioning** (`/v1/...`) | Hiç external consumer yok, breaking change kendi frontend'imizi etkiler — ezbere version yapmak abartı |
| **Mass-assignment koruması** | Register/login DTO'ları zaten role/userId field'ı yok; bilinçli minimal contract |
| **CSRF** | Stateless JWT API — CSRF cookie-based session pattern'inde anlamlı, burada değil |
| **Account lockout / CAPTCHA** | Login rate-limit (10/dk per IP) brute force'a yeterli; CAPTCHA UX kayıp |
| **Refresh token** | Access token TTL 60 dakika, kullanıcı yeniden login OK. Refresh flow karmaşıklığı demo için fazla |
| **Soft delete** | İhtiyaç olmadı — order CANCELLED status'ü row'u tutar, gerçek delete yok |
| **Audit log table** | CRUD audit production gerekliği, prototip için fazla |
| **CQRS / Event sourcing** | Read load yok, replay senaryosu yok |
| **Multi-tenant** | Tek tenant — n11 kendisi |
| **Bulk operations API** | Sepete tek tek item eklemek yeterli |
| **Webhook / outbound integration** | Slack CI bildirim hariç, dış sisteme webhook yok |
| **Internationalization** | Türkçe sabit, demo n11 hedefli |
| **Kubernetes manifests** | Docker Compose tek droplet için yeterli — k8s gereksiz overhead |

---

## 7. Bir Şeyi Değiştirirken — Okuma Listesi

Yeni bir feature eklemeden önce dokunduğun katmanın ilgili docs'unu oku:

| Eklemek istediğin | Önce oku |
|---|---|
| Yeni saga step | [`docs/saga.md`](saga.md) — idempotency rules, DLX |
| Yeni cache | [README — Cache (Redis)](../README.md#cache-redis) — TTL stratejisi, `@CacheEvict` invalidation |
| Yeni endpoint | [Bölüm 2.5 Security defense-in-depth](#25-security-defense-in-depth) — hangi servisin policy'si ne |
| Yeni discount tipi | `cart-service/.../pricing/DiscountStrategy.java` Javadoc + README "Kampanya & Kupon Motoru" |
| Yeni mikroservis | Bu doküman bölüm 3 — common pattern'ları (SecurityConfig, RabbitConfig, *Properties record) |
| Frontend route | `frontend/src/App.jsx` — public/protected route convention |
| OAuth provider eklemek | `auth-service/.../config/OAuth2ClientConfig.java` — `CommonOAuth2Provider.GOOGLE.getBuilder` benzeri |
| Yeni payment gateway | `payment-service/.../gateway/PaymentGateway.java` interface — `@ConditionalOnProperty` provider seçimi |

Genel kural: bir tasarım kararını değiştirmek istiyorsan, önce buradaki "Niye bu" satırını
oku. O cümleyi yanlış buluyorsan, **PR description'ında niye yanlış olduğunu yaz**.
Daha önce verilen kararı sessizce flip etmek = ileride aynı tartışmayı tekrar yapmak.

---

## Ekler

- [`docs/architecture.md`](architecture.md) — yüksek seviye diyagram + tasarım kararları özet
- [`docs/saga.md`](saga.md) — saga akışları + idempotency + race senaryoları
- [`docs/deployment.md`](deployment.md) — DigitalOcean droplet kurulumu
- [`docs/cicd.md`](cicd.md) — GitHub Actions workflow'ları
- [`README.md`](../README.md) — kullanıcı-facing özet, kurulum, security, cache, OAuth setup
