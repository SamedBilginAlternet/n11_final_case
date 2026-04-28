# `order-service`

**Bu doküman:** Sipariş checkout, lifecycle state machine, saga publisher, admin lifecycle
yönetimi, co-purchase signal sağlayıcı.

**Port:** 8084
**DB:** `orderdb`
**Stack:** Spring Boot 3 + JPA + Flyway + RabbitMQ (publish + consume)
**Cross-service**: cart-service (cart fetch), auth-service (address fetch)

---

## 1. Sorumluluklar

| Concern | Endpoint(ler) | Erişim |
|---|---|---|
| Checkout | `POST /api/orders/checkout` | Auth |
| User's orders | `GET /api/orders`, `GET /api/orders/{id}` | Auth (user-scoped) |
| **Admin: list all** | `GET /api/orders/admin?status=...` | ADMIN |
| **Admin: detail** | `GET /api/orders/admin/{id}` | ADMIN |
| **Admin: lifecycle** | `POST /api/orders/{id}/processing\|shipped\|delivered` | ADMIN |
| **Admin: dashboard metrics** | `GET /api/orders/admin/metrics?days=30` | ADMIN |
| **Internal**: co-purchase signal | `GET /internal/co-purchases?productId=X` | Cluster-internal |
| **Saga publisher**: order.created, order.confirmed, order.cancelled, order.shipped, order.delivered | (RabbitMQ) | — |
| **Saga consumer**: payment.succeeded, payment.failed | (RabbitMQ) | — |

---

## 2. Order Domain — State Machine

### Schema

```sql
-- V3__shipping_and_tracking.sql (after V1 + V2 evolved through this session)
ALTER TABLE orders ADD COLUMN shipping_recipient VARCHAR(120);
ALTER TABLE orders ADD COLUMN shipping_phone VARCHAR(20);
-- ... shipping fields
ALTER TABLE orders ADD COLUMN confirmed_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN processing_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN shipped_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN delivered_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN cancelled_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN carrier VARCHAR(60);
ALTER TABLE orders ADD COLUMN tracking_number VARCHAR(80);
```

`OrderStatus` enum:
```java
public enum OrderStatus {
    PENDING, AWAITING_PAYMENT,
    CONFIRMED, PROCESSING, SHIPPED, DELIVERED,
    CANCELLED
}
```

### State Machine — `Order.transitionTo`

```java
public void transitionTo(OrderStatus next) {
    if (!isValidNext(next)) {
        throw new IllegalStateException(
                "Invalid transition: " + this.status + " → " + next);
    }
    Instant now = Instant.now();
    switch (next) {
        case AWAITING_PAYMENT -> {}    // no extra timestamp
        case CONFIRMED   -> { if (this.confirmedAt == null)  this.confirmedAt = now; }
        case PROCESSING  -> { if (this.processingAt == null) this.processingAt = now; }
        case SHIPPED     -> { if (this.shippedAt == null)    this.shippedAt = now; }
        case DELIVERED   -> { if (this.deliveredAt == null)  this.deliveredAt = now; }
        case CANCELLED   -> { if (this.cancelledAt == null)  this.cancelledAt = now; }
        default -> {}
    }
    this.status = next;
}

private boolean isValidNext(OrderStatus to) {
    return switch (this.status) {
        case PENDING          -> to == OrderStatus.AWAITING_PAYMENT;
        case AWAITING_PAYMENT -> to == OrderStatus.CONFIRMED        || to == OrderStatus.CANCELLED;
        case CONFIRMED        -> to == OrderStatus.PROCESSING       || to == OrderStatus.CANCELLED;
        case PROCESSING       -> to == OrderStatus.SHIPPED          || to == OrderStatus.CANCELLED;
        case SHIPPED          -> to == OrderStatus.DELIVERED;
        case DELIVERED, CANCELLED -> false;   // terminal
    };
}
```

### Niye State Machine Domain'de, Yardımcı Class'ta Değil

`Order` entity'sinin invariant'ı: status sadece `transitionTo()` ile değişir. `setStatus(...)`
public değil — direkt set tehlikeli. Domain-driven design: business rule entity'nin
sahibinin kendisi olmalı.

