# `common` Module

**Bu doküman:** Tüm servislerin paylaştığı utility/cross-cutting kod. Niye var, ne içerir,
ne içermez.

---

## 1. Niye `common` Modülü?

7 servis var. JWT parsing logic'i her servise yazsam:
- 7 yerde aynı bug'ı düzeltirim.
- 7 yerde aynı testi tekrarlarım.
- Bir servis security policy değiştirirse diğerleri uyumsuz kalır.

`common` Maven module — bir **bağımlılık olarak** her servise dahil edilen ortak Java module.
Her servisin `pom.xml`'inde:
```xml
<dependency>
    <groupId>com.n11</groupId>
    <artifactId>common</artifactId>
</dependency>
```

### Niye JAR Dağıtımı Yok

`common/pom.xml`:
```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <skip>true</skip>
    </configuration>
</plugin>
```

`spring-boot-maven-plugin` skip — common bir Spring Boot uygulaması değil, sadece bir kod
kitaplığı. Bu yüzden:
- Boot loader'ı yok.
- `main()` method yok.
- JAR sadece sınıf dosyalarını içerir, bağımsız çalıştırılamaz.

Maven multi-module reactor build sırasında `common` ilk derlenir (parent pom'da `<modules>`
listesinde ilk sırada), sonra her servis ona bağımlı şekilde derlenir.

---

## 2. İçerik — Public Sınıflar

### 2.1 `correlation/`

| Sınıf | Görev |
|---|---|
| `CorrelationId` | Sabitler: `HEADER = "X-Correlation-Id"`, `MDC_KEY = "correlationId"` |
| `CorrelationIdFilter` | Her request'e correlation ID ata + MDC'ye koy |

```java
public class CorrelationIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
        String existing = req.getHeader(CorrelationId.HEADER);
        String id = (existing != null && !existing.isBlank()) ? existing : UUID.randomUUID().toString();
        MDC.put(CorrelationId.MDC_KEY, id);
        res.setHeader(CorrelationId.HEADER, id);
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }
}
```

Her servis'in SecurityConfig'i bu filter'ı en başta register eder.

### 2.2 `security/`

