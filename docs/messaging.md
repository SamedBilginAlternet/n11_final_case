# Messaging — RabbitMQ Topology

**Bu doküman:** Projedeki **tek mesajlaşma altyapısı** (RabbitMQ) — niye var, nasıl tasarlandı,
hangi kararların arkasında ne var, nasıl genişletilir, ne yapılmaz.

Audience: bu projeye yeni katılan bir backend mühendisi.

---

## 1. Niye RabbitMQ?

### Neyi çözüyor

Sistem 4 servisin işbirliğine ihtiyaç duyuyor (cart → order → payment → cart-clear), ve
yeni event consumer'lar zaman içinde ekleniyor (notification-service mail, audit, analytics
gibi). İki problem var:

1. **Servisler birbirini direkt çağırırsa** (sync HTTP) → checkout sırasında payment-service
   down ise sipariş **hiç oluşmaz**, kullanıcı hata alır. Order, payment'ın up olmasına bağımlı.
2. **Servisler birbirini bilirse** → her yeni consumer eklediğimde **mevcut servisin koduna**
   dokunmam gerekir. Notification-service eklemek için order-service'e `if (notificationEnabled)
   notify(...)` koymak zorunda kalırım.

RabbitMQ bu iki problemi de çözer:
- **Async**: order-service event'i **publish eder**, transaction'ı kapatır, dönüş yapar.
  payment-service event'i **kendi tempo'sunda** tüketir. Order-service payment'ın o anda
  ayakta olmasıyla ilgilenmez.
- **Decoupled**: notification-service eklemek için sadece **yeni queue + binding** declare
  ettim — order-service'in kodu hiç değişmedi.

### Niye Kafka değil?

Kafka da aynı işi yapar. Niye reddedildi:

| Konu | RabbitMQ | Kafka |
|---|---|---|
| Memory footprint (idle) | ~80MB | ~400MB + ZooKeeper/KRaft |
| Topology declaration | Programatik (`@Bean` ile) | İşletim aracı (`kafka-topics`) |
| Per-message ack | Native | Consumer-side offset commit |
| DLX (dead-letter) | Built-in (broker-level) | App-level retry topic |
| Smaller deployment | Tek container yeter | Broker + ZK/KRaft + schema registry |

Bootcamp scope için 4-5 event'lik bir saga — Kafka **overkill**. RabbitMQ'nun broker-level
DLX'i, programatik topology'si ve düşük RAM ihtiyacı (8GB total bütçemiz var) kazandı.
Real-time stream processing veya log-event volume varsa Kafka daha mantıklı, **biz orada değiliz**.

---

## 2. Tek Exchange — `saga.exchange`

```java
// backend/common/src/main/java/com/n11/common/saga/SagaTopology.java
public static final String EXCHANGE = "saga.exchange";  // topic, durable
public static final String DLX_EXCHANGE = "saga.exchange.dlx";  // topic, durable
```

Bütün domain event'leri **tek bir topic exchange**'ten geçer. Her servis konfigürasyonu
bu exchange'i declare eder (idempotent — RabbitMQ aynı exchange'i tekrar declare etmek
şikayet etmez):

```java
@Bean
public TopicExchange sagaExchange() {
    return new TopicExchange(SagaTopology.EXCHANGE, true /*durable*/, false /*autoDelete*/);
}
```

### Niye topic exchange?

3 alternatif vardı:

- **Direct**: routing key birebir queue ismi — yeni consumer için yeni binding gerekir, **wildcard yok**.
- **Fanout**: routing key yok, her queue tüm mesajları alır — **filtreleme imkansız** (notification-service
  da `payment.succeeded` event'lerini almak zorunda kalırdı, kendi consume mantığında ignore etse bile
  network trafiği boşa gider).
- **Topic**: routing key pattern matching (`order.*`, `*.failed`) — **istenilen consumer istediği
  event'i** dinler.

Topic kazandı çünkü:
- Notification-service `order.confirmed` + `order.shipped` + `order.delivered` dinler ama
  `payment.*` veya `cart.*` ile ilgilenmez. Wildcard-bind ile rahat: tek queue, üç binding.
- Yeni event class'ı eklemek için `RoutingKey` constant'ına ekle, publisher'ı yaz, dinleyenler
  kendi binding'lerini ekler. Exchange tarafında değişiklik yok.

### Niye tek exchange? Niye event'lere göre ayırmadık?

"Per-event exchange" düşünüldü ve reddedildi:
- Her exchange için ayrı bean → kod kalabalığı.
- Cross-event dinleyiciler (örnek: future audit-service her event'i log'lasın) **çoklu exchange**
  bind etmek zorunda — pattern bind tek exchange'te çok daha temiz.
- DLX topology'si exchange başına çoğaltılır — **6 ayrı DLX** demek olur.

Tek `saga.exchange` + routing key pattern → ölçek geldiğinde de çalışır.

---

## 3. Routing Key Konvansiyonu

```
<aggregate>.<verb>[.<context>]
```

Örnekler:
- `order.created` → order-service publish, payment-service consume
- `order.confirmed` → order-service publish, cart-service + notification-service consume
- `order.shipped` / `order.delivered` → order-service publish, notification-service consume
- `order.cancelled` → order-service publish, cart-service (coupon release) consume
- `payment.succeeded` / `payment.failed` → payment-service publish, order-service consume
- `inventory.low-stock-report` → product-service publish, notification-service consume

**Niye verb past-tense?** Domain event = **olmuş bir şey**. "order.create" gibi imperative
form olsa command sanılır. Past-tense okumayı netleştirir: "order created" → bu olay olmuş,
geriye dönüş yok.

**Niye dotted hierarchy?** Topic exchange'in pattern-bind avantajını kullanır:
- `order.*` ile bütün order event'lerini dinleyen bir audit-service sonradan eklenebilir.
- `*.failed` ile başarısızlıkları toplayan bir monitör.
- Hiyerarşik isimlendirme **sonradan eklenecek consumer'ları** düşünmeden kullanım sağlar.

Tüm key'ler `SagaTopology.RoutingKey` constant class'ında (string magic yok):

```java
public static final class RoutingKey {
    public static final String ORDER_CREATED = "order.created";
    public static final String ORDER_CONFIRMED = "order.confirmed";
    public static final String ORDER_CANCELLED = "order.cancelled";
    public static final String ORDER_SHIPPED = "order.shipped";
    public static final String ORDER_DELIVERED = "order.delivered";
    public static final String PAYMENT_SUCCEEDED = "payment.succeeded";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String LOW_STOCK_REPORT = "inventory.low-stock-report";
}
```

Yanlış yazım compile-time'da yakalanır.

---

## 4. Queue Naming — `<consumer-service>.<event>.q`

```
payment.order-created.q          → payment-service, order.created bind
order.payment-succeeded.q        → order-service,   payment.succeeded bind
order.payment-failed.q           → order-service,   payment.failed bind
cart.order-confirmed.q           → cart-service,    order.confirmed bind
cart.order-created.coupon.q      → cart-service,    order.created bind (coupon reservation)
cart.order-cancelled.coupon.q    → cart-service,    order.cancelled bind (coupon release)
notification.order-confirmed.q   → notification-service
notification.order-shipped.q     → notification-service
notification.order-delivered.q   → notification-service
notification.low-stock.q         → notification-service
```

### Niye consumer ön ek?

Bir queue **bir tek consumer servisin sorumluluğunda**. Ön ek bunu netleştirir:
- Operasyonda RabbitMQ Management UI'a bakan biri "kim bu queue'yu boşaltmıyor" sorusunu
  saniyede cevaplayabilir.
- Bir servis silindiğinde hangi queue'ların temizleneceği belli (`cart.*`).
- İki farklı servisin **aynı routing key'i** dinlemesi sorun değil — her birinin **kendi queue'su** var:
  `order.confirmed` hem `cart.order-confirmed.q`'ya hem `notification.order-confirmed.q`'ya gidiyor,
  iki servis bağımsız tüketiyor.

### Niye `.q` suffix?

Operasyonel yanıt: konsol'da `rabbitmqctl list_queues` çıktısında queue olduğu **bir bakışta**
belli olsun. `.dlq` (dead-letter queue) ile parite oluştursun.

---

## 5. Dead-Letter Pattern (DLX)

Her **primary queue'nun** yanında **eşleşen bir `.dlq`** parking lot var:

```
notification.order-shipped.q    ──── nack(requeue=false) ────►  saga.exchange.dlx
                                                                        │
                                                                        │ aynı routing key
                                                                        ▼
                                                          notification.order-shipped.q.dlq
                                                          (manuel inceleme için park)
