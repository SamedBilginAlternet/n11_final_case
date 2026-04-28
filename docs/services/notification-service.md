# `notification-service`

**Bu doküman:** RabbitMQ event'lerini dinleyen mail dispatcher.

**Port:** 8086
**DB:** `notificationdb`
**Stack:** Spring Boot 3 + JPA + Flyway + RabbitMQ + JavaMailSender + Thymeleaf
**External:** SMTP server (MailHog dev / Resend prod)

---

## 1. Sorumluluklar

| Event consumed | Routing key | Mail template | When |
|---|---|---|---|
| `OrderConfirmedEvent` | `order.confirmed` | `order-confirmed.html` (pembe-mor) | Ödeme onaylandı |
| `OrderShippedEvent` | `order.shipped` | `order-shipped.html` (mavi, kargo + tracking) | Admin kargoya verdi |
| `OrderDeliveredEvent` | `order.delivered` | `order-delivered.html` (yeşil, yorum çağrısı) | Admin teslim işaretledi |
| `LowStockReportEvent` | `inventory.low-stock-report` | `low-stock-alert.html` (turuncu/kırmızı) | Düşük stok scanner cron tetikledi |

REST endpoint **yok**. Sadece consumer + audit DB.

---

## 2. Niye Ayrı Servis

Mail gönderme order-service veya cart-service içine de gömülebilirdi. Ayrı servis:

- **Decoupling**: Order/payment işlemi mail SMTP failure yüzünden block olmaz. Saga publish
  + return; mail asenkron.
- **Scaling**: Mail volume artarsa sadece notification-service replikası eklenir.
- **Observability**: "Mail neden gitmedi?" sorusu tek servisten cevaplanır.
- **Future expansion**: SMS, push notification, webhook gibi channel'lar buraya eklenir;
  diğer servisler hiç dokunmaz.

---

## 3. Idempotency — `notifications` Audit Table

```sql
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    recipient VARCHAR(160) NOT NULL,
    kind VARCHAR(40) NOT NULL,            -- ORDER_CONFIRMED, ORDER_SHIPPED, ORDER_DELIVERED
    subject VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,          -- SENT, FAILED
    error TEXT,
    correlation_id VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (order_id, kind)
);
```

`UNIQUE(order_id, kind)` — bir sipariş için bir tip mail **bir kez** atılır. RabbitMQ
duplicate event'inde:

```java
public void sendOrderMail(Long orderId, ..., NotificationKind kind, ...) {
    if (repository.existsByOrderIdAndKind(orderId, kind)) {
        log.info("Skip duplicate notification orderId={} kind={}", orderId, kind);
        return;
    }
    
    // ... render template, send mail
    
    Notification record = ...;
    record.setStatus(NotificationStatus.SENT);
    try {
        repository.saveAndFlush(record);    // UNIQUE constraint enforce
    } catch (DataIntegrityViolationException dup) {
        // Race: iki thread aynı anda işledi, ikincisi UNIQUE çarpışması
        log.info("Skip duplicate (race) orderId={} kind={}", orderId, kind);
    }
}
```

İki katman:
1. **Pre-check**: `existsByOrderIdAndKind` — yaygın case (race olmadan), DB query bir kez.
2. **DB-level UNIQUE**: race-safe garantor.

Test: `EmailServiceTest`'te ikinci sendOrderMail çağrısının sessizce skip'lendiği test edilir.

### Niye Stateless İdempotency Değil

Alternatif: messaging library'nin "exactly-once" feature'ı (RabbitMQ confirm + dedupe
plugin). Daha karmaşık + broker-side. Bizim yaklaşım: app-side audit table + DB constraint
= portable, broker-agnostic.

`LowStockReportEvent` için **idempotency yok** — niye:
- Audit row her event için bir tane eklemek anlamsız (hangi field unique olur, eventId mi?).
- Duplicate alert mail rahatsız ama harmful değil.
- Recurring digest = duplicate'in zarar verdiği nokta zaten yok.

---

## 4. Pluggable SMTP — MailHog ↔ Resend

```yaml
spring:
  mail:
    host: ${SMTP_HOST:localhost}
    port: ${SMTP_PORT:1025}
    username: ${SMTP_USERNAME:}
    password: ${SMTP_PASSWORD:}
    properties:
      mail.smtp.auth: ${SMTP_AUTH:false}
      mail.smtp.starttls.enable: ${SMTP_STARTTLS:false}
      mail.smtp.starttls.required: ${SMTP_STARTTLS:false}
```