| Sınıf | Görev |
|---|---|
| `JwtParser` | JWT decode + verify (HS256). `parse(token)` → `ParsedToken(userId, email, role)` veya throw |
| `JwtAuthenticationFilter` | Bearer token'ı extract, parse, `SecurityContextHolder`'a `AuthenticatedUser` koy |
| `AuthenticatedUser` | Record: `(Long userId, String email, String role)` |
| `UserContext` | Thread-local `AuthenticatedUser` getter (controller'lar için convenience) |
| `TokenBucketRateLimitFilter` | Per-IP rate limit, `(capacity, refillSeconds, predicate)` ile config |

`JwtAuthenticationFilter`:
```java
@Override
protected void doFilterInternal(HttpServletRequest req, ...) {
    String h = req.getHeader("Authorization");
    if (h != null && h.startsWith("Bearer ")) {
        try {
            ParsedToken p = jwtParser.parse(h.substring(7));
            AuthenticatedUser principal = new AuthenticatedUser(p.userId(), p.email(), p.role());
            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    principal, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + p.role()))));
        } catch (Exception ignored) {
            // log + 401 dönsün — sonraki filter Spring Security'nin auth-required matcher'ı handle eder
        }
    }
    chain.doFilter(req, res);
}
```

Detaylı açıklama: [`docs/security.md`](../security.md).

### 2.3 `event/` — Saga Event Records

```
common.event/
├── OrderCreatedEvent.java
├── OrderConfirmedEvent.java
├── OrderCancelledEvent.java
├── OrderShippedEvent.java
├── OrderDeliveredEvent.java
├── OrderItemPayload.java
├── PaymentSucceededEvent.java
├── PaymentFailedEvent.java
└── LowStockReportEvent.java
```

Her biri immutable record. Static factory `of(...)` ile `eventId` + `occurredAt` otomatik
doldurulur. Detay: [`docs/messaging.md`](../messaging.md#6-mesaj-şekli--java-records--json).

### 2.4 `saga/SagaTopology.java`

RabbitMQ exchange + routing key + queue isim sabitleri:

```java
public final class SagaTopology {
    public static final String EXCHANGE = "saga.exchange";
    public static final String DLX_EXCHANGE = "saga.exchange.dlx";

    public static final class RoutingKey {
        public static final String ORDER_CREATED = "order.created";
        // ... diğerleri
    }

    public static final class Queue {
        public static final String PAYMENT_ORDER_CREATED = "payment.order-created.q";
        public static final String NOTIFICATION_ORDER_SHIPPED_DLQ = NOTIFICATION_ORDER_SHIPPED + ".dlq";
        // ...
    }
}
```

String magic yok — yanlış routing key compile-time hatası.

### 2.5 `web/ApiError.java`

Standart hata response DTO'su:

```java
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId
) { ... }
```

Her servisin `GlobalExceptionHandler`'ı bu shape'i döner — frontend tek bir hata interceptor'ı
ile her servisten gelen hatayı uniform parse eder.

---

## 3. `pom.xml` — Bağımlılıklar

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-amqp</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>

    <!-- JWT (jjwt) -->
    <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-api</artifactId></dependency>
    <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-impl</artifactId><scope>runtime</scope></dependency>
    <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-jackson</artifactId><scope>runtime</scope></dependency>

    <!-- Jackson JSR-310 (Instant serialization) -->
    <dependency><groupId>com.fasterxml.jackson.datatype</groupId><artifactId>jackson-datatype-jsr310</artifactId></dependency>

    <!-- OpenTelemetry tracing -->
    <dependency><groupId>io.micrometer</groupId><artifactId>micrometer-tracing-bridge-otel</artifactId></dependency>
    <dependency><groupId>io.opentelemetry</groupId><artifactId>opentelemetry-exporter-otlp</artifactId></dependency>

    <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><optional>true</optional></dependency>
</dependencies>
```

Niye burada:
- **`spring-boot-starter`**: Logging, configuration binding, slf4j default'ları.
- **`spring-boot-starter-amqp`**: Event class'ları RabbitTemplate.convertAndSend'in
  serialize edebileceği şekilde marker.
- **`spring-boot-starter-web`**: `OncePerRequestFilter` Servlet API'i için. (web olmayan servisler
  bile common'ı dahil ediyor — küçük tradeoff, kullanmazlarsa bean'lemiyor.)
- **`spring-boot-starter-security`**: `JwtAuthenticationFilter` Spring Security API'lerini kullanıyor.
- **`jjwt`**: JWT encode/decode kütüphanesi.
- **`micrometer-tracing` + OTLP exporter**: Tüm servisler tracing kullanır → ortak yer.

---

## 4. Niye Bu Sınıflar `common`'da?

Kriter: **Birden fazla servisin aynı şeyi yapması gerekiyorsa** common'da.

| Sınıf | Kullanan Servisler | Niye Common |
|---|---|---|
| `JwtAuthenticationFilter` | auth, product, cart, order, payment, chatbot, notification | 7 servis aynı JWT'yi parse etmek zorunda |
| `CorrelationIdFilter` | Hepsi | Tek correlation conventionu lazım |
| `OrderCreatedEvent` | order (publish), payment (consume), cart (coupon reserve) | 3 servis aynı record'a referans veriyor |
| `SagaTopology` | Tüm publisher + consumer | String magic yerine compile-time check |

### Niye `User` Entity Common'da Değil

Çünkü:
- Sadece **auth-service** User entity'sine erişiyor.
- Diğer servisler `userId` (long) ile yetiniyor — JWT'den geliyor.
- Eğer User entity'i common'da olsa, **tüm servisler authdb şemasını bilirdi** → per-service-DB
  prensibi kırılır.

Aynı mantık: `Order`, `Product`, `Cart` entity'leri common'da değil — sadece sahibi servis
biliyor. Cross-service'te DTO veya event payload kullanılır.

### Niye `CategoryDto` Common'da Değil

Hatta DTO'lar bile servise ait. product-service'in `CategoryDto`'su var, başka servis ona
referans verirse — refactoring'de coupling yaratır. Cross-service ihtiyaç olursa **event payload**
record'u common'a koyulur (örn. `OrderItemPayload`), genel DTO değil.

---

## 5. Bilinçli Olarak `common`'da Olmayan

- **`@Configuration` sınıfları**: SecurityConfig, RabbitConfig, CacheConfig, vb. — her servis
  kendi yapılandırmasını yazar. Common'a koymak servislerin "kendi karar verdiği"
  şeyleri zorlardı. Sadece **yardımcılar** common'da.
- **JPA Entity'ler**: Her servis kendi şemasını sahiplenir.
- **REST controller'lar**: Endpoint'ler servise özgü.
- **Service layer**: Business logic.

`common` = **utility + cross-cutting + shared types**, daha fazlası değil.

---

## 6. Test Strategy — `common` Tek Başına Test Edilmiyor

`common` modülünün kendi test'i yok (`src/test` boş). Niye:
- `JwtAuthenticationFilter` izole test edilebilir ama her servisin integration test'i zaten
  bu filter'ı practice'te exercise ediyor.
- Event record'lar trivial (record, no logic). Test yazmak zaman kaybı.

Common'a karmaşık bir helper eklenirse (örn. password complexity validator), o helper'a unit
test eklenir. Şu an gerek yok.

---

## 7. Versiyonlama — Yok

`common` sürümü `1.0.0`-sabit. Servisler aynı reactor build'inde derlendiği için her servis
**aynı `common` versiyon'una bağlı** — multi-version coexistence imkansız.

Eğer `common`'ı **bağımsız sürüm yönetimi olan** bir kitaplık yapsak (dış Nexus repo'ya publish):
- v1.0 vs v1.1 farklı serviste çalışabilir → API uyumsuzluğu riski.
- "Hangi servis hangi common version'ında?" matrix takibi.

Bu projede gerek yok. Monorepo + reactor build = atomic upgrade. Common'da breaking change
yapan PR aynı PR'da tüm servisleri günceller.

---

## 8. Klasör Yapısı

```
backend/common/
├── pom.xml
└── src/main/java/com/n11/common/
    ├── correlation/
    │   ├── CorrelationId.java
    │   └── CorrelationIdFilter.java
    ├── event/
    │   ├── LowStockReportEvent.java
    │   ├── OrderCancelledEvent.java
    │   ├── OrderConfirmedEvent.java
    │   ├── OrderCreatedEvent.java
    │   ├── OrderDeliveredEvent.java
    │   ├── OrderItemPayload.java
    │   ├── OrderShippedEvent.java
    │   ├── PaymentFailedEvent.java
    │   └── PaymentSucceededEvent.java
    ├── saga/
    │   └── SagaTopology.java
    ├── security/
    │   ├── AuthenticatedUser.java
    │   ├── JwtAuthenticationFilter.java
    │   ├── JwtParser.java
    │   ├── TokenBucketRateLimitFilter.java
    │   └── UserContext.java
    └── web/
        └── ApiError.java
```

---

## İlgili Dokümanlar

- [`docs/security.md`](../security.md) — JWT detayı
- [`docs/messaging.md`](../messaging.md) — Event topology
- [`docs/observability.md`](../observability.md) — Correlation ID detayı