```

Declare:

```java
private static Queue primaryQueue(String name) {
    return QueueBuilder.durable(name)
            .withArgument("x-dead-letter-exchange", SagaTopology.DLX_EXCHANGE)  // ←
            .build();
}

@Bean public Queue notificationOrderShippedQueue() {
    return primaryQueue(SagaTopology.Queue.NOTIFICATION_ORDER_SHIPPED);
}

@Bean public Queue notificationOrderShippedDlq() {
    return QueueBuilder.durable(SagaTopology.Queue.NOTIFICATION_ORDER_SHIPPED_DLQ).build();
}

@Bean public Binding bindNotificationOrderShippedDlq(Queue dlq, TopicExchange sagaDlxExchange) {
    return BindingBuilder.bind(dlq).to(sagaDlxExchange)
            .with(SagaTopology.RoutingKey.ORDER_SHIPPED);  // aynı key
}
```

### Mesaj DLX'e nasıl düşer

Üç durum:
1. **Listener bir `RuntimeException` fırlatır** ve consumer config'i `defaultRequeueRejected=false`
   (Spring AMQP varsayılanı `true` ama biz override etmiyoruz çünkü DLX kullandığımız için requeue
   kuyruğu doldurmasın istiyoruz; Spring AMQP'nin `acknowledgeMode=AUTO` + `default-requeue-rejected=false`
   kombinasyonu — `application.yml` seviyesinde değiştirilmiyor, default'la geliyor).
2. **TTL süresi dolar** (queue'da `x-message-ttl` set edilmediği için bu projede tetiklenmez).
3. **Queue capacity dolarsa** (set edilmediği için tetiklenmez).

Bizim için **ana yol #1**: bir `EmailService.sendOrderMail()` `MailDispatchException` fırlatır
→ listener nack atar → broker mesajı `saga.exchange.dlx`'e republish eder → routing key korunur
→ `.dlq` kuyruğunda parklanır.

### Niye DLX, basit retry değil?

3 alternatif:

| Yaklaşım | Avantaj | Dezavantaj |
|---|---|---|
| Sonsuz retry (requeue=true) | Basit | **Poison message** kuyruğu kilitler — broken event sonsuz döner, sonraki mesajları bloklar |
| Bounded in-app retry | Kontrollü | Spring Retry config'i her listener'a ayrı; başarısız durumda nereye? |
| **DLX** | Failure izole, manuel inceleme/replay mümkün, broker yönetimi | DLX queue'ları izlemek lazım |

DLX kazandı çünkü:
- **Hiçbir mesaj kaybolmuyor** — bir failure manual replay edilebilir.
- **Poison message diğerlerini blokla­mıyor** — broken event `.dlq`'ya gider, diğerleri akışa devam eder.
- **Operasyonel görünürlük**: RabbitMQ Management UI'da `notification.order-shipped.q.dlq`
  içindeki mesaj sayısı = "sayfa boyutu kadar başarısız mail" alarmı.

---

## 6. Mesaj Şekli — Java Records + JSON

Event'ler `common` modülünde **immutable record**'lar:

```java
public record OrderShippedEvent(
        UUID eventId,
        Instant occurredAt,
        Long orderId,
        Long userId,
        String userEmail,
        String carrier,
        String trackingNumber,
        String correlationId
) {
    public static OrderShippedEvent of(Long orderId, Long userId, String userEmail,
                                       String carrier, String trackingNumber, String correlationId) {
        return new OrderShippedEvent(UUID.randomUUID(), Instant.now(),
                orderId, userId, userEmail, carrier, trackingNumber, correlationId);
    }
}
```

### Niye record?

- Immutable by default — event olmuş bir şeyi temsil eder, mutate etmek anlamlı değil.
- Boilerplate yok — getter, equals, hashCode, toString otomatik.
- Static factory (`of(...)`) eventId+occurredAt'i otomatik doldurur — caller unutamaz.

### Niye JSON, niye binary değil?

Wire formatı:

```java
@Bean
public MessageConverter jsonMessageConverter() {
    return new Jackson2JsonMessageConverter();
}
```

Üç alternatif:

| Format | Boyut | İnsan-okunur? | Şema değişikliği |
|---|---|---|---|
| **JSON** (Jackson) | Orta | ✅ RabbitMQ Management UI'da görünür | Field eklemek geri-uyumlu (Jackson eski field'ları yok sayar) |
| Java serialization | Küçük | ❌ | Kırılgan — class rename uyumsuz |
| Protobuf / Avro | Çok küçük | ❌ | Schema registry gerekir |

JSON kazandı:
- **Debug**: Management UI'da "Get messages" → mesajı **direkt okuyup** anlayabilirsin.
- **Schema evolution**: `OrderShippedEvent`'e `expectedDeliveryDate` eklediğimde eski mesajlar
  null olarak deserialize olur (record'da default null), eski consumer'lar yeni field'ı yok sayar.
- **Cross-language ready**: future Python/Node consumer eklenirse JSON'ı parse etmek 1 satır.

JSON'ın overhead'i (~30-50 byte/event) bizim mesaj volümümüzde **görünmez bir maliyet**.

### Routing Key Nereden Geliyor?

`RabbitTemplate.convertAndSend(exchange, routingKey, payload)` — routing key **publisher'ın
bildiği şey**. Event class'ından otomatik türetilmiyor (refleksiyon ile yapılabilirdi ama
karmaşık). Publisher constant kullanır:

```java
public void publishOrderShipped(OrderShippedEvent event) {
    rabbitTemplate.convertAndSend(SagaTopology.EXCHANGE,
            SagaTopology.RoutingKey.ORDER_SHIPPED, event);
    log.info("Published OrderShipped orderId={} carrier={} tracking={}",
            event.orderId(), event.carrier(), event.trackingNumber());
}
```

Yanlış routing key kullanılırsa derleme yine geçer ama **hiçbir consumer dinlemiyor olur**.
Bu yüzden her publisher metodu `SagaTopology.RoutingKey.X` constant'ı kullanır — yazım hatası
imkansız (constant ismini yazıp IDE autocompletion'ı seçersen).

---

## 7. Consumer Tarafı — `@RabbitListener`

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
            emailService.sendOrderMail(...);
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }
}
```

