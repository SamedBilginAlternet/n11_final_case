# `api-gateway`

**Bu doküman:** Public-facing tek giriş noktası. Spring Cloud Gateway. Niye var, ne yapar,
ne yapmaz.

**Port:** 8080
**Stack:** Spring Cloud Gateway + WebFlux (reactive)
**State:** Stateless

---

## 1. Niye Gateway?

7 backend servis. İki seçenek:

| Seçenek | Avantaj | Dezavantaj |
|---|---|---|
| **Servisleri direkt expose et** | Sade, hop yok | CORS her serviste, client port matrix bilmek zorunda, public surface area büyük |
| **Tek gateway** (bizim) | Tek public port, tek CORS noktası, client'a tek URL | Ekstra hop (~1-3ms), reactive WebFlux servisi |

Gateway kazandı çünkü:
- **Tek public host**: Client `http://localhost:8080/api/...`'ı bilir, hangi servisin hangi
  port'ta olduğuyla ilgilenmez.
- **CORS tek yerde**: 7 servise ayrı CORS yazmak yerine gateway'de tek config.
- **Caddy reverse proxy** + **gateway** + **service** — tek entry, kontrollü.
- **Future**: rate limit, circuit breaker, request transformation gateway'de yapılır.

---

## 2. Routing — `application.yml`

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: auth
          uri: ${AUTH_SERVICE_URL:http://localhost:8081}
          predicates:
            - Path=/api/auth/**,/api/users/**,/api/addresses/**

        - id: products
          uri: ${PRODUCT_SERVICE_URL:http://localhost:8082}
          predicates:
            - Path=/api/products/**,/api/categories/**

        - id: cart
          uri: ${CART_SERVICE_URL:http://localhost:8083}
          predicates:
            - Path=/api/cart/**,/api/wishlist/**,/api/coupons/**

        - id: orders
          uri: ${ORDER_SERVICE_URL:http://localhost:8084}
          predicates:
            - Path=/api/orders/**

        - id: payments
          uri: ${PAYMENT_SERVICE_URL:http://localhost:8085}
          predicates:
            - Path=/api/payments/**

        - id: chatbot
          uri: ${CHATBOT_SERVICE_URL:http://localhost:8087}
          predicates:
            - Path=/api/chat/**
```

### Routing Pattern

`Path=/api/<domain>/**` — path prefix matcher. URL'in geri kalanı (path + query string) hedefe
**aynen** forward edilir.

Örnek:
```
Client:    GET http://localhost:8080/api/products/5/recommendations
Gateway:   GET http://product-service:8082/api/products/5/recommendations
```

`StripPrefix` filter **kullanmıyoruz** — backend servisler path'in tamamını bekliyor (her
servisin RestController'ı `/api/...` ile başlıyor). Bu pattern niye:

- Backend servis bağımsız çalışabilir — gateway olmasa da `curl http://localhost:8082/api/products`
  çalışır.
- Service-to-service internal call'larda da aynı path geçerli.
- `StripPrefix=2` kullansak servisler içlerinde path bilmezler — gateway-coupling artar.

### Niye `/api/auth/**` ve `/api/users/**` ikisi auth-service'e

Tarihsel: kullanıcı yönetimi (`/users/me`, `/users/{id}/promote`) auth-service'in sorumluluğunda
çünkü User entity orada. Mantıken aynı domain. İki path prefix tek route'ta birleşmiş.

`/api/addresses/**` da auth'a — adresler User entity'sine FK ile bağlı, ayrı servis ayırmak
karmaşıklık eklerdi.

### Niye `/api/coupons/**` cart-service'e değil yeni bir service'e değil

Coupon entity cart-service'in DB'sinde — checkout sırasında `cart.order-created.coupon.q`
saga participant'ı kupon reservation yapıyor. Cart ve coupon **aynı transaction sınırında**
oturuyor. Ayrı servis = network call + cross-DB sync = karmaşıklık.

---

## 3. CORS — Globalcors

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:3001,http://localhost:5173,http://localhost:5174}
            allowedMethods: GET,POST,PUT,PATCH,DELETE,OPTIONS
            allowedHeaders: Authorization,Content-Type,X-Correlation-Id,X-Guest-Token
            exposedHeaders: X-Correlation-Id,Retry-After
            allowCredentials: true
            maxAge: 3600
```

### Per-path mu Global mi

`globalcors` = tüm route'lara aynı config. Niye:
- 7 servis, 1 web app, 1 admin panel — hepsi aynı CORS politikası kullanıyor.
- Per-path config tekrar (DRY ihlali).
- Future'da bir endpoint için farklı CORS gerekirse (örn. webhook public endpoint), ekstra
  filter eklenir.

### `allowedOrigins` Liste, Pattern Değil

```yaml
allowedOrigins: http://localhost:3000,http://localhost:3001,...
```

Spring'in `allowedOriginPatterns: '*'` variant'ı **`allowCredentials=true` ile birleştirildiğinde
güvenlik açığı**. Pattern wildcard'ı her origin'i kabul eder, browser credential'ı (cookie,
Authorization header) gönderir. Saldırgan kendi domain'inden authenticated request yapabilir.

`allowedOrigins` **explicit list** = sadece listedeki origin'ler credentialed CORS yapabilir.
Production'da `CORS_ALLOWED_ORIGINS=https://yourdomain.com,https://admin.yourdomain.com`.

Detay: [`docs/security.md`](../security.md#7-cors--gateway-seviyesinde-tek-yer).

### `exposedHeaders`

Default'ta browser CORS response'unda **safe header'ları** JS'e expose eder. Custom header'lar
expose edilmez. `X-Correlation-Id` debug için JS tarafından okunabilsin diye explicit expose.

`Retry-After` rate-limit (429) response'larda gönderiliyor — UI'da kullanıcıya "X saniye
sonra dene" göstermek için.

---

## 4. JWT Forwarding — Implicit

Gateway JWT'ye **dokunmaz**. `Authorization: Bearer <token>` header'ı backend servise olduğu
gibi forward eder. Backend kendi `JwtAuthenticationFilter`'ı ile parse + verify yapar.

### Niye Gateway'de Verify Etmiyoruz

Defense in depth — bir saldırgan gateway'i bypass edip doğrudan service'e ulaşırsa (intra-cluster
network attack), service yine korunmuş olur. Gateway "trust boundary" değil, **routing layer**.

Detay: [`docs/security.md`](../security.md#niye-gatewayde-değil-servislerde-verify).

---

## 5. Aggregated Swagger UI

Her backend servis kendi `/swagger-ui.html` ve `/v3/api-docs` endpoint'lerine sahip. Gateway
hepsini tek UI'da agregate eder:

```yaml
springdoc:
  swagger-ui:
    urls:
      - name: auth
        url: /aggregated-docs/auth
      - name: product
        url: /aggregated-docs/product
      # ...
```

Ek route'lar:
```yaml
- id: auth-docs
  uri: ${AUTH_SERVICE_URL:http://localhost:8081}
  predicates:
    - Path=/aggregated-docs/auth
  filters:
    - SetPath=/v3/api-docs
```

Client `http://localhost:8080/swagger-ui.html` → dropdown'da 6 servis görür → seçtiğinde o
servisin OpenAPI spec'ini gateway aracılığıyla çeker.

`SetPath` filter: `/aggregated-docs/auth` → backend'e `/v3/api-docs` olarak gider. Gateway-side
URL rewriting tek istisna.

---

## 6. WebFlux — Reactive Stack

Spring Cloud Gateway **WebFlux** (reactive) üzerine kurulu, Servlet (blocking) değil. Backend
servislerimiz Servlet ama gateway WebFlux. Niye:

- **Connection multiplexing**: Gateway thread başına 1 request değil, event-loop'ta binlerce.
- **Backend yavaş ise gateway kilitlenmez**: 1000 paralel client + slow backend = WebFlux
  havuzu açık tutar, blocking model'de thread pool tükenir.

Trade-off: Gateway'de `@Component` Spring bean writing'i farklı (`Mono`, `Flux`). Bu repoda
gateway'in custom logic'i yok — sadece routing config — ama eklemek istesen reactive paradigm
gerekir.

---

## 7. Tracing

```yaml
management:
  tracing:
    sampling:
      probability: ${TRACE_SAMPLING:1.0}
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318}/v1/traces
```

Spring Cloud Gateway'in built-in `RouteToRequestUrlFilter` her route hop için span üretir.
Jaeger UI'da bir HTTP request:
```
gateway   |─ 0ms  HTTP GET /api/products/5
          |    └─ product-service |─ 1ms  HTTP GET /api/products/5
          |         └─ DB query   |─ 12ms SELECT...
gateway   |─ 18ms HTTP 200
```

Cross-service propagation: gateway span context'i (`traceparent` HTTP header) backend'e
forward eder, backend kendi span'ını parent context altında oluşturur. Trace tek bir tree'de.

---

## 8. Hata Yönetimi

Backend 500 dönerse gateway:
- Response body olduğu gibi forward edilir.
- Status code korunur.
- Header'lar (`Content-Type`, custom header'lar) korunur.

Backend **down** ise (TCP refuse veya timeout) gateway 503/504 döner. Connection error tipine
göre. UI tarafı interceptor'ı bu status'ları "sunucu hatası" toast'una çevirir.

### Niye Circuit Breaker Yok

Spring Cloud Circuit Breaker (Resilience4j) eklenebilirdi. Reddedildi:
- 7-servisli, 8GB-RAM bütçeli scope'ta over-engineering.
- Backend zaten healthcheck ile compose'da izleniyor — down ise compose otomatik restart eder.
- Critical path için (payment) DLQ ile failure isolated.

Volume büyürse + downstream third-party (Iyzico) yavaşlarsa circuit breaker eklenir.

---

## 9. Build & Deploy

`pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway</artifactId>
</dependency>
```

`Application.java`:
```java
@SpringBootApplication
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
```

Docker compose:
```yaml
api-gateway:
  build:
    context: ./backend
    args:
      SERVICE: api-gateway
  depends_on:
    auth-service: { condition: service_started }
    product-service: { condition: service_started }
    # ... (tüm backend servisler)
  environment:
    SERVER_PORT: 8080
    AUTH_SERVICE_URL: http://auth-service:8081
    # ... (her servis URL'i)
  ports: ["8080:8080"]
```

`depends_on` — gateway tüm servisler **started** olana kadar başlamaz. Servis healthy değil
ama TCP açık olması yeterli (gateway başlangıçta route declare ediyor, healthcheck o sırada
çalışmıyor henüz).

---

## 10. Klasör Yapısı

```
backend/api-gateway/
├── pom.xml
└── src/main/
    ├── java/com/n11/gateway/
    │   └── GatewayApplication.java   # main + @SpringBootApplication
    └── resources/
        └── application.yml           # tüm gateway config burada
```

Tek Java sınıfı — gateway logic'i config'de. Niye:
- Spring Cloud Gateway "configuration as code" değil, "configuration as YAML" tasarım felsefesi.
- Custom logic gerekirse `RouteLocator` bean'i Java tarafından eklenir; ihtiyacımız yok.

---

## 11. Bilinçli Olarak Yapmadıklarımız

- **Gateway-side caching**: Backend cache zaten Redis'te. Gateway cache layer eklemek
  invalidation iki yerde olur.
- **Request body transform**: Reddedildi. Backend doğru DTO'yu beklesin.
- **JWT verify gateway'de**: Defense in depth karşıtı.
- **API key auth**: B2B integration yok.
- **GraphQL gateway**: REST yeterli. GraphQL future'a açık.

---

## İlgili Dokümanlar

- [`docs/security.md`](../security.md) — CORS + JWT forwarding rationale
- [`docs/services/common.md`](common.md) — JwtAuthenticationFilter detayı
- [`docs/observability.md`](../observability.md) — Tracing