Yardımcı class (`OrderStateMachine`) ile yapsam:
- `Order.setStatus(...)` public olmak zorunda → kötü amaçlı kullanım açık.
- "Where does the rule live?" — Order'a bakan biri rule'u görmez.

Method on entity = single source of truth.

### Niye Timestamp Kolonları, Audit Tablosu Değil

İki seçenek:
1. `orders` tablosunda `confirmed_at`, `shipped_at`, vb. (bizim).
2. Ayrı `order_status_history` tablosu, her transition bir row.

Audit table daha "normalize" ama:
- Frontend timeline render için her ürün detay'da 1 query + JOIN.
- "Bu sipariş hangi tarihte shipped oldu?" sorusu sık.
- Status state machine zaten transition'ları engelliyor → "geçmişe rewind" senaryosu yok.

Denormalize timestamp = read-fast, dashboard query'leri direkt.

Trade-off: ileride "iptal edilmiş bir CONFIRMED sipariş" gibi double-state dökümanlamak
gerekirse audit table eklenir. Şu an gerek yok.

---

## 3. Checkout Akışı

```java
@Transactional
public OrderDto checkout(Long userId, String userEmail, Long addressId) {
    // 1. Snapshot cart
    CartDto cart = cartClient.getCart(userId);   // cross-service HTTP
    if (cart.items().isEmpty()) throw new ResponseStatusException(BAD_REQUEST, "Sepet boş");
    
    // 2. Snapshot address
    AddressDto address = addressClient.get(addressId, currentBearerToken());  // cross-service
    
    // 3. Build Order entity with PENDING status, snapshotted shipping
    Order order = Order.builder()
            .userId(userId).userEmail(userEmail)
            .totalAmount(cart.total()).currency("TRY")
            .status(OrderStatus.PENDING)
            .couponCode(cart.couponCode())
            .shippingRecipient(address.recipientName())
            .shippingLine1(address.line1())
            // ... shipping fields
            .build();
    
    // 4. Snapshot items
    cart.items().forEach(item -> order.addItem(OrderItem.builder()
            .productId(item.productId()).productName(item.productName())
            .quantity(item.quantity()).unitPrice(item.unitPrice())
            .build()));
    
    // 5. Save (status PENDING)
    Order saved = orderRepository.save(order);
    
    // 6. Transition to AWAITING_PAYMENT
    saved.transitionTo(OrderStatus.AWAITING_PAYMENT);
    
    // 7. Build event
    String correlationId = MDC.get(CorrelationId.MDC_KEY);
    OrderCreatedEvent event = OrderCreatedEvent.of(
            saved.getId(), userId, userEmail,
            saved.getTotalAmount(), saved.getCurrency(),
            saved.getItems().stream().map(...).toList(),
            saved.getCouponCode(), correlationId);
    
    // 8. Publish AFTER COMMIT
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCommit() {
            eventPublisher.publishOrderCreated(event);
        }
    });
    
    return mapper.toDto(saved);
}
```

### Niye Snapshot Cart + Address

Cart ve address servisleri **kendi** DB'lerinde değişiklik yapabilir (ürün fiyatı update,
adres edit). Sipariş **o anki durum** ile bağlı kalmalı. Snapshot:
- `productName` ve `unitPrice` order_items'a kopyalanır → ürün adı/fiyatı sonradan değişse
  bile sipariş geçmişi tutarlı.
- Shipping address kolonları kopyalanır → user adresini silebilir, sipariş kayıt'ı bozulmaz.

### Niye PENDING → AWAITING_PAYMENT Transition

`PENDING` çok kısa-ömürlü bir durum — `Order.builder()` default ile. Sonra `transitionTo(AWAITING_PAYMENT)`
ile state machine'in legitimate ilk adımına geçer. Bu sayede:
- State machine her zaman entry point'ten başlar.
- Future'ta PENDING'den **başka transition** açılırsa (örn. `validate-stock` adımı), aynı
  kalıp.

### Niye Publish-After-Commit