### Niye annotation'lı listener, manuel `BasicConsume` değil?

Spring AMQP'nin `@RabbitListener` annotation'ı:
- Container yönetimi otomatik (start/stop, connection recovery, prefetch).
- Mesajı otomatik deserialize ediyor (MessageConverter ile).
- Method'a doğrudan typed argument geçiyor — `OrderShippedEvent` parametresi gelir.

Manuel API daha esnek ama **kazanç yok** — bizim case'imiz vanilla.

### `correlationId` MDC propagation'ı

Event'in içindeki `correlationId` field'ı listener tarafından MDC'ye konulur. Bu sayede
`logging.pattern.level: "%5p [%X{correlationId:-}]"` config'i ile her log satırının başında
correlation ID görünür.

`finally` bloğu kritik: aynı thread sonraki bir mesajı işlerken **eski correlationId**'yi
miras alıp logları kirletmesin. `MDC.remove` zorunlu.

### Concurrency — Listener Container Factory

```java
@Bean
public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
        ConnectionFactory cf, MessageConverter converter) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(cf);
    factory.setMessageConverter(converter);
    factory.setConcurrentConsumers(2);
    factory.setMaxConcurrentConsumers(8);  // cart-service
    return factory;
}
```

- **`concurrentConsumers=2`**: queue başına 2 consumer thread aktif.
- **`maxConcurrentConsumers=8`**: yük arttıkça dinamik olarak 8'e kadar çıkar.

