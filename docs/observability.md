# Observability — Logs + Tracing + Metrics

**Bu doküman:** Sistemde olan biteni nasıl görüyoruz. Üç pilar: structured log + distributed
trace + Micrometer metrics. Niye bu seçimler, nasıl konfigüre edildi.

---

## 1. Üç Pilar

| Pilar | Araç | Görünür yer | "Ne sorduğumda?" |
|---|---|---|---|
| **Logs** | SLF4J + Logback (default) + correlation ID | stdout (Docker logs) | "Bu request'in akışı ne?" |
| **Traces** | Micrometer Tracing + OTel + Jaeger | Jaeger UI (basic-auth korumalı `$JAEGER_DOMAIN` veya `:16686`) | "Bu request her servise kaç ms'te geçti?" |
| **Metrics** | Micrometer (built-in) → Actuator | `/actuator/metrics/...` | "Cache hit ratio ne?" "Latency p99?" |
| **Errors** | **Sentry** (frontend + backend) | sentry.io / `n11-frontend`, `n11-backend` projeleri | "Hata stack trace'i ne, hangi user gördü, kaç kere oldu?" |

Dördü ayrı ayrı sorulara cevap verir. Birinin yerini diğeri tutmaz. Sentry tracing
özelliği bilinçli olarak **kapalı** (`traces-sample-rate=0`) — onu Jaeger yapıyor,
ikisini birden açmak hem maliyetli hem context split eder.

---

## 2. Correlation ID

### Niye Var

Bir kullanıcı checkout yapar. Akış:
```
frontend → gateway → auth-service (token verify)
                  → cart-service (read cart)
                  → order-service (create order)
                                     └─ publishes order.created
payment-service ←─ subscribes ─┘
   └─ Iyzico HTTP call
   └─ publishes payment.succeeded
order-service ←─ subscribes ─┘
   └─ transitions order to CONFIRMED
   └─ publishes order.confirmed
cart-service ←─ subscribes ─┘
notification-service ←─ subscribes ─┘
   └─ sends email
```

Bu zincirin tek bir kullanıcı işlemine ait olduğunu nasıl bilirim log'larda?

**Cevap**: her log line'a aynı `correlationId` etiketle. Bir kullanıcı 500 alırsa,
correlation ID'siyle tüm zinciri tek `grep` ile çekersin.

### Implementation

```java
// backend/common/src/main/java/com/n11/common/correlation/CorrelationIdFilter.java
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, ...) {
        String existing = request.getHeader(HEADER);
        String correlationId = (existing != null && !existing.isBlank())
                ? existing
                : UUID.randomUUID().toString();
        
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
```

**MDC** (Mapped Diagnostic Context) = SLF4J'nin thread-local key-value store'u. Logback
pattern'inden okunur.

```yaml
logging:
  pattern:
    level: "%5p [%X{correlationId:-}]"
```

`%X{correlationId:-}` — MDC'den `correlationId` key'ini al, yoksa boş bas. Sonuç:
```
INFO [a8f2-...]  Started checkout for userId=42
```

### Servis-Servis Propagation

#### HTTP

axios interceptor (frontend) yeni correlation generate eder ve `X-Correlation-Id` header'ı
ile gönderir. Gateway proxy'ler header'ı pass eder. Her servis filter'i header'dan okur.
Yoksa kendi UUID'sini üretir (her gateway boost'u her request için yeni ID değil — header
varsa korunur).

#### RabbitMQ

Event'in **payload**'ında `correlationId` field'ı:

```java
public record OrderShippedEvent(
        UUID eventId,
        Instant occurredAt,
        Long orderId,
        ...
        String correlationId
) { ... }
```

Publisher tarafı:
```java
String correlationId = MDC.get(CorrelationId.MDC_KEY);
OrderShippedEvent event = OrderShippedEvent.of(..., correlationId);
publisher.publishOrderShipped(event);
```

Consumer tarafı:
```java
@RabbitListener(queues = ...)
public void onOrderShipped(OrderShippedEvent event) {
    if (event.correlationId() != null) MDC.put(CorrelationId.MDC_KEY, event.correlationId());
    try {
        // ... process
    } finally {
        MDC.remove(CorrelationId.MDC_KEY);
    }
}
```

`finally` zorunlu: aynı thread sonraki bir mesajı eski correlationId ile loglamasın.

### Niye RabbitMQ headers değil, payload field?

Alternatif: `MessageProperties.setCorrelationId(...)` (RabbitMQ message header). Yapılabilirdi.
Niye payload:

- **Kalıcılık**: Mesaj DLX'e düşüp `.dlq`'da bekliyor → header'lar değişiyor olabilir, payload
  içindeki ID immutable.
- **Cross-channel**: Future'ta event Kafka'ya re-publish edilirse, payload field her yerde
  taşınır. Header convention'ı broker-specific.
- **Debug**: Management UI'da mesaj peek ediyorum, payload'ı görüyorum, correlationId hemen
  belli.

---

## 3. Distributed Tracing — Jaeger

### Stack

```
Service spans → Micrometer Tracing → OTLP exporter → Jaeger collector → Jaeger UI
```

`pom.xml`:
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

Konfig:
```yaml
management:
  tracing:
    sampling:
      probability: ${TRACE_SAMPLING:1.0}
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318}/v1/traces
```

### Jaeger Container

```yaml
jaeger:
  image: jaegertracing/all-in-one:1.62
  environment:
    COLLECTOR_OTLP_ENABLED: "true"
  ports:
    - "16686:16686"  # UI
    - "4318:4318"    # OTLP HTTP
```

`all-in-one` = collector + storage + query/UI tek container. Production'da split önerilir
(scale tarafları), bizim ölçekte gereksiz.

### Niye OTLP, niye Zipkin format değil?

Micrometer iki exporter destekler: Zipkin format (Zipkin'e gönder) ve OTLP (Jaeger / Tempo
/ Honeycomb / vendor-neutral). Kazanan: OTLP.

- **Vendor-neutral**: Aynı exporter herhangi bir OTel-compatible backend'e gider. Future
  Tempo/Honeycomb migration kolay.
- **Modern standard**: OTel her büyük observability vendor'da default.
- **Attribute schema**: HTTP/RPC/DB için standart attribute name'leri (`http.method`,
  `db.system`, vb.) — query yazarken kolaylık.

### Sampling

`probability: 1.0` = **her request trace'lenir**. Bizim trafik volümümüzde Jaeger'i şişirmez.

Production: `probability: 0.1` = %10 sample. Tüm trace'ler değil, ama hatalar (`exception=true`
attribute olan span'lar) genelde 100% sample edilir vendor-side. Bu setup'ta head-based
sampling — basit, deterministic.

### Trace ID'lerini Log'larda Görmek

Spring Boot 3 + Micrometer Tracing otomatik `traceId` + `spanId`'yi MDC'ye ekler. Logback
pattern'i correlationId yanında onları da basabilir:

```yaml
logging:
  pattern:
    level: "%5p [%X{correlationId:-}] [%X{traceId:-},%X{spanId:-}]"
```