Aynı kod, env değişimi ile farklı SMTP backend:

| Mode | SMTP_HOST | SMTP_PORT | AUTH | STARTTLS |
|---|---|---|---|---|
| Local dev (MailHog) | `mailhog` | `1025` | false | false |
| Production (Resend) | `smtp.resend.com` | `587` | true | true |

### MailHog (Dev)

```yaml
mailhog:
  image: mailhog/mailhog:v1.0.1
  ports: ["1025:1025", "8025:8025"]
```

Trap SMTP server. Hiçbir mail gerçek dışarı gitmez — `http://localhost:8025` web UI'da
yakalanır. Demo için ideal.

### Resend (Prod)

`smtp.resend.com:587` STARTTLS. Username `"resend"`, password = API key (`re_xxx...`). Sender
domain (`send.samedbilgin.com`) DNS'inde DKIM/SPF kayıtları gerekir.

Kod tarafı **hiç değişmez**. JavaMailSender STARTTLS upgrade'i Spring Boot'un default
implementation'ı handle ediyor.

---

## 5. Thymeleaf Templates

```
src/main/resources/templates/mail/
├── order-confirmed.html
├── order-shipped.html
├── order-delivered.html
└── low-stock-alert.html
```

Inline CSS (mail client compatibility). Gradient header'lar:
- order-confirmed: pink → fuchsia → purple (n11 brand vibe)
- order-shipped: sky → indigo (mavi tones, "yolda")
- order-delivered: emerald → green ("ulaştı")
- low-stock-alert: amber → red ("uyarı")

Variables:
```html
<table>
  <tr th:each="item : ${items}">
    <td th:text="${item.name}">Ürün adı</td>
    <td th:text="${item.stock} + ' adet'"
        th:style="${item.stock == 0} ? 'color:#dc2626;' : 'color:#ea580c;'">0 adet</td>
  </tr>
</table>
```

Conditional inline style — stok=0 kırmızı, >0 turuncu.

`th:href="${storefrontUrl} + '/orders'"` — env-driven CTA URL'leri (storefront vs admin
panel).

---

## 6. RabbitMQ Topology — 4 Queues + 4 DLQs

```java
// RabbitConfig.java
@Bean public Queue notificationOrderConfirmedQueue() { return primaryQueue(...); }
@Bean public Queue notificationOrderShippedQueue() { ... }
@Bean public Queue notificationOrderDeliveredQueue() { ... }
@Bean public Queue notificationLowStockQueue() { ... }

// + matching .dlq + bindings (4 primary bind to saga.exchange,
// 4 dlq bind to saga.exchange.dlx with same routing keys)

private static Queue primaryQueue(String name) {
    return QueueBuilder.durable(name)
            .withArgument("x-dead-letter-exchange", SagaTopology.DLX_EXCHANGE)
            .build();
}
```

Niye 4 ayrı queue, niye `notification.events.q` tek queue ile filter değil:
- Her event tipi için **bağımsız retry / DLQ** isteniyor.
- Order shipped mail başarısız olursa o queue'da park; order confirmed devam eder.
- Concurrent consumer pool tipi başına ayarlanabilir.

Detay: [`docs/messaging.md`](../messaging.md).

---

## 7. Listener Pattern

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderShippedNotifier {
    private final EmailService emailService;
    
    @RabbitListener(queues = SagaTopology.Queue.NOTIFICATION_ORDER_SHIPPED)
    public void onOrderShipped(OrderShippedEvent event) {
        if (event.correlationId() != null) MDC.put(CorrelationId.MDC_KEY, event.correlationId());
        try {
            log.info("OrderShipped received orderId={} carrier={} tracking={}",
                    event.orderId(), event.carrier(), event.trackingNumber());
            Map<String, Object> model = new HashMap<>();
            model.put("orderId", event.orderId());
            model.put("carrier", event.carrier());
            model.put("trackingNumber", event.trackingNumber());
            emailService.sendOrderMail(
                    event.orderId(), event.userId(), event.userEmail(),
                    NotificationKind.ORDER_SHIPPED,
                    "Siparişin kargoya verildi — n11 #" + event.orderId(),
                    "order-shipped",                         // template name
                    model,
                    event.correlationId());
        } finally { MDC.remove(CorrelationId.MDC_KEY); }
    }
}
```

3 listener (confirmed/shipped/delivered) aynı pattern. `LowStockNotifier` benzer ama
`LowStockMailer` kullanır (audit row yok).

### Mail Send Failure → DLQ

`EmailService.sendOrderMail` fırlatır:
```java
} catch (Exception ex) {
    log.error("Failed to send {} mail to {} for orderId={}: {}",
            kind, recipient, orderId, ex.getMessage(), ex);
    record.setStatus(NotificationStatus.FAILED);
    record.setError(ex.getMessage());
    try { repository.saveAndFlush(record); } catch (Exception ignored) { /* best-effort */ }
    throw new MailDispatchException("Failed to send " + kind + " mail", ex);
}
```

Listener `MailDispatchException` yutmaz → Spring AMQP nack atar → broker DLX'e republish →
`notification.order-shipped.q.dlq`'da park. Manuel review + replay flow.

---

## 8. Low-Stock Alert — Special Case

```java
@Component
public class LowStockNotifier {
    private final LowStockMailer mailer;
    private final NotificationProperties properties;
    
