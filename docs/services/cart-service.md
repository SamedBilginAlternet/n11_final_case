# `cart-service`

**Bu doküman:** Sepet, wishlist, kupon/kampanya engine, saga participant.

**Port:** 8083
**DB:** `cartdb`
**Stack:** Spring Boot 3 + JPA + Flyway + Redis cache + RabbitMQ (publish + consume)

---

## 1. Sorumluluklar

| Concern | Endpoint(ler) | Erişim |
|---|---|---|
| Cart CRUD | `GET/POST/DELETE /api/cart`, `PUT /api/cart/items/{productId}` | Auth (JWT) |
| Cart preview (guest) | `GET /api/cart?guestToken=...` | Guest token |
| Apply / remove coupon | `POST /api/cart/coupon`, `DELETE /api/cart/coupon` | Auth |
| Wishlist | `GET/POST/DELETE /api/wishlist` | Auth |
| **Admin: coupon CRUD** | `GET/POST/PUT/DELETE /api/coupons[/{id}]` | ADMIN |
| **Saga consumer**: `order.created` → reserve coupon | (RabbitMQ) | — |
| **Saga consumer**: `order.cancelled` → release coupon | (RabbitMQ) | — |
| **Saga consumer**: `order.confirmed` → clear cart | (RabbitMQ) | — |

cart-service hem **publisher** (cart-side events şu an yok), hem **consumer** (3 saga
participant). Discount engine'i + saga compensation'ı en iddialı kısımları.

---

## 2. Domain — `Cart`, `CartItem`, `Coupon`, `Campaign`, `WishlistItem`

### Cart Topology

```sql
-- V1
CREATE TABLE carts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,                   -- nullable for guest
    guest_token UUID,                 -- nullable for authenticated
    coupon_code VARCHAR(40),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (user_id),                 -- partial: WHERE user_id IS NOT NULL
    UNIQUE (guest_token)              -- partial: WHERE guest_token IS NOT NULL
);

CREATE TABLE cart_items (
    id BIGSERIAL PRIMARY KEY,
    cart_id BIGINT NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    UNIQUE (cart_id, product_id)
);
```

### Niye guest_token + user_id ikisi birden, "ya / ya"

Guest user (login etmemiş) cart'a ürün ekleyebilir → frontend localStorage'da `guestToken`
UUID üretir, her cart request'te `?guestToken=...` query param ile gönderir. Login olduğunda
backend guest cart'ı user'a **merge** eder.

İki nullable kolon + partial UNIQUE — hem guest hem user için aynı tablo. Tasarım kararı:
ayrı tablo (`guest_carts`) düşünüldü, reddedildi:
- Merge logic'i iki tablo arasında karmaşık.
- Tek tablo + user_id NULL pattern → query basit.

### Wishlist

```sql
-- V6__wishlist.sql
CREATE TABLE wishlist_items (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, product_id)
);
```

UNIQUE — bir kullanıcı bir ürünü iki kez fav'lemez.

### Coupon