Kritik: rollback halinde event göndermek istemiyoruz. Detay: [`docs/messaging.md`](../messaging.md#9-publish-after-commit-pattern).

---

## 4. Saga Consumer — Payment Result

```java
@RabbitListener(queues = SagaTopology.Queue.ORDER_PAYMENT_SUCCEEDED)
public void onPaymentSucceeded(PaymentSucceededEvent event) {
    if (event.correlationId() != null) MDC.put(CorrelationId.MDC_KEY, event.correlationId());
    try {
        Order order = orderRepository.findById(event.orderId()).orElseThrow(...);
        try {
            order.transitionTo(OrderStatus.CONFIRMED);
        } catch (IllegalStateException ignored) {
            // duplicate event, already CONFIRMED — listen log + ack
            log.info("Duplicate payment.succeeded for orderId={}, current status={}",
                    event.orderId(), order.getStatus());
            return;
        }
        Order saved = orderRepository.save(order);
        
        // Publish OrderConfirmed
        OrderConfirmedEvent confirmed = OrderConfirmedEvent.of(...);
        TransactionSynchronizationManager.registerSynchronization(...);
    } finally { MDC.remove(CorrelationId.MDC_KEY); }
}

@RabbitListener(queues = SagaTopology.Queue.ORDER_PAYMENT_FAILED)
public void onPaymentFailed(PaymentFailedEvent event) {
    // Symmetric: transition to CANCELLED, publish OrderCancelledEvent
}
```

### Idempotency: Double-Process Tolerant

`payment.succeeded` event RabbitMQ tarafından redeliver olabilir (consumer crash mid-process).
İkinci işlem:
- `transitionTo(CONFIRMED)` ikinci kez → `IllegalStateException` (CONFIRMED is terminal-like).
- Catch'le yutuyoruz, log'luyoruz, return. Ack normal gider.

Alternatif: catch yapmadan exception fırlat → DLQ'ya parking. Tartışmalı tasarım — 
`IllegalStateException` zaman zaman duplicate'i değil **logic bug'ı** da işaret edebilir
(payment-service yanlış orderId yolladı). Şu an "duplicate ignore" kabul ediliyor — log'da
şüpheli durum görünür.

---

## 5. Admin Lifecycle Transitions

```java
@Service
public class OrderStatusService {
    @Transactional public OrderDto markProcessing(Long orderId) {
        return transition(orderId, OrderStatus.PROCESSING, null);
    }
    @Transactional public OrderDto markShipped(Long orderId, StatusUpdateRequest body) {
        return transition(orderId, OrderStatus.SHIPPED, body);
    }
    @Transactional public OrderDto markDelivered(Long orderId) {
        return transition(orderId, OrderStatus.DELIVERED, null);
    }
    
    private OrderDto transition(Long orderId, OrderStatus next, StatusUpdateRequest body) {
        Order order = repository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Order not found"));
        try {
            order.transitionTo(next);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(CONFLICT, ex.getMessage());
        }
        if (next == OrderStatus.SHIPPED && body != null) {
            order.setCarrier(body.carrier());
            order.setTrackingNumber(body.trackingNumber());
        }
        Order saved = repository.save(order);

        String correlationId = MDC.get(CorrelationId.MDC_KEY);
        if (next == OrderStatus.SHIPPED) {
            OrderShippedEvent event = OrderShippedEvent.of(
                    saved.getId(), saved.getUserId(), saved.getUserEmail(),
                    saved.getCarrier(), saved.getTrackingNumber(), correlationId);
            registerAfterCommit(() -> eventPublisher.publishOrderShipped(event));
        } else if (next == OrderStatus.DELIVERED) {
            OrderDeliveredEvent event = OrderDeliveredEvent.of(...);
            registerAfterCommit(() -> eventPublisher.publishOrderDelivered(event));
        }
        return mapper.toDto(saved);
    }
}
```

### Niye SHIPPED + DELIVERED Event, PROCESSING Event Yok

User'a faydalı bilgi olan transitions için event:
- **CONFIRMED**: ödeme onaylandı, sipariş işlemde — onay maili.
- **SHIPPED**: kargoya verildi, takip numarası ile — kargo maili.
- **DELIVERED**: ulaştı — yorum çağrısı maili.

PROCESSING admin'in iç durumu — user için gözle görülür değişiklik yok, mail spam olur. Bu
yüzden event publish edilmez.

### After-Commit Pattern Burada da

```java
private void registerAfterCommit(Runnable action) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCommit() { action.run(); }
    });
}
```

DRY: aynı pattern checkout + status transition.

---

## 6. Admin Endpoints

### Listing — `GET /api/orders/admin?status=...`

```java
@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/admin")
public List<OrderDto> adminList(@RequestParam(required = false) OrderStatus status,
                                @PageableDefault(size = 20) Pageable pageable) {
    return repository.findAllByOptionalStatus(status, pageable).map(mapper::toDto).getContent();
}
```

Repository:
```java
@Query("""
        select o from Order o
        where (:status is null or o.status = :status)
        order by o.createdAt desc
        """)
Page<Order> findAllByOptionalStatus(@Param("status") OrderStatus status, Pageable pageable);
```

`:status is null or` pattern — tek query iki use case (filtered + unfiltered).

### Niye `/admin` Path, `/orders` Üzerine Param Değil

Alternatif: `GET /api/orders?adminMode=true` veya `?ownerScope=all`. Reddedildi:
- Param-bazlı flag karışıklık yaratır — same path different behavior based on param.
- `/admin` ayrı path explicit.
- Future: `/admin` altında daha fazla admin-only endpoint eklenebilir (`/admin/refund`, `/admin/notes`).

### Detail — `/admin/{id}` (No User Scope)

```java
@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/admin/{id}")
public OrderDto adminGet(@PathVariable Long id) {
    return repository.findById(id)
            .map(mapper::toDto)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Order not found"));
}
```

`findByIdAndUserId` değil sadece `findById` — admin scope filter olmadan görür.

---

## 7. Dashboard Metrics

`OrderMetricsService.compute(days)` üç şey üretir:

1. **Summary**: bugünkü sipariş sayısı, bugünkü ciro, pending sipariş, total ciro.
2. **Daily series** (last `days`): her gün için `{date, orderCount, revenue}` — gap-fill ile
   eksik günler 0 doldurulur.
3. **Status breakdown**: `{status, count}` — donut chart için.

```java
@SuppressWarnings("unchecked")
private List<OrderMetricsDto.DailyPoint> daily(Instant startOfWindow, int window) {
    Query q = em.createNativeQuery("""
            SELECT date_trunc('day', created_at)::date AS d,
                   count(*) AS cnt,
                   COALESCE(SUM(CASE WHEN status IN ('CONFIRMED','PROCESSING','SHIPPED','DELIVERED')
                                     THEN total_amount ELSE 0 END), 0) AS rev
              FROM orders
             WHERE created_at >= :since
             GROUP BY d
             ORDER BY d
            """);
    // ...
    
    // Gap-fill: missing days as zeros so the line chart has no gaps
    Map<LocalDate, OrderMetricsDto.DailyPoint> byDate = ...;
    List<OrderMetricsDto.DailyPoint> out = new ArrayList<>(window);
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    for (int i = window - 1; i >= 0; i--) {
        LocalDate d = today.minusDays(i);
        out.add(byDate.getOrDefault(d, new OrderMetricsDto.DailyPoint(d, 0L, BigDecimal.ZERO)));
    }
    return out;
}
```

### Niye Native SQL

`date_trunc('day', created_at)::date` JPQL'de yok. SQL'in built-in date arithmetic'ini
kullanmak doğal.

### Niye Revenue = CONFIRMED+

PENDING/AWAITING_PAYMENT hala ödeme gözünde, **gerçek ciro değil**. CANCELLED iade edildi.
"Ciro" muhasebede onaylı + ileri durumlar.

---

## 8. Co-Purchase Endpoint — Internal API

```java
@RestController
@RequestMapping("/internal/co-purchases")
@RequiredArgsConstructor
public class InternalController {
    @GetMapping
    public List<CoPurchaseDto> coPurchases(@RequestParam Long productId,
                                           @RequestParam(defaultValue = "10") int limit) {
        Instant since = Instant.now().minus(LOOKBACK_DAYS, ChronoUnit.DAYS);
        int capped = Math.min(Math.max(limit, 1), 50);
        return repository.findCoPurchaseCandidates(productId, since, PageRequest.of(0, capped))
                .stream()
                .map(row -> new CoPurchaseDto(
                        ((Number) row[0]).longValue(),
                        (String) row[1],
                        ((Number) row[2]).longValue()))
                .toList();
    }
}
```

Repository:
```java
@Query("""
        select co.productId, co.productName, count(co) as cnt
        from OrderItem self
        join self.order o
        join o.items co
        where self.productId = :productId
          and co.productId <> :productId
          and o.status <> com.n11.order.domain.OrderStatus.CANCELLED
          and o.createdAt >= :since
        group by co.productId, co.productName
        order by cnt desc
        """)
List<Object[]> findCoPurchaseCandidates(@Param("productId") Long productId,
                                        @Param("since") Instant since,
                                        Pageable pageable);
```

### Niye `/internal/`, `/api/` Değil

api-gateway sadece `/api/orders/**`'i route'lar. `/internal/**` external'a invisible.
SecurityConfig:

```java
.requestMatchers("/internal/**").permitAll()
```

Network izolasyonu = trust boundary. Detay: [`docs/recommendations.md`](../recommendations.md#niye-order-servicete-endpoint-niye-direkt-db-query-değil).

---

## 9. SecurityConfig

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // @PreAuthorize için zorunlu
public class SecurityConfig {
    @Bean public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) {
        http
            .csrf(csrf -> csrf.disable())
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                .requestMatchers("/internal/**").permitAll()    // cluster-internal
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

`@PreAuthorize("hasRole('ADMIN')")` admin endpoint'lerinde — `@EnableMethodSecurity` olmadan
sessizce no-op.

---

## 10. Klasör Yapısı

```
backend/order-service/
├── pom.xml
└── src/main/java/com/n11/order/
    ├── OrderApplication.java
    ├── api/
    │   ├── OrderController.java          # checkout + user list + admin lifecycle + admin metrics
    │   ├── StatusUpdateRequest.java
    │   ├── CheckoutRequest.java
    │   ├── admin/
    │   │   ├── OrderMetricsDto.java
    │   │   └── OrderMetricsService.java
    │   ├── internal/
    │   │   ├── InternalController.java   # /internal/co-purchases
    │   │   └── CoPurchaseDto.java
    │   ├── dto/                          # OrderDto + nested ShippingDto/TrackingDto/TimelineDto
    │   └── mapper/                       # MapStruct
    ├── client/
    │   ├── CartClient.java
    │   └── AddressClient.java
    ├── config/
    │   ├── SecurityConfig.java
    │   └── OrderProperties.java
    ├── domain/
    │   ├── Order.java                    # state machine + transitionTo
    │   ├── OrderItem.java
    │   └── OrderStatus.java
    ├── repository/
    │   └── OrderRepository.java          # findByIdAndUserId, findAllByOptionalStatus, co-purchase
    ├── service/
    │   ├── CheckoutService.java          # snapshot + transition + after-commit publish
    │   └── OrderStatusService.java       # admin lifecycle + after-commit publish
    └── messaging/
        ├── RabbitConfig.java
        ├── OrderEventPublisher.java      # 5 publish methods
        └── PaymentResultListener.java    # consume payment.succeeded/failed
```

---

## 11. Bilinçli Olarak Yapmadıklarımız

- **Multi-shipping per order**: Tek adrese gönderim varsayımı.
- **Partial fulfillment**: Tüm sipariş tek seferde shipped/delivered. Item-level ayrı
  shipping yok.
- **Returns / refunds**: Yok. RMA flow eklenir → state machine'e `RETURN_REQUESTED`,
  `RETURNED` eklenir, ayrı saga.
- **Order edit (post-checkout)**: Status değiştirilmedikçe edit yok. CONFIRMED'den sonra
  user iptal edebilir mi? Hayır, sadece admin CANCELLED yapar (henüz UI'sı yok admin
  tarafında — backend `transitionTo(CANCELLED)` mevcut).
- **Stock decrement on confirmation**: Şu an stok cart'a eklemekte değil, sipariş onayında
  da değil — manuel admin/seed. Production'da CONFIRMED state'inde `UPDATE products SET stock=stock-Q`
  saga step'i gerekir.

---

## İlgili Dokümanlar

- [`docs/messaging.md`](../messaging.md) — Event publishing, after-commit pattern
- [`docs/saga.md`](../saga.md) — Choreography saga akışı
- [`docs/services/cart-service.md`](cart-service.md) — Saga consumer counterpart
- [`docs/services/payment-service.md`](payment-service.md) — Payment publisher
- [`docs/services/notification-service.md`](notification-service.md) — Mail consumer
- [`docs/recommendations.md`](../recommendations.md) — Co-purchase consumer
