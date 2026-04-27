# Choreography Saga

## Exchange ve Routing Keys

| | |
|---|---|
| Exchange | `saga.exchange` (topic, durable) |
| Routing keys | `order.created`, `payment.succeeded`, `payment.failed`, `order.confirmed`, `order.cancelled` |

## Queue Bindings

| Queue | Binds | Consumer |
|---|---|---|
| `payment.order-created.q` | `order.created` | payment-service (charge) |
| `order.payment-succeeded.q` | `payment.succeeded` | order-service (CONFIRM) |
| `order.payment-failed.q` | `payment.failed` | order-service (CANCEL) |
| `cart.order-confirmed.q` | `order.confirmed` | cart-service (clear cart) |
| `cart.order-created.coupon.q` | `order.created` | cart-service (reserve coupon) |
| `cart.order-cancelled.coupon.q` | `order.cancelled` | cart-service (release coupon — compensation) |

> Aynı `order.created` routing key'i hem payment-service'i (ödeme tetikler) hem de
> cart-service'i (kupon rezervasyonu) bağımsız queue'larla besliyor. Topic exchange
> birden fazla consumer'ı paralel çalıştırır; kupon rezervasyonu ödeme akışını
> bloke etmez.

## Akışlar

### CheckoutSaga (mutluyolcu)

```
POST /api/orders/checkout
 │
 ▼
order-service
 ├─► REST: cart-service.fetchCurrent()       (cart.couponCode dahil)
 ├─► orders.insert (PENDING → AWAITING_PAYMENT, coupon_code snapshot)
 └─► afterCommit
      └─► publish "order.created" {orderId, userId, totalAmount, items[], couponCode, correlationId}
            │
            ├──► payment-service.onOrderCreated()
            │     ├─► payments.insert (PENDING)
            │     ├─► gateway.charge(...)         ← Iyzico (or MockPaymentGateway)
            │     ├─► payments.update (SUCCEEDED, providerRef)
            │     └─► publish "payment.succeeded" {paymentId, orderId, providerRef, ...}
            │           │
            │           ▼
            │      order-service.onPaymentSucceeded()
            │       ├─► orders.update (CONFIRMED)
            │       └─► publish "order.confirmed" {orderId, userId, userEmail}
            │             │
            │             └──► cart-service.onOrderConfirmed()
            │                   └─► cart.items.clear()
            │
            └──► cart-service.CouponSagaListener.onOrderCreated()  (couponCode ≠ null ise)
                  ├─► UPDATE coupons SET redemptions = redemptions + 1
                  │     WHERE redemptions < max_redemptions       ← race-safe
                  └─► INSERT coupon_redemptions (coupon_id, order_id, user_id)
                        UNIQUE (coupon_id, order_id)              ← duplicate guard
```

### CheckoutSaga (compensation: ödeme başarısız)

```
POST /api/orders/checkout
 │
 ▼
order-service                 (orders → AWAITING_PAYMENT, "order.created")
 │
 ▼
payment-service
 ├─► gateway.charge(...) → DECLINED ya da gateway hatası
 ├─► payments.update (FAILED, failure_reason)
 └─► publish "payment.failed" {orderId, paymentId, reason}
       │
       ▼
  order-service.onPaymentFailed()
   ├─► orders.update (CANCELLED, failure_reason)
   └─► publish "order.cancelled" {orderId, reason, couponCode}
         │
         └──► cart-service.CouponSagaListener.onOrderCancelled()
               (compensation — sadece couponCode ≠ null ise)
                ├─► coupon_redemptions.find(orderId) — yoksa no-op (idempotent)
                ├─► UPDATE coupons SET redemptions = redemptions − 1 WHERE redemptions > 0
                └─► coupon_redemptions.delete(orderId)
```

> Compensating action sepeti **boşaltmaz** — kullanıcı tekrar deneyebilir. Eğer ürün
> rezervasyonu olsaydı (örn. inventory-service), `order.cancelled` üzerinden stok serbest
> bırakma adımı eklenirdi.
>
> `order.confirmed` ve `order.cancelled` event'leri yayınlanmaya devam eder; ileride
> bağlanacak consumer'lar (e-posta, in-app notification, analytics) için topic-exchange
> üzerinden hazır beklerler. Slack'e *runtime* bildirim atan bir consumer **yoktur** —
> Slack sadece CI/CD deploy bildirimleri için kullanılır (`deploy.yml`).

## Idempotency ve at-least-once

- Saga consumer'ları event'in yan etkisini uygulamadan önce mevcut order durumunu kontrol
  eder. `OrderStatus.CONFIRMED` zaten ise `payment.succeeded` duplicate'ı sessizce yok
  sayılır (`PaymentResultListener.onPaymentSucceeded` içinde).
- Order entity'deki `transitionTo()` state machine, geçersiz transition'ları
  `IllegalStateException` ile reddeder — RabbitMQ retry'lerinde state corruption olmaz.

### Kupon rezervasyonu — yarış senaryoları

| Senaryo | Kapatan mekanizma |
|---|---|
| Aynı `order.created` iki kez teslim edildi | `coupon_redemptions` tablosunda `(coupon_id, order_id)` UNIQUE — ikinci insert `DataIntegrityViolationException` fırlatır, listener counter'ı geri çevirir, log'lar |
| İki paralel checkout son slot'a yarışıyor | `UPDATE … WHERE redemptions < max_redemptions` — biri 1 row affected, diğeri 0; 0 alan order'ı tam fiyatla devam ettirir (sipariş roll-back maliyeti rezervasyon kaybından büyük) |
| Aynı `order.cancelled` iki kez | `findByOrderId` ikincisinde boş döner, no-op |
| Kupon admin tarafından silindi/inactive yapıldı, ardından `order.created` geldi | `findByCodeIgnoreCase` null → log + skip; sipariş etkilenmez |
| Compensation duplicate counter'ı negatif yapar mı? | `WHERE redemptions > 0` predicate'i floor sağlıyor, PostgreSQL CHECK ekstra savunma |

## Correlation propagation

- HTTP isteği `X-Correlation-Id` header üretir → `MDC[correlationId]` olarak set edilir.
- `OrderCreatedEvent` payload'ında `correlationId` taşınır.
- Tüketici servis MDC'ye geri koyar → log satırlarında aynı ID görünür.
- Bir `POST /api/orders/checkout` çağrısı 4 servisin log'larında tek bir `[cid]` ile takip
  edilebilir.