```sql
CREATE TABLE coupons (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    label VARCHAR(160) NOT NULL,
    type VARCHAR(20) NOT NULL,         -- FIXED, PERCENT
    value NUMERIC(12,2) NOT NULL,
    min_cart_total NUMERIC(12,2),
    max_redemptions INTEGER,
    redemptions INTEGER NOT NULL DEFAULT 0,
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

`isValidAt(Instant)` method:
```java
public boolean isValidAt(Instant t) {
    if (!Boolean.TRUE.equals(active)) return false;
    if (validFrom != null && t.isBefore(validFrom)) return false;
    if (validUntil != null && t.isAfter(validUntil)) return false;
    if (maxRedemptions != null && redemptions >= maxRedemptions) return false;
    return true;
}
```

### Campaign — Otomatik Kampanya

```sql
CREATE TABLE campaigns (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    label VARCHAR(160) NOT NULL,
    type VARCHAR(40) NOT NULL,    -- PERCENT_OFF_CART, BUY_X_PAY_Y
    priority INTEGER NOT NULL,    -- daha düşük = önce uygulanır
    value NUMERIC(12,2),
    pay_y INTEGER,                -- BUY_X_PAY_Y için (örn. 4 al 3 öde → x=4, y=3)
    min_cart_total NUMERIC(12,2),
    valid_from TIMESTAMPTZ,
    valid_until TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT TRUE
);
```

### CouponRedemption — Audit + Saga Anti-Replay

```sql
CREATE TABLE coupon_redemptions (
    id BIGSERIAL PRIMARY KEY,
    coupon_id BIGINT NOT NULL REFERENCES coupons(id),
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    redeemed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (coupon_id, order_id)
);
```

UNIQUE(coupon_id, order_id) — saga duplicate event'inde ikinci INSERT fail eder, idempotent.

---

## 3. Discount Engine — Strategy + Chain Pattern

```
Cart  →  DiscountEngine  →  Quote
            │
            ├─ subtotal = Σ unitPrice × qty
            ├─ activeCoupon (cart.coupon_code'a göre DB lookup)
            ├─ activeCampaigns (DB, validFrom/Until içinde, active=true)
            │
            └─ for each DiscountStrategy in priority order:
                  strategy.evaluate(QuoteContext) → AppliedDiscount?
                                                  └─ amount, label, source
            
            Quote = subtotal, totalDiscount, total, appliedDiscounts[]
```

### `DiscountStrategy` Interface

```java
public interface DiscountStrategy {
    int priority();   // küçük önce
    Optional<AppliedDiscount> evaluate(QuoteContext ctx);
}
```

### Implementasyonlar

| Strategy | Priority | Logic |
|---|---|---|
| `BuyXPayYStrategy` | 100 | "4 al 3 öde" — sepetteki ürünler için en pahalı Y'sini ücretsiz |
| `PercentOffCartStrategy` | 200 | "%20 kupon" — subtotal × value/100 |
| `FixedAmountCouponStrategy` | 300 | "100 TL kupon" — subtotal'dan düşer |

Priority sırası önemli: `BuyXPayYStrategy` önce → ücretsiz item'i belirle, **sonra** percent
indirim **kalan** subtotal'a uygulanır. Sıra değişince matematik bozulur.

### Chain — Sıralı Uygulama

```java
public Quote computeQuote(QuoteContext ctx) {
    BigDecimal subtotal = ctx.cartItems().stream()
            .map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    
    List<AppliedDiscount> applied = new ArrayList<>();
    BigDecimal runningTotal = subtotal;
    
    List<DiscountStrategy> orderedStrategies = strategies.stream()
            .sorted(Comparator.comparingInt(DiscountStrategy::priority))
            .toList();
    
    for (DiscountStrategy s : orderedStrategies) {
        QuoteContext narrowed = ctx.withRunningTotal(runningTotal);
        Optional<AppliedDiscount> result = s.evaluate(narrowed);
        result.ifPresent(d -> {
            applied.add(d);
            // running total update
        });
    }
    return new Quote(subtotal, totalDiscount, total, applied);
}
```

Niye chain (kümülatif): "kupon + kampanya birlikte uygulanabilir" iş kuralı. User aynı anda
hem coupon kullanabilir hem de aktif kampanyadan yararlanabilir → discount kümülatif.

### Re-quote on Every Mutation

`POST /api/cart/items` veya `DELETE` veya kupon değişikliği — her değişimde sepet quote'u
yeniden hesaplanır:

```java
public CartDto applyCoupon(Long userId, String couponCode) {
    Cart cart = repo.findByUserId(userId).orElseThrow(...);
    cart.setCouponCode(couponCode);
    Coupon coupon = couponRepository.findByCodeIgnoreCase(couponCode)
            .filter(c -> c.isValidAt(Instant.now()))
            .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Geçersiz kupon"));
    if (coupon.getMinCartTotal() != null
            && quote.subtotal().compareTo(coupon.getMinCartTotal()) < 0) {
        throw new ResponseStatusException(BAD_REQUEST,
                "Min sepet tutarı: " + coupon.getMinCartTotal());
    }
    // re-quote, save
}
```

Validate her seferinde — saldırgan stale coupon koduyla cart'a inject edemez.

---

## 4. Saga Participation

cart-service 3 farklı queue'da consumer:

### `order.confirmed` → Clear Cart

```java
@RabbitListener(queues = SagaTopology.Queue.CART_ORDER_CONFIRMED)
public void onOrderConfirmed(OrderConfirmedEvent event) {
    if (event.correlationId() != null) MDC.put(CorrelationId.MDC_KEY, event.correlationId());
    try {
        cartService.clearCart(event.userId());
        log.info("Cleared cart for userId={} after orderId={} confirmed", event.userId(), event.orderId());
    } finally { MDC.remove(CorrelationId.MDC_KEY); }
}
```

İdempotent: `clearCart` cart yoksa no-op. Duplicate event çift clear yapar, hata değil.

### `order.created` → Reserve Coupon

```java
@RabbitListener(queues = SagaTopology.Queue.CART_ORDER_CREATED_COUPON)
public void onOrderCreated(OrderCreatedEvent event) {
    if (event.couponCode() == null) return;   // sipariş kupon kullanmadı
    
    int updated = couponRepository.reserveOne(event.couponCode());
    if (updated == 0) {
        log.warn("Coupon {} reserve failed (already maxed or inactive) for orderId={}",
                event.couponCode(), event.orderId());
        return;   // saga compensation (order.cancelled) ile undo edilebilir
    }
    
    // Audit row — UNIQUE(coupon_id, order_id) duplicate'i engeller
    try {
        Coupon c = couponRepository.findByCodeIgnoreCase(event.couponCode()).orElseThrow();
        couponRedemptionRepository.save(CouponRedemption.builder()
                .couponId(c.getId()).orderId(event.orderId()).userId(event.userId()).build());
    } catch (DataIntegrityViolationException dup) {
        // duplicate event — release the duplicate redemption to keep counter accurate
        couponRepository.releaseOne(event.couponCode());
    }
}
```

### `order.cancelled` → Release Coupon (Compensation)

```java
@RabbitListener(queues = SagaTopology.Queue.CART_ORDER_CANCELLED_COUPON)
public void onOrderCancelled(OrderCancelledEvent event) {
    if (event.couponCode() == null) return;
    int released = couponRepository.releaseOne(event.couponCode());
    if (released == 0) {
        log.warn("Coupon release no-op for orderId={} (counter already at 0)", event.orderId());
    }
    // Audit silmiyoruz — geçmiş kayıt kalsın
}
```

`releaseOne` SQL: `WHERE redemptions > 0` — duplicate cancel event ikinci decrement'i hiçbir
şey yapmaz. Counter negatif olamaz.

---

## 5. Atomic Coupon Reservation — Race-Safe

```java
// CouponRepository
@Modifying
@CacheEvict(cacheNames = "coupons:byCode", key = "#code.toUpperCase()")
@Query("""
        UPDATE Coupon c
           SET c.redemptions = c.redemptions + 1
         WHERE c.code = :code
           AND c.active = true
           AND (c.maxRedemptions IS NULL OR c.redemptions < c.maxRedemptions)
        """)
int reserveOne(@Param("code") String code);
```

Tek atomik UPDATE. Postgres row-lock garanti eder:
- 100 paralel request aynı kuponu reserve ederse — UPDATE'ler **serialize** olur.
- `max_redemptions=10` ise, 10 başarılı UPDATE + 90 no-op UPDATE.
- Return value (rowcount): 1 success, 0 failure (max'a takıldı veya inactive).

App-side check + UPDATE pattern (`if redemptions < max then update`) **race condition'a
açık** — iki thread aynı `redemptions=9` görür, ikisi de UPDATE'e gider, ikisi de başarılı
sayılır → 11 redemption olur. Atomik UPDATE bu sınıf bug'ları kapatır.

Cache eviction key-spesifik (`#code.toUpperCase()`) — sadece o kuponun cache entry'si
silinir; başka kupon cache'leri etkilenmez.

---

## 6. Wishlist

```java
@Service
public class WishlistService {
    public ToggleResult toggle(Long userId, Long productId) {
        Optional<WishlistItem> existing = repository.findByUserIdAndProductId(userId, productId);
        if (existing.isPresent()) {
            repository.delete(existing.get());
            return new ToggleResult(false);
        }
        repository.save(WishlistItem.builder()
                .userId(userId).productId(productId).build());
        return new ToggleResult(true);
    }
    
    public List<ProductSummaryDto> list(Long userId) {
        List<Long> productIds = repository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(WishlistItem::getProductId).toList();
        // Live hydrate via product-service
        return productClient.batchGet(productIds);
    }
}
```

### Niye Live Hydrate

Wishlist'te ürün id'leri tutuluyor, **fiyat ve isim cache değil**. List sırasında product-service
HTTP call ile **canlı** veri çekilir:
- Fiyat değişti → wishlist'te güncel fiyat görünür.
- Ürün silindi → response'ta yok (silently dropped).

Trade-off: Her wishlist list'i N product-service call'ı. `batchGet` endpoint'i ile single-call
batch — N+1 problem yok.

### Guest → Login Merge

Frontend localStorage'da guest favorite ID listesi tutar (`['p1', 'p2']`). Login olunca:

```js
async function mergeGuestWishlist() {
    const guestIds = JSON.parse(localStorage.getItem('n11.guest.wishlist') || '[]');
    if (!guestIds.length) return;
    await Promise.all(guestIds.map(id => api.post('/api/wishlist', { productId: id })));
    localStorage.removeItem('n11.guest.wishlist');
}
```

Login sonrası tüm guest favorites server-side wishlist'e eklenir, localStorage temizlenir.

---

## 7. Coupon Admin CRUD

`/api/coupons` — class-level `@PreAuthorize("hasRole('ADMIN')")`:

```java
@RestController
@RequestMapping("/api/coupons")
@PreAuthorize("hasRole('ADMIN')")
public class CouponAdminController { ... }
```

### Delete-If-Redeemed Guard

```java
public void delete(Long id) {
    Coupon c = findOrThrow(id);
    if (c.getRedemptions() != null && c.getRedemptions() > 0) {
        throw new ResponseStatusException(CONFLICT,
                "Bu kupon daha önce kullanıldı, silinemez. active=false yaparak pasifleştir.");
    }
    couponRepository.delete(c);
}
```

Niye soft-delete (active=false) gerek: `coupon_redemptions` audit table FK ile bağlı. Hard
delete cascade-wipe veya FK violation yapar. Audit history korunsun → "deactivate" workflow.

### PERCENT Bounds Check

```java
private void validatePercentRange(CouponType type, BigDecimal value) {
    if (type == CouponType.PERCENT && (value.compareTo(BigDecimal.ZERO) <= 0
            || value.compareTo(new BigDecimal("100")) > 0)) {
        throw new ResponseStatusException(BAD_REQUEST,
                "Yüzde indirim 0-100 aralığında olmalı (gönderilen: " + value + ")");
    }
}
```

`@DecimalMin("0.01")` Bean Validation handles lower bound, ama upper bound + type-aware
constraint annotation'la yazılamaz (PERCENT için 100, FIXED için sınırsız). Service-side
imperative check.

---

## 8. SecurityConfig

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    @Bean public SecurityFilterChain filterChain(...) {
        http
            .csrf(csrf -> csrf.disable())
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

`@EnableMethodSecurity` zorunlu — `@PreAuthorize` no-op olmasın diye.

`anyRequest().authenticated()` — bu servis tüm endpoint'leri auth gerektirir. Public
endpoint yok (cart action'ları için login zorunlu, browse product-service'te).

Guest user için: frontend `?guestToken=...` query param'ı + special handler — JWT yok ama
guest cart endpoint'leri public-allowed olabilir; bu repoda detail var. Tipik flow: guest'ın
operations'ı `Authorization` header **olmadan** doğrudan permit edilen path'lerle yapılır
(detay implementasyon kodda).

---

## 9. Bilinçli Olarak Yapmadıklarımız

- **Stock reservation on add-to-cart**: Cart'a eklemek stok bloke etmez. Race koşulunda son
  ürünü iki kişi ekler, checkout'ta biri kaybeder. Yüksek-volume sites'da reservation gerekir.
- **Cart expiry**: Cart 30 gün boş kalsa bile silmiyoruz. Future cleanup job eklenebilir.
- **Multi-currency cart**: Tek currency (TRY).
- **Tax calculation**: Yok. Net fiyat sergilenir.
- **Shipping cost in cart**: Cart subtotal'a kargo eklenmez. Order checkout sırasında
  hesaplanır (şimdi free shipping default).
- **Cart sharing / wishlist sharing**: Kişisel.

---

## 10. Klasör Yapısı

```
backend/cart-service/
├── pom.xml
└── src/main/java/com/n11/cart/
    ├── CartApplication.java
    ├── api/
    │   ├── CartController.java
    │   ├── WishlistController.java
    │   ├── CouponAdminController.java     # admin CRUD
    │   ├── GlobalExceptionHandler.java
    │   └── dto/                           # CartDto, Quote, CouponDto, vb.
    ├── config/
    │   ├── SecurityConfig.java
    │   ├── CartProperties.java
    │   └── CacheConfig.java
    ├── domain/
    │   ├── Cart.java, CartItem.java
    │   ├── Coupon.java, CouponType.java
    │   ├── CouponRedemption.java
    │   ├── Campaign.java, CampaignType.java
    │   └── WishlistItem.java
    ├── repository/
    │   ├── CartRepository.java, CartItemRepository.java
    │   ├── CouponRepository.java          # atomik UPDATE
    │   ├── CouponRedemptionRepository.java
    │   ├── CampaignRepository.java
    │   └── WishlistItemRepository.java
    ├── service/
    │   ├── CartService.java
    │   ├── WishlistService.java
    │   ├── CouponAdminService.java
    │   └── DiscountEngine.java
    ├── discount/                          # Strategy implementations
    │   ├── DiscountStrategy.java
    │   ├── BuyXPayYStrategy.java
    │   ├── PercentOffCartStrategy.java
    │   ├── FixedAmountCouponStrategy.java
    │   └── PercentCouponStrategy.java
    ├── messaging/
    │   ├── RabbitConfig.java              # primary queues + DLQ + listener factory
    │   ├── OrderConfirmedListener.java    # cart clear
    │   └── CouponSagaListener.java        # reserve + release
    └── client/
        └── ProductClient.java             # cross-service hydrate
```

---

## İlgili Dokümanlar

- [`docs/messaging.md`](../messaging.md) — RabbitMQ topology, DLX
- [`docs/services/order-service.md`](order-service.md) — Saga publisher counterpart
- [`docs/saga.md`](../saga.md) — End-to-end saga akışı
- [`docs/caching.md`](../caching.md) — `coupons:byCode` namespace
