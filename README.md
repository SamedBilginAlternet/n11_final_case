# n11 Final Case — E-Ticaret

Spring Boot 3.3 / Java 21 **7 mikroservis**, RabbitMQ üzerinde **choreography saga**, JWT
auth, Iyzico ödeme entegrasyonu, React + Vite + Tailwind frontend ve **GitHub Actions →
AWS Elastic Beanstalk + ECR + RDS** deploy boru hattı.

| | |
|---|---|
| **Backend** | Spring Boot 3.3.4, Java 21, Spring Cloud Gateway 2023.0.3, JPA + Flyway + PostgreSQL 16 |
| **Mesajlaşma** | RabbitMQ 3.13 — topic exchange, durable queues, JSON message converter |
| **Auth** | JJWT 0.12.6 HS256, BCrypt, gateway-relayed Bearer header |
| **Ödeme** | iyzipay-java 2.0.65 (sandbox/prod toggle); offline `MockPaymentGateway` fallback |
| **Frontend** | React 18, Vite 5, Tailwind 3, react-router 6, axios, react-hot-toast |
| **DevOps** | Docker Compose, Jib, GitHub Actions, AWS Elastic Beanstalk Multi-Container, AWS ECR, Slack incoming webhook |
| **Test** | JUnit 5 + Mockito + Testcontainers (PostgreSQL & RabbitMQ) |

## İçindekiler