Niye 2 değil 1, niye 8 değil 16:
- Mail gönderimi I/O-bound (notification-service): 4 max yeterli, RAM az.
- Coupon reservation atomic UPDATE (cart-service): 8 max ama row-lock contention var, ötesi geri sayım getirmiyor.
- Tek-consumer = sequential processing = güvenli ama yavaş; daha çok concurrency gerekiyorsa
  consumer'ı **idempotent** yapmak zorunlu (zaten yapıyoruz).

---

## 8. Idempotency — Mesaj iki kez gelirse ne olur?

RabbitMQ **at-least-once** delivery garantisi verir. At-most-once isteseydim broker'ın
ack-then-deliver pattern'i gerekiyordu ki **çok daha kırılgan**. Realistic case: aynı
event'in **ikinci kez delivere edilmesi**.

### Bizdeki idempotency mekanizmaları

**1. Notification-service: `UNIQUE(order_id, kind)`**

```sql
CREATE TABLE notifications (
    ...
    UNIQUE (order_id, kind)
);
```

```java
public void sendOrderMail(...) {
    if (repository.existsByOrderIdAndKind(orderId, kind)) {
        log.info("Skip duplicate notification orderId={} kind={}", orderId, kind);
        return;
    }
    // ... send + INSERT
    // Race: iki thread aynı event'i aynı anda işlerse, ikincisi UNIQUE constraint
    // hit'i alır → DataIntegrityViolationException → silently skip
}
```