    @RabbitListener(queues = SagaTopology.Queue.NOTIFICATION_LOW_STOCK)
    public void onLowStockReport(LowStockReportEvent event) {
        if (event.items() == null || event.items().isEmpty()) return;
        String recipient = properties.adminAlertRecipient();
        if (recipient == null || recipient.isBlank()) {
            log.warn("Low-stock report received but no admin recipient configured — skipping send");
            return;
        }
        mailer.send(recipient, event);
    }
}
```

`ADMIN_ALERT_RECIPIENT` env boşsa **listener çalışır ama send etmez**. Niye:
- Listener bean'i her zaman aktif (queue declare ediliyor, broker tarafı tutarlı).
- Recipient set edilmezse log warning + return — broker'da pile-up yok.
- Set edildiğinde anlık aktive olur.

Alternatif: `@ConditionalOnProperty` recipient'a bağlı. Ama bu queue declare'ini de kapatır
→ broker'da queue eksik → producer publish ederse routing failure. Şu an bean her zaman
aktif, send conditional.

---

## 9. SecurityConfig

`SecurityConfig` yok — notification-service Spring Security starter'ı dahil etmiyor:

```xml
<!-- pom.xml: spring-boot-starter-security YOK -->
```

REST endpoint olmadığı için JWT filter chain'e gerek yok. Actuator endpoint'i `/actuator/health`
public (default). Attack surface minimal.

---

## 10. Klasör Yapısı

```
backend/notification-service/
├── pom.xml
└── src/main/
    ├── java/com/n11/notification/
    │   ├── NotificationApplication.java
    │   ├── config/
    │   │   └── NotificationProperties.java   # from-address, admin-recipient, urls
    │   ├── domain/
    │   │   ├── Notification.java
    │   │   ├── NotificationKind.java
    │   │   └── NotificationStatus.java
    │   ├── messaging/
    │   │   ├── RabbitConfig.java             # 4 queue + 4 dlq
    │   │   ├── OrderConfirmedNotifier.java
    │   │   ├── OrderShippedNotifier.java
    │   │   ├── OrderDeliveredNotifier.java
    │   │   └── LowStockNotifier.java
    │   ├── repository/
    │   │   └── NotificationRepository.java
    │   └── service/
    │       ├── EmailService.java             # transactional mails (UNIQUE audit)
    │       └── LowStockMailer.java           # recurring digest (no audit)
    └── resources/
        ├── application.yml
        ├── db/migration/
        │   └── V1__create_notifications.sql
        └── templates/mail/
            ├── order-confirmed.html
            ├── order-shipped.html
            ├── order-delivered.html
            └── low-stock-alert.html
```

---

## 11. Bilinçli Olarak Yapmadıklarımız

- **SMS / push channel**: Sadece email. Twilio integration eklenirse `SmsNotifier` listener
  + `Sms` audit table.
- **Bulk email (newsletter)**: Yok. Per-event transactional emails.
- **User notification preferences**: User unsubscribe mekanizması yok. Production'da
  `user_preferences` tablosu + `unsubscribe_token` link.
- **Bounce / complaint handling**: Resend webhook → bounce events → mark recipient invalid
  — yok. Future feature.
- **Template editor in admin panel**: Hard-coded HTML files. Future: DB-driven templates +
  WYSIWYG admin UI.
- **Email queue**: SMTP send blocking olduğu için listener thread block oluyor. Yüksek
  volume için ayrı job queue + worker pool eklenir.

---

## İlgili Dokümanlar

- [`docs/messaging.md`](../messaging.md) — DLQ pattern, idempotency
- [`docs/services/order-service.md`](order-service.md) — Order event publisher
- [`docs/services/product-service.md`](product-service.md) — Low-stock scanner publisher
