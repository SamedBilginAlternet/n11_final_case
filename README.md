# n11 Final Case — E-Ticaret

Spring Boot 3.3 / Java 21 mikroservisler, RabbitMQ üzerinde **choreography saga**, JWT
auth, Iyzico ödeme entegrasyonu, React + Vite + Tailwind frontend ve **GitHub Actions →
AWS Elastic Beanstalk + RDS** deploy boru hattı.

## İçindekiler

- [Mimari](#mimari)
- [Servisler](#servisler)
- [Saga Akışı](#saga-akışı)
- [Çalıştırma](#çalıştırma)
- [Test](#test)
- [CI/CD](#cicd)
- [AWS Deployment](#aws-deployment)

> Detaylı dokümantasyon `docs/` altındadır; mimari diyagramları, saga waterfall'ı ve
> Jenkins ↔ GitHub Actions karşılaştırması orada bulunur.

## Mimari

```
browser → frontend (nginx) → api-gateway → { auth, product, cart, order, payment }
                                   │
                                   ├─► PostgreSQL  (per-service DB)
                                   ├─► RabbitMQ    (saga.exchange / topic)
                                   └─► Slack       (notification webhook)
```

Daha fazlası `docs/architecture.md`.

## Servisler

| Servis | Port | DB | Görev |
|---|---|---|---|
| api-gateway | 8080 | — | Public giriş, JWT relay, Swagger aggregator |
| auth-service | 8081 | authdb | Kayıt, login, JWT, kullanıcı bilgisi |
| product-service | 8082 | productdb | Ürün listeleme (pagination), kategori, detay |
| cart-service | 8083 | cartdb | Sepet CRUD, OrderConfirmed → sepet temizleme |
| order-service | 8084 | orderdb | Checkout, sipariş yaşam döngüsü, saga publisher |
| payment-service | 8085 | paymentdb | Iyzico entegrasyonu, OrderCreated saga consumer |
| notification-service | 8086 | notificationdb | Slack bildirimleri |

## Saga Akışı

`docs/saga.md` dosyasında waterfall + compensation senaryosu.

## Çalıştırma

Yerelde tek komut:

```bash
cp .env.example .env
docker compose up --build
```

- Frontend → http://localhost:3000
- Gateway → http://localhost:8080
- Swagger UI → http://localhost:8080/swagger-ui.html
- RabbitMQ UI → http://localhost:15672 (guest / guest)

## Test

```bash
# Tüm servislerde
mvn -f backend/pom.xml verify

# Sadece bir servis
mvn -pl auth-service -am -f backend/pom.xml test

# Frontend
cd frontend && npm test
```

## CI/CD

GitHub Actions her push'ta:

1. Backend matrix (her servis için): unit + integration testler (Testcontainers)
2. Frontend: lint + build
3. Main branch'te: Jib ile Docker image build & push
4. Tag (`v*`): Elastic Beanstalk'a deploy + Slack bildirim

`docs/cicd.md` Jenkins ile karşılaştırmayı içerir.

## AWS Deployment

Bkz. `docs/aws.md` — Elastic Beanstalk multi-container + RDS PostgreSQL.

---

> Bu repo n11 TalentHub bootcamp final projesi olarak yazılmıştır.