İki layered guard: önce `existsBy*` (race olmadığında ucuz check), sonra **DB-level
UNIQUE** (race koşulunda atomik garanti).

**2. Cart-service coupon: atomik conditional UPDATE**

```sql
UPDATE coupons
   SET redemptions = redemptions + 1
 WHERE code = :code
   AND active = true
   AND (max_redemptions IS NULL OR redemptions < max_redemptions)
```

Spring Data tarafı:

```java
@Modifying
@Query("UPDATE Coupon c SET c.redemptions = c.redemptions + 1 ...")
int reserveOne(@Param("code") String code);
```

Return değeri 1 ise reserve oldu, 0 ise ya kupon yok ya zaten dolmuş ya pasif. **Race-safe**:
PostgreSQL row-lock atomik UPDATE'i halleder. Duplicate event geldiğinde max'a takılır, 0 döner,
listener log'lar ve geçer.

**3. Order-service state machine: invalid transition reddet**

```java
public void transitionTo(OrderStatus next) {
    if (!isValidNext(next)) {
        throw new IllegalStateException("...");
    }
    // ...
}
```

`payment.succeeded` event'i iki kez gelirse: ilki `AWAITING_PAYMENT → CONFIRMED` yapar.
İkincisi `CONFIRMED → CONFIRMED` denemesi → `IllegalStateException`. Listener nack'lar,
mesaj `.dlq`'ya düşer (bu durumda zararsız bir duplicate ama yine de inceleme için park
edilmesi mantıklı; alternatif: listener "already processed, skip" yapması). Bu repo'da
strict — duplicate state transition fail edilir, çünkü aynı `paymentId`'nin iki kez
gelmesi **upstream bug** sinyali.

---

## 9. Publish-After-Commit Pattern