- [Mimari](#mimari)
- [Servisler](#servisler)
- [Saga Akışı](#saga-akışı)
- [Çalıştırma](#çalıştırma)
- [Test](#test)
- [API Dokümantasyonu](#api-dokümantasyonu)
- [CI/CD](#cicd)
- [AWS Deployment](#aws-deployment)
- [Klasör Yapısı](#klasör-yapısı)

> Detaylı dokümantasyon `docs/` altındadır:
> - [`docs/architecture.md`](docs/architecture.md) — mimari diyagram + tasarım kararları
> - [`docs/saga.md`](docs/saga.md) — saga waterfall, compensation, idempotency
> - [`docs/cicd.md`](docs/cicd.md) — GitHub Actions ↔ Jenkins karşılaştırması
> - [`docs/aws.md`](docs/aws.md) — AWS playbook (EB + RDS + ECR)

## Mimari

```
browser → frontend (nginx) → api-gateway → { auth, product, cart, order, payment }
                                   │
                                   ├─► PostgreSQL  (per-service DB)
                                   ├─► RabbitMQ    (saga.exchange / topic)
                                   └─► Slack       (notification-service webhook)
```

Detaylı diyagram: [`docs/architecture.md`](docs/architecture.md).

## Servisler

| Servis | Port | DB | Görev |
|---|---|---|---|
| **api-gateway** | 8080 | — | Public giriş, JWT relay, aggregated Swagger UI |
| **auth-service** | 8081 | `authdb` | `register`, `login`, `users/me`, JWT issuance, `UserRegistered` publisher |
| **product-service** | 8082 | `productdb` | Pagination + search + categories |
| **cart-service** | 8083 | `cartdb` | Sepet CRUD, `OrderConfirmed` consumer (sepeti temizler) |
| **order-service** | 8084 | `orderdb` | Checkout, state machine, saga publisher + payment-event consumer |
| **payment-service** | 8085 | `paymentdb` | `OrderCreated` consumer, Iyzico, `Payment*` publisher |
| **notification-service** | 8086 | — | Saga olaylarını fan-out tüketir, Slack webhook'a yazar |

Her servis kendi `pom.xml`'i, kendi Flyway migration set'i ve kendi DB'si ile bağımsız
olarak deploy edilebilir.

## Saga Akışı

3 saga var:

1. **UserRegistrationSaga** — kayıt sonrası Slack bildirimi (publisher: auth, consumer: notification).
2. **CheckoutSaga (mutluyolcu)** — `order.created` → ödeme → `payment.succeeded` → `order.confirmed` →
   sepeti temizleme + Slack bildirimi.
3. **CheckoutSaga (compensation)** — ödeme reddedildiğinde `payment.failed` → `order.cancelled` →
   Slack uyarısı; sipariş `CANCELLED` durumuna düşer ve müşteri tekrar deneyebilir.

ASCII waterfall + idempotency notları: [`docs/saga.md`](docs/saga.md).

## Çalıştırma

```bash
cp .env.example .env
docker compose up --build
```

| Servis | URL |
|---|---|
| Frontend | http://localhost:3000 |
| API Gateway | http://localhost:8080 |
| Aggregated Swagger UI | http://localhost:8080/swagger-ui.html |
| RabbitMQ Management | http://localhost:15672 (`guest` / `guest`) |
| PostgreSQL | localhost:5432 (`n11` / `n11pw`) |

`/api/categories` ile sample veriyi doğrulayabilirsiniz; `http/` dizininde IntelliJ /
VS Code REST Client için hazır request collection mevcut (`auth.http`,
`products.http`, `cart-and-checkout.http`).

### Iyzico

Varsayılan olarak `IYZICO_ENABLED=false` ile **MockPaymentGateway** çalışır — hiçbir dış
servise istek gitmez, başarılı bir `MOCK-XXXXX` referansı üretir.

Sandbox testi için:

```bash
IYZICO_ENABLED=true \
IYZICO_API_KEY=sandbox-... \
IYZICO_SECRET_KEY=sandbox-... \
IYZICO_BASE_URL=https://sandbox-api.iyzipay.com \
docker compose up --build
```

## Test

```bash
# Tüm modüller (parent POM)
mvn -f backend/pom.xml verify

# Tek servis
mvn -f backend/pom.xml -pl auth-service -am test

# Frontend
cd frontend && npm test
```

Backend test stratejisi:
- **Unit**: domain (state machine), service layer (mock'lu).
- **Integration**: `@SpringBootTest` + Testcontainers PostgreSQL (auth-service, product-service).

## API Dokümantasyonu

Her servis kendi `/v3/api-docs` ve `/swagger-ui.html` endpoint'ini açar. Gateway
**aggregated Swagger UI** sunar:

- http://localhost:8080/swagger-ui.html → drop-down'dan servis seç (`auth`, `products`, `cart`,
  `orders`, `payments`).

JWT'yi kullanmak için Swagger'da "Authorize" → `Bearer <token>` olarak yapıştır.

## CI/CD

| Workflow | Tetikleyici | Görev |
|---|---|---|
| [`backend.yml`](.github/workflows/backend.yml) | `push`/`pr` (backend changes) | 8 modül için matrix `mvn verify`. main'de Jib ile GHCR push |
| [`frontend.yml`](.github/workflows/frontend.yml) | `push`/`pr` (frontend changes) | npm ci → lint → vitest → vite build → dist artifact |
| [`deploy.yml`](.github/workflows/deploy.yml) | tag `v*` | Jib → ECR, frontend Docker build → ECR, EB deploy, Slack bildirim |

Jenkins ile karşılaştırma: [`docs/cicd.md`](docs/cicd.md) — aynı pipeline'ın `Jenkinsfile`
karşılığı + adım adım kavram eşleştirmesi.

## AWS Deployment

[`docs/aws.md`](docs/aws.md) içinde:
- Elastic Beanstalk multi-container Docker konfigürasyonu (`infra/aws/Dockerrun.aws.template.json`)
- RDS PostgreSQL (multi-AZ) provisioning notları
- IAM OIDC role for GitHub Actions
- EB environment variables (`RDS_*`, `JWT_SECRET`, `IYZICO_*`, `SLACK_WEBHOOK_URL`)
- Tag-driven release: `git tag v1.0.0 && git push origin v1.0.0`

Slack bildirimi: deploy başlangıcı/sonu için `secrets.SLACK_WEBHOOK_URL` ile.

## Klasör Yapısı

```
n11_final_case/
├── backend/                    Maven multi-module parent
│   ├── common/                 paylaşılan event DTO'ları, saga topology, JwtParser, CorrelationId filter
│   ├── api-gateway/            Spring Cloud Gateway
│   ├── auth-service/           kayıt + login + JWT
│   ├── product-service/        katalog + pagination
│   ├── cart-service/           sepet CRUD + saga consumer
│   ├── order-service/          checkout + saga publisher + payment-event consumer
│   ├── payment-service/        Iyzico + saga participant
│   ├── notification-service/   saga fan-out → Slack
│   └── Dockerfile              tüm servisler için tek multi-stage build (ARG SERVICE)
├── frontend/                   React + Vite + Tailwind + React Router 6
│   ├── src/{api,components,pages,state,utils}
│   └── Dockerfile + nginx.conf
├── infra/
│   ├── postgres-init/          multi-DB initdb script
│   └── aws/                    Beanstalk Dockerrun template
├── http/                       IntelliJ/VSCode REST Client requests
├── docs/                       architecture, saga, cicd, aws
├── .github/workflows/          backend.yml, frontend.yml, deploy.yml
├── docker-compose.yml          tek komutla full stack
└── .env.example
```

---

> Bu repo n11 TalentHub bootcamp final case projesi olarak yazılmıştır.
> Choreography saga + atomic commits + clean code önceliklidir.
