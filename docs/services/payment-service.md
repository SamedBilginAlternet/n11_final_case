# `payment-service`

**Bu doküman:** Iyzico ödeme entegrasyonu, saga participant.

**Port:** 8085
**DB:** `paymentdb`
**Stack:** Spring Boot 3 + JPA + Flyway + RabbitMQ + Iyzico SDK
**External:** Iyzico API (sandbox/prod)

---

## 1. Sorumluluklar

| Concern | API | Erişim |
|---|---|---|
| Saga consumer: `order.created` → process payment | (RabbitMQ) | — |
| Saga publisher: `payment.succeeded`, `payment.failed` | (RabbitMQ) | — |
| **User-facing endpoint**: yok | — | — |

payment-service **public REST endpoint expose etmez**. Sadece RabbitMQ üzerinden çalışır:
- Sipariş oluşturulduğunda event geldiğinde process eder.
- Sonucu event olarak yayınlar.
- Order-service result'ı dinler.

User'ın hiçbir zaman doğrudan payment-service'e HTTP atması gerekmiyor — checkout ile cart →
order chain'i otomatik tetikler.

### Niye No REST Endpoint

3D Secure callback URL gerekirse `POST /api/payments/callback` eklenir. Şu an demo/sandbox
flow'da gerek yok. Future eklenirse SecurityConfig public-path olarak işaretlenir + Iyzico'nun
imza header'ı verify edilir.

---

## 2. Iyzico Provider

### Pluggable Gateway

```java
public interface PaymentGateway {
    PaymentResult charge(PaymentRequest req);
}

@Component
@ConditionalOnProperty(prefix = "n11.iyzico", name = "enabled", havingValue = "true")
public class IyzicoPaymentGateway implements PaymentGateway { ... }

@Component
@ConditionalOnProperty(prefix = "n11.iyzico", name = "enabled", havingValue = "false", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway { ... }
```