Publisher event'i **transaction commit'inden sonra** atmalı. Yoksa: transaction rollback
olduysa zaten **olmamış bir şey** event'i atılmış olur — tüketenler hayalet veriyle çalışır.

### Yanlış (anti-pattern)

```java
@Transactional
public Order checkout(...) {
    Order saved = repo.save(order);
    eventPublisher.publishOrderCreated(...);  // ❌ TX commit olmadan publish
    // ... validation throws → rollback, event YİNE de gitti
    return saved;
}
```

### Bizim çözüm

```java
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override
    public void afterCommit() {
        eventPublisher.publishOrderCreated(event);
    }
});
```

`OrderStatusService.transition()` ve `CheckoutService.checkout()` ikisi de bu pattern'i
kullanır. Spring TX manager commit fazına geldiğinde callback'i tetikler. Rollback olursa
callback çalışmaz, event hiç publish edilmez.

### Test ederken

Unit test'te `TransactionSynchronizationManager.initSynchronization()` ile sync'i aktive
edip test sonunda manuel `afterCommit()` tetiklemen gerekiyor:

```java
@BeforeEach
void enableSynchronizations() {
    TransactionSynchronizationManager.initSynchronization();
}

@Test
void markShippedPublishesEvent() {
    service.markShipped(7L, body);
    runAfterCommitHooks();  // manuel TX commit simülasyonu
    verify(publisher).publishOrderShipped(any());
}
```

Detay: [`docs/services/order-service.md`](services/order-service.md)'in test bölümünde.

### Alternatif: Outbox Pattern

Daha sağlam alternatif: **transactional outbox**. Event'i ana TX içinde `outbox` tablosuna
yaz, ayrı bir background poller outbox'tan okuyup RabbitMQ'ya publish eder. Bu repo'da
yok — neden:

- Bir tablo + bir scheduler + bir cleanup job ekstra karmaşıklık.
- TSM-after-commit %99 case'i çözer.
- **Edge case**: TX commit oldu ama publish'ten önce JVM crash → event kayboldu. Nadir,
  monitör edilebilir. Kritik bir e-ticaret olsaydı outbox haklı, bootcamp scope'unda değil.

Doğru yorum: TSM "happy path için yeterli", outbox "kritiklik gerektiren mesajlar için".

---

## 10. Operasyon — Yönetim UI

Compose:
```yaml
rabbitmq:
  image: rabbitmq:3.13-management-alpine
  ports: ["5672:5672", "15672:15672"]
```

`http://localhost:15672` (guest/guest) → topology dashboard:
- Exchanges: `saga.exchange`, `saga.exchange.dlx`
- Queues: 10 primary + 6 .dlq (cart 3 dlq + notification 3 dlq)
- Bindings: routing key matrix
- "Get messages" feature ile herhangi bir queue'dan mesaj peek edebilirsin (test için).

### Tipik incelemeler

**"Mesaj kayboluyor mu?"**
1. RabbitMQ UI → Queues → ilgili `.q` → "Get messages" → ham JSON'ı oku → routing key + payload doğru mu?
2. Producer log'unda `Published OrderShipped orderId=X` line'ı var mı?
3. `.dlq` boyutu sıfır değilse → `.dlq`'ya bak, hangi mesaj başarısız oldu?

**"Listener çalışıyor mu?"**
1. `Channels` tab → consumer count > 0 mu? (bizimki concurrentConsumers=2 başlangıçta).
2. Pod log'larında `@RabbitListener` boot mesajı var mı? (`Subscribing to queue ... with consumerTag ...`).

**"Replay yapmak istiyorum"**
1. UI'da `.dlq`'dan mesajları "Move" feature'ı ile primary queue'ya yeniden yolla.
2. Veya `rabbitmqadmin` CLI ile script.

---

## 11. Yeni Event Eklemek — Adım Listesi

`order.refunded` (örnek) event'i eklemek istiyorsun:

1. **`SagaTopology.RoutingKey.ORDER_REFUNDED = "order.refunded"`** ekle.
2. **`OrderRefundedEvent.java` record'unu** `common/event/` altında yaz.
3. **Publisher tarafı**: ilgili servis (örn. order-service) için
   `OrderEventPublisher.publishOrderRefunded(...)` ekle. `convertAndSend(EXCHANGE, ROUTING_KEY, event)`.
4. **Publish-after-commit** kullan — `TransactionSynchronizationManager.registerSynchronization`.
5. **Consumer servis(ler)**: 
   - `SagaTopology.Queue.<service>_ORDER_REFUNDED = "<service>.order-refunded.q"`
   - `SagaTopology.Queue.<service>_ORDER_REFUNDED_DLQ = ... + ".dlq"`
   - `RabbitConfig`'te primary queue + DLQ + iki binding (saga + dlx).
   - `@RabbitListener(queues = ...)` ile dinleyici.
6. **Idempotency düşün**: aynı event iki kez gelirse ne olur? Audit tablosu? UNIQUE constraint?
   Atomik UPDATE? Tek consumer thread (yavaş ama güvenli)?
7. **Test**: Mockito ile publisher unit-test'i + consumer için boş listener stub'ı yeterli.
   Integration test gerekmiyor (RabbitMQ'yu test container'da ayağa kaldırmak overkill).

---

## 12. Bilinçli Olarak Yapmadıklarımız

- **Schema registry yok**: Event class'ı `common`'da, derleme zamanı garantisi yeterli. Cross-language
  consumer eklenince Avro + registry düşünürüz.
- **Per-message TTL yok**: `x-message-ttl` set etmiyoruz — mesaj silmek için gerekli senaryo yok.
  Eğer `notification.order-shipped.q` çok şişerse 1-saat TTL ekleyip eski mesajları DLX'e
  gönderebiliriz.
- **Lazy queue mode yok**: Default RAM-tutar queue'lar. Volume büyürse `x-queue-mode=lazy` ile
  disk'e yaz.
- **Cluster yok**: Tek RabbitMQ node. HA/clustering gereken volume bizde yok.
- **Federation/shovel yok**: Single broker.
- **Encrypted payload yok**: TLS-at-rest yok. Compose network internal — production'da Caddy
  reverse proxy ile TLS ama broker-to-service hop hala plain. Sensitive PII (e-mail, telefon)
  event'lerde **gerekli minimum** tutuluyor; tam adres veya kart bilgisi event'te asla yok.
- **Saga state tracker yok**: Choreography olduğu için saga başına merkezi state yok. Sorguya
  ihtiyaç olursa correlation ID ile `Order` tablosundaki `status + timeline` join.

---

## 13. Cheat Sheet

| İhtiyaç | Yapılacak |
|---|---|
| Yeni domain event | `RoutingKey` constant + record + publisher.convertAndSend |
| Yeni consumer servis | Primary queue + DLQ + 2 binding + `@RabbitListener` |
| Mesaj kayboldu | Management UI'da `.dlq` kontrolü |
| Sequential processing | `concurrentConsumers=1` factory |
| Yüksek throughput | `maxConcurrentConsumers` artır + idempotency garanti et |
| Mesaj boyutu büyük | Ham binary değil, S3 link gönder, consumer indirir |
| Cross-domain replay | `.dlq` mesajlarını UI'dan move-to-primary |

---

## İlgili Dokümanlar

- [`docs/saga.md`](saga.md) — Choreography saga akışı (waterfall + idempotency notları)
- [`docs/services/order-service.md`](services/order-service.md) — Order publisher + state machine detayı
- [`docs/services/notification-service.md`](services/notification-service.md) — Mail consumer detayı
- [`docs/services/cart-service.md`](services/cart-service.md) — Coupon saga participant + atomik UPDATE
- [`docs/services/payment-service.md`](services/payment-service.md) — Payment publisher + Iyzico