(Şu an config'imizde sadece correlationId var; traceId eklenebilir.)

### Niye Sadece Correlation ID Yeterli Değil

Trace ID ve correlation ID görünüşte benzer ama farklı:
- **Correlation ID**: Tüm zincir boyunca **aynı** kalır (publish-subscribe dahil). User-action
  scope.
- **Trace ID**: HTTP request başına. Async event publish edildiğinde **yeni trace** başlar
  (RabbitMQ propagation kompleks).

Pragmatik: correlation ID **business view** (bir kullanıcı işlemi), trace ID **technical view**
(bir HTTP request). İkisini birden tutmak idealdir.

---

## 4. Error Tracking — Sentry

Jaeger latency'yi gösterir, log'lar mesajları yazar — ama "**hata olduğu anda neredeydi
kullanıcı, hangi React state'iyle, stack trace nerede?**" sorusu için stack-aware
bir error tracker lazım. Sentry bunu yapar; SaaS free tier portfolio scope için yeter
(5k event + 50 replay / ay).

### 4.1 İki Proje, İki Platform

| Proje | Platform | Yakaladığı |
|---|---|---|
| `n11-frontend` | Browser/React | UI render hataları, axios reject, unhandled promise, console errors |
| `n11-backend` | Java/Spring Boot | Tüm 8 microservice'in exception'ları + ERROR-level log event'leri |

Backend tek proje + `service:auth-service` gibi tag'larla servis ayrımı yapılır;
frontend için ayrı proje **platform tag'ı doğal şekilde JS hatalarını React event
modeline ayırdığı** için.

### 4.2 Backend Wiring

`backend/common/pom.xml` shared dep:

```xml
<dependency>
    <groupId>io.sentry</groupId>
    <artifactId>sentry-spring-boot-starter-jakarta</artifactId>
</dependency>
<dependency>
    <groupId>io.sentry</groupId>
    <artifactId>sentry-logback</artifactId>
</dependency>
```

8 servis bu library'yi inherit eder, her birinin `application.yml`'ında küçük bir
`sentry:` block:

```yaml
sentry:
  dsn: ${SENTRY_DSN:}            # boş -> SDK no-op (local dev)
  environment: ${SENTRY_ENVIRONMENT:local}
  release: ${SENTRY_RELEASE:dev}  # docker compose IMAGE_TAG'ından geliyor
  send-default-pii: false
  traces-sample-rate: 0.0         # tracing Jaeger'da kalır
  logging:
    minimum-event-level: error    # WARN log'ları Sentry'ye düşmez
    minimum-breadcrumb-level: info
  tags:
    service: auth-service          # her serviste hardcoded
```

`docker-compose.prod.yml`'ndaki `*sentry-env` YAML anchor merge'i ile DSN ortak,
release her deploy'da `IMAGE_TAG`'ından otomatik:

```yaml
x-sentry-env: &sentry-env
  SENTRY_DSN: ${SENTRY_DSN:-}
  SENTRY_ENVIRONMENT: ${SENTRY_ENVIRONMENT:-production}
  SENTRY_RELEASE: ${IMAGE_TAG:-latest}
```

### 4.3 Niye OpenTelemetry Agent Yok

Sentry'nin önerdiği kurulum `sentry-opentelemetry-agent.jar` ile JVM'i sarıyor —
tracing hem Jaeger'a hem Sentry'ye gidiyor. Bizde **sadece error tracking**
istiyoruz, tracing zaten Jaeger'da. Agent'ı kullanmamak:
- Daha az bellek/CPU yükü (agent her span'i interceptlemiyor)
- Sentry "Tracing" sekmesi boş kalır (bilinçli)
- `SENTRY_AUTO_INIT` flag'lerine + extra javaagent çağrısına gerek yok

Trade-off: Sentry Issues'da "Performance" tab'ı yok. Yavaşlık için Jaeger'a bakacaksın.

### 4.4 Frontend Wiring

`frontend/src/lib/sentry.js`:

```js
export function initSentry() {
  const dsn = import.meta.env.VITE_SENTRY_DSN;
  if (!dsn) return;
  Sentry.init({
    dsn,
    environment: import.meta.env.VITE_SENTRY_ENVIRONMENT || 'production',
    release: import.meta.env.VITE_SENTRY_RELEASE || 'dev',
    integrations: [Sentry.replayIntegration({ maskAllText: true, blockAllMedia: true })],
    tracesSampleRate: 0.0,
    replaysSessionSampleRate: 0.1,
    replaysOnErrorSampleRate: 1.0,
    initialScope: { tags: { service: 'frontend' } },
  });
}
```

`main.jsx` → `initSentry()` + `Sentry.ErrorBoundary` tüm React tree'yi sarar.
Render-time React hataları yakalar, blank-screen yerine fallback gösterir.

### 4.5 User Scope — AuthContext Bridge

`AuthContext` `useEffect`'inden Sentry'ye user identity push ediyor:

```jsx
useEffect(() => {
  setSentryUser(user);
}, [user]);
```

Login → `Sentry.setUser({ id, email, username })` → sonraki tüm event'ler bu
kullanıcıya bağlanır. Logout → `Sentry.setUser(null)` → scope temizlenir,
sonraki anonymous session önceki user'ı miras almaz.

### 4.6 Source Map Upload

Production bundle minified — `e.t.handleClick` gibi okunmaz stack trace'ler
gelir. `@sentry/vite-plugin` build-time'da source map'leri Sentry'ye yükler,
sonra `dist/`'ten siler:

```js
// vite.config.js
sentryVitePlugin({
  authToken: process.env.SENTRY_AUTH_TOKEN,    // GitHub Secrets'tan
  org: process.env.SENTRY_ORG,
  project: process.env.SENTRY_PROJECT,
  release: { name: process.env.VITE_SENTRY_RELEASE },
  sourcemaps: { filesToDeleteAfterUpload: ['./dist/**/*.map'] },
})
```

Sonuç: Sentry Issues'da stack trace **`LoginPage.jsx:147`** olarak okunur, end
user `*.map` indirmez (sadece Sentry'nin var). Plugin sadece `SENTRY_AUTH_TOKEN`
varsa çalışır — local `npm run build` token'sız sourcemap upload yapmaz.

### 4.7 Bilinçli Saf Tutulan Şeyler

| Özellik | Açıklama | Niye |
|---|---|---|
| **Tracing** | `traces-sample-rate: 0.0` her iki tarafta da | Jaeger zaten yapıyor, ikisini birden açmak hem maliyetli hem context split |
| **Performance Monitoring** | Aktif değil | Aynı sebep — Jaeger'da var |
| **Profiling** | Kapalı | Portfolio scope'unda overkill |
| **Cron monitoring** | Kapalı | Actuator scheduled task metrikleri yetiyor |
| **Validation hatalarını capture** | Hayır | "+90 ile başlat" gibi UI validation toast'ları Sentry'ye düşmüyor — beklenen kullanıcı hatası, free tier'ı doldurma |

### 4.8 Sentry Setup → Infisical / GitHub Secrets

Hangi secret nereye gider:

| Secret | Yer | Niye |
|---|---|---|
| `SENTRY_DSN` (backend) | **Infisical** | Runtime'da inilir, sync-env.sh ile compose'a girer |
| `SENTRY_ENVIRONMENT` | **Infisical** | Runtime config |
| `VITE_SENTRY_DSN` (frontend) | **GitHub Secrets** | Build-time'da bundle'a girer, droplet'a inmeden önce CI'da gerekli |
| `SENTRY_AUTH_TOKEN` | **GitHub Secrets** | CI build sırasında source map upload için |
| `SENTRY_ORG`, `SENTRY_PROJECT` | **GitHub Secrets** | Aynı sebep |

Bootstrap ayrımı: backend DSN runtime, frontend DSN build-time.

---

## 5. Metrics — Actuator + Micrometer

### Endpoint

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

Açık endpoint'ler:
- `/actuator/health` — basic up/down + per-component (DB, Redis, RabbitMQ).
- `/actuator/info` — build info.
- `/actuator/metrics` — meter listesi.
- `/actuator/metrics/<name>` — meter detail.

### Useful Meter'lar

```
GET /actuator/metrics/http.server.requests
→ Per-endpoint latency histogram + count + status code breakdown.

GET /actuator/metrics/cache.gets?tag=cache:products:byId
→ Cache hit/miss for the byId namespace.

GET /actuator/metrics/jvm.memory.used
→ JVM heap usage.

GET /actuator/metrics/process.cpu.usage
→ Process CPU.

GET /actuator/metrics/spring.rabbitmq.amqp.consumed
→ RabbitMQ messages consumed (Spring AMQP auto-instrumentation).

GET /actuator/metrics/spring.cloud.gateway.requests
→ Per-route gateway latency.
```

### Niye Prometheus Endpoint Eklenmedi

`micrometer-registry-prometheus` dep'i eklenirse `/actuator/prometheus` açılır → Prometheus
scrape. Şu an yok çünkü:
- Lokal dev için Actuator JSON yeterli.
- Prometheus + Grafana ek container — RAM (8GB cap'imiz var).
- Production'da Caddy + Grafana Cloud setup yapılabilir, demo için scope dışı.

Eklenirse: `pom.xml`'a `micrometer-registry-prometheus` + `management.endpoints.web.exposure.include`
listesine `prometheus` eklenir, hazır.

### Custom Metric Eklemek

Spring Boot 3'te `MeterRegistry` inject edip:

```java
@Service
public class CheckoutService {
    private final Counter checkoutCounter;
    
    public CheckoutService(MeterRegistry registry) {
        this.checkoutCounter = Counter.builder("n11.checkout.attempts")
                .tag("source", "web")
                .register(registry);
    }
    
    public OrderDto checkout(...) {
        checkoutCounter.increment();
        // ...
    }
}
```

`/actuator/metrics/n11.checkout.attempts` ile sorgulanır.

Şu an custom metric yok — Spring Boot'un default'ları yetiyor.

---

## 6. Health Checks

```yaml
management:
  endpoint:
    health:
      show-details: when-authorized
```

`/actuator/health`:
```json
{"status":"UP"}
```

`when-authorized` = JWT'li request detayları görür:
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "redis": {"status": "UP"},
    "rabbit": {"status": "UP"},
    "diskSpace": {"status": "UP", "details": {...}}
  }
}
```

Auto-detected: Spring Boot her config'lenmiş resource (DataSource, RedisConnectionFactory,
ConnectionFactory amqp) için indicator ekler.

### Container Health Check

`docker-compose.yml`:
```yaml
healthcheck:
  test: ["CMD", "wget", "-q", "--spider", "http://localhost:8081/actuator/health"]
  interval: 5s
  timeout: 3s
  retries: 6
```

Compose `depends_on: { condition: service_healthy }` ile bağlı servisler bu health check
geçene kadar başlamaz. Order: postgres ✓ → auth-service ✓ → order-service başlar.

---

## 7. Log Format

Default Spring Boot Logback pattern (override yok):
```
%d{yyyy-MM-dd HH:mm:ss.SSS} %5p [%X{correlationId:-}] [thread] logger - msg
```

Örnek:
```
2026-04-28 14:23:45.678  INFO [a8f2-...]  [http-nio-8084-exec-1] c.n.o.a.OrderController : Started checkout for userId=42
2026-04-28 14:23:45.701  INFO [a8f2-...]  [http-nio-8084-exec-1] c.n.o.s.CheckoutService : Created order id=123 amount=499.00
2026-04-28 14:23:45.745  INFO [a8f2-...]  [http-nio-8084-exec-1] c.n.o.m.OrderEventPublisher : Published OrderCreated orderId=123
```

### Niye JSON Log Yok

Production'da JSON log (`logstash-logback-encoder`) Loki/Elasticsearch ile kolay parse edilir.
Bizde:
- Dev/demo'da insanın okuyacağı format öncelik.
- Loki/ES yok zaten — Docker `docker logs` veya Caddy stdout.
- JSON eklemek isterse `pom.xml`'a `logstash-logback-encoder` + `logback-spring.xml`
  override yeterli.

### Log Level

```yaml
logging:
  level:
    root: INFO
    com.n11: DEBUG
```

- Spring + 3rd-party: INFO (gürültüsüz).
- Bizim kod: DEBUG (development debug log'ları görünür).

Production'da `com.n11: INFO` yapılır — DEBUG produktif değil.

---

## 8. Tipik Debug Senaryoları

### "Sipariş oluştu ama mail gelmedi"

1. **Correlation ID al**: User'ın çağrısı response header'ında `X-Correlation-Id` döner; UI
   tarafında geliştirici tools'ta gör.
2. **Tüm servislerin log'unu grep**:
   ```
   docker compose logs | grep "correlation-id-uuid"
   ```
3. Beklenen sequence:
   - order-service: `Created order id=X`
   - order-service: `Published OrderCreated orderId=X`
   - payment-service: `OrderCreated received...`
   - payment-service: `Published PaymentSucceeded...`
   - order-service: `Order CONFIRMED...`
   - notification-service: `OrderConfirmed received orderId=X`
   - notification-service: `Sent ORDER_CONFIRMED mail...`
4. Eksik adım hangisi → o servis nereye takıldı.

### "Bu request niye 5 saniye sürdü?"

1. Jaeger UI'da Service: `order-service`, Operation: `POST /api/orders/checkout`.
2. En yavaş trace'i seç.
3. Span breakdown'ında bottleneck görünür: HTTP call to auth-service mi, DB query mi, vs.

### "Cache çalışıyor mu?"

```bash
curl http://localhost:8082/actuator/metrics/cache.gets | jq
```

Hit/miss ratio'su. Hit %< 90 ise cache invalidation çok agresif veya TTL çok kısa.

---

## 9. Bilinçli Olarak Yapmadıklarımız

- **Centralized log aggregation (Loki, ELK)**: Compose'da yok; `docker compose logs` ile
  yetiniyoruz. Production deployment'a Loki + Promtail sidecar eklemek bir akşamlık iş.
- **APM agent (Elastic APM, Datadog)**: Vendor-neutral OTel exporter ile gelecek auto-instrumentation
  yetiyor. Vendor lock-in ekstra maliyet.
- **Per-tenant metric labels**: Single-tenant olduğu için yok.
- **Cardinality bombs koruma**: Custom metric'lerde `tag("userId", X)` koymak metric explosion
  yaratır (her userId ayrı time series). Şu an custom metric yok, sorun değil; eklenirse
  cardinality limit'e dikkat.
- **Log scrubbing (PII)**: Mail adresleri INFO log'larında geçiyor. Production-grade GDPR-compliance
  için sensitive field'ları masklemek gerekir; scope dışı.

---

## İlgili Dokümanlar

- [`docs/services/common.md`](services/common.md) — `CorrelationIdFilter` implementasyonu
- [`docs/messaging.md`](messaging.md) — RabbitMQ event'lerinde correlation ID propagation
- [`docs/services/api-gateway.md`](services/api-gateway.md) — Gateway tracing config