`IYZICO_ENABLED=false` (default) → Mock gateway. Her sipariş %95 succeeds, %5 fails (deterministic
test için seed'lenebilir). Gerçek Iyzico hesabı olmadan akış çalışır.

`IYZICO_ENABLED=true` + sandbox API key → gerçek Iyzico sandbox. Test kart numaraları
documented (demo'da gerek yok).

### Mock'un Faydası

- **Bootcamp grader fresh clone'da çalıştırır** — Iyzico hesabı + KYC süreç gerekmez.
- **Test isolation** — IT'lerde gerçek third-party API'a bağlanmak yavaş + flake-prone.
- **CI'da** Iyzico sandbox'ı blocking dependency olmaz.

Pluggable gateway pattern: aynı yaklaşım chatbot-service'te (`MockChatProvider` /
`GroqChatProvider` / `ClaudeChatProvider`).

---

## 3. Saga Listener — `OrderCreatedListener`

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCreatedListener {
    private final PaymentService paymentService;
    
    @RabbitListener(queues = SagaTopology.Queue.PAYMENT_ORDER_CREATED)
    public void onOrderCreated(OrderCreatedEvent event) {
        if (event.correlationId() != null) MDC.put(CorrelationId.MDC_KEY, event.correlationId());
        try {
            log.info("OrderCreated received orderId={} amount={} {}",
                    event.orderId(), event.totalAmount(), event.currency());
            paymentService.process(event);
        } finally { MDC.remove(CorrelationId.MDC_KEY); }
    }
}
```

### `PaymentService.process` — Idempotent

```java
@Transactional
public void process(OrderCreatedEvent event) {
    // Idempotency check: aynı orderId için Payment zaten var mı?
    Optional<Payment> existing = paymentRepository.findByOrderId(event.orderId());
    if (existing.isPresent() && existing.get().isFinal()) {
        log.info("Skipping duplicate process for orderId={}, status={}",
                event.orderId(), existing.get().getStatus());
        return;
    }
    
    Payment payment = existing.orElseGet(() -> Payment.builder()
            .orderId(event.orderId()).userId(event.userId())
            .amount(event.totalAmount()).currency(event.currency())
            .status(PaymentStatus.PENDING)
            .build());
    
    PaymentResult result = gateway.charge(toRequest(event));
    payment.applyResult(result);
    paymentRepository.save(payment);
    
    String correlationId = event.correlationId();
    if (result.isSuccess()) {
        registerAfterCommit(() -> publisher.publishPaymentSucceeded(
                PaymentSucceededEvent.of(event.orderId(), payment.getId(),
                        result.providerRef(), payment.getAmount(), payment.getCurrency(),
                        correlationId)));
    } else {
        registerAfterCommit(() -> publisher.publishPaymentFailed(
                PaymentFailedEvent.of(event.orderId(), payment.getId(),
                        result.failureReason(), correlationId)));
    }
}
```

### Idempotency Detayı

- Payment her order için **bir kez** oluşturulur (`UNIQUE(order_id)`).
- Duplicate event: `existing.isPresent() && isFinal()` → skip + return.
- Final değilse (`PENDING` veya retry edilebilir failure): yeniden process. Şu an "PENDING"
  state'inde sıkışan payment retry edilebilir; future "stuck-payment recovery" job için
  açık kapı.

`UNIQUE(order_id)` constraint:
```sql
ALTER TABLE payments ADD CONSTRAINT uq_payments_order_id UNIQUE (order_id);
```

DB-level garantor. App-side check **race-safe değil**, INSERT race'i UNIQUE constraint
violation alır → catch + retry-with-existing.

---

## 4. Domain — `Payment`

```sql
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,         -- PENDING, SUCCEEDED, FAILED
    provider_ref VARCHAR(120),           -- Iyzico'nun returned reference
    failure_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### Niye `provider_ref`

Audit + reconciliation. Iyzico'nun verdiği transaction ID'sini sakla — sonradan dispute
veya refund için reference. Bizim DB'de tek source of truth değil — Iyzico Dashboard'da da
görünür.

### Payment State Machine

`Payment.applyResult(PaymentResult)`:
```java
public void applyResult(PaymentResult result) {
    if (this.status != PaymentStatus.PENDING) {
        throw new IllegalStateException("Payment already finalised: " + this.status);
    }
    this.status = result.isSuccess() ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED;
    this.providerRef = result.providerRef();
    this.failureReason = result.failureReason();
}
```

PENDING → SUCCEEDED veya PENDING → FAILED. Terminal once set.

---

## 5. SecurityConfig

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
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

REST endpoint olmadığı için config minimal — sadece actuator açık.

---

## 6. Bilinçli Olarak Yapmadıklarımız

- **3D Secure callback handler**: Iyzico'nun 3DS flow'u `/callback` endpoint gerektirir
  bazı senaryolarda. Şu an non-3DS sandbox.
- **Refund flow**: Payment status SUCCEEDED'den geri dönüş yok. RMA flow eklenirse
  `Payment.refund(...)` + ayrı saga.
- **Multiple payment providers**: Sadece Iyzico (+mock). Stripe, PayPal eklenirse
  `PaymentGateway` interface'e yeni implementasyon.
- **Webhooks (asenkron payment update)**: Iyzico bildirimi anlık. Provider yavaşsa webhook
  pattern eklenir.
- **PCI compliance**: Kart bilgisi backend'e **uğramaz** — Iyzico'nun hosted form sayfasına
  redirect. Bizim DB'de PAN tutulmaz, sadece `provider_ref`.

---

## 7. Klasör Yapısı

```
backend/payment-service/
├── pom.xml
└── src/main/java/com/n11/payment/
    ├── PaymentApplication.java
    ├── api/                              # endpoint yok ama health varsa
    ├── config/
    │   ├── SecurityConfig.java
    │   └── PaymentProperties.java
    ├── domain/
    │   ├── Payment.java
    │   └── PaymentStatus.java
    ├── gateway/
    │   ├── PaymentGateway.java           # interface
    │   ├── PaymentRequest.java
    │   ├── PaymentResult.java
    │   ├── IyzicoPaymentGateway.java     # @ConditionalOnProperty
    │   └── MockPaymentGateway.java       # default
    ├── messaging/
    │   ├── RabbitConfig.java
    │   ├── OrderCreatedListener.java
    │   └── PaymentEventPublisher.java
    ├── repository/
    │   └── PaymentRepository.java
    └── service/
        └── PaymentService.java
```

---

## İlgili Dokümanlar

- [`docs/saga.md`](../saga.md) — Saga akışı
- [`docs/services/order-service.md`](order-service.md) — Saga publisher (order.created) + consumer (payment.*)
- [`docs/messaging.md`](../messaging.md)
