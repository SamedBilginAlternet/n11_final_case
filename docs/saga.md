# Choreography Saga

## Exchange ve Routing Keys

| | |
|---|---|
| Exchange | `saga.exchange` (topic, durable) |
| Routing keys | `order.created`, `payment.succeeded`, `payment.failed`, `order.confirmed`, `order.cancelled` |

## Queue Bindings

| Queue | Binds | Consumer |
|---|---|---|
| `payment.order-created.q` | `order.created` | payment-service |
| `order.payment-succeeded.q` | `payment.succeeded` | order-service |
| `order.payment-failed.q` | `payment.failed` | order-service |
| `cart.order-confirmed.q` | `order.confirmed` | cart-service |

## Akışlar

### CheckoutSaga (mutluyolcu)

```
POST /api/orders/checkout
 │
 ▼
order-service
 ├─► REST: cart-service.fetchCurrent()
 ├─► orders.insert (PENDING → AWAITING_PAYMENT)
 └─► afterCommit
      └─► publish "order.created" {orderId, userId, totalAmount, items[], correlationId}
            │
            ▼
       payment-service.onOrderCreated()
        ├─► payments.insert (PENDING)
        ├─► gateway.charge(...)         ← Iyzico (or MockPaymentGateway)
        ├─► payments.update (SUCCEEDED, providerRef)
        └─► publish "payment.succeeded" {paymentId, orderId, providerRef, ...}
              │
              ▼
         order-service.onPaymentSucceeded()
          ├─► orders.update (CONFIRMED)
          └─► publish "order.confirmed" {orderId, userId, userEmail}
                │
                └──► cart-service.onOrderConfirmed()
                      └─► cart.items.clear()
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
   └─► publish "order.cancelled" {orderId, reason}
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

## Correlation propagation

- HTTP isteği `X-Correlation-Id` header üretir → `MDC[correlationId]` olarak set edilir.
- `OrderCreatedEvent` payload'ında `correlationId` taşınır.
- Tüketici servis MDC'ye geri koyar → log satırlarında aynı ID görünür.
- Bir `POST /api/orders/checkout` çağrısı 4 servisin log'larında tek bir `[cid]` ile takip
  edilebilir.
