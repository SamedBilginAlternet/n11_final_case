# AWS Deployment

## Hedef Mimari

```
   Route 53 ──► CloudFront ──► Elastic Beanstalk Environment
                                  │   (multi-container Docker)
                                  │
                                  ├─► api-gateway      (8080)
                                  ├─► auth-service     (8081)
                                  ├─► product-service  (8082)
                                  ├─► cart-service     (8083)
                                  ├─► order-service    (8084)
                                  ├─► payment-service  (8085)
                                  ├─► notification-service (8086)
                                  ├─► frontend         (80)
                                  └─► rabbitmq         (5672, 15672)
                                       (in-cluster, küçük workload için)

   ┌─────────────────────────────────────────────────────────────────────┐
   │   Amazon RDS — PostgreSQL 16, multi-AZ db.t3.medium                 │
   │      Databases: authdb, productdb, cartdb, orderdb, paymentdb       │
   └─────────────────────────────────────────────────────────────────────┘
```

> Üretim ölçeğinde RabbitMQ için **Amazon MQ for RabbitMQ** veya **AmazonMQ Single Instance**
> ayrı bir managed service olarak kullanılmalı. Bu repoda demo amaçlı RabbitMQ aynı
> Beanstalk container grubunda çalışıyor.

## İlk kurulum (terraform yapmadan, console adımları)

1. **RDS PostgreSQL 16** — public erişimsiz, EB security group'tan 5432 izinli.
2. **EB Application + Environment**: `n11-final-case` / `n11-final-case-prod`,
   platform: *Multi-container Docker running on Amazon Linux 2*.
3. **EB instance role**: ECR pull izni (`AmazonEC2ContainerRegistryReadOnly`),
   CloudWatch Logs (`CloudWatchLogsFullAccess`).
4. **ECR repolar**: `n11/api-gateway`, `n11/auth-service`, `n11/product-service`,
   `n11/cart-service`, `n11/order-service`, `n11/payment-service`,
   `n11/notification-service`, `n11/frontend`.
5. **GitHub OIDC role**: deploy iş akışı için, EB + S3 + ECR
   üzerinde gerekli izinler. ARN'ı `secrets.AWS_DEPLOY_ROLE_ARN` olarak ekle.
6. **EB environment vars** (Console → Configuration → Software):

   | Anahtar | Değer |
   |---|---|
   | `RDS_HOSTNAME` | RDS endpoint |
   | `RDS_PORT` | 5432 |
   | `RDS_USERNAME` | n11 |
   | `RDS_PASSWORD` | (Secrets Manager) |
   | `JWT_SECRET` | min 32 byte secret |
   | `IYZICO_API_KEY` | sandbox veya production key |
   | `IYZICO_SECRET_KEY` | sandbox veya production key |
   | `IYZICO_BASE_URL` | https://sandbox-api.iyzipay.com (veya prod) |
   | `SLACK_WEBHOOK_URL` | (opsiyonel) |
   | `ECR_REGISTRY` | `<acct>.dkr.ecr.<region>.amazonaws.com` |
   | `IMAGE_TAG` | (workflow doldurur) |

## Tag tabanlı deploy

```bash
# yerelde
git tag v1.0.0
git push origin v1.0.0
```

GitHub Actions `deploy.yml` workflow tetiklenir:

1. Java 21 + Maven cache hazırlanır.
2. AWS OIDC credential alınır (kalıcı access key kullanılmaz).
3. Her servis için `mvn jib:build` ile ECR'a push.
4. Frontend için `docker build` + `docker push`.
5. `infra/aws/Dockerrun.aws.template.json` substitute edilip `Dockerrun.aws.json`
   oluşturulur, S3'e zip olarak yüklenir.
6. `aws elasticbeanstalk create-application-version` + `update-environment` ile EB güncellenir.
7. Slack webhook'a deploy başarılı/başarısız bildirimi gider.

## Logging & monitoring

- **CloudWatch Logs**: Beanstalk her container için stdout/stderr'i CW Logs'a aktarır.
  Filter pattern olarak `correlationId` ile arama yapılabilir.
- **RDS Performance Insights**: PostgreSQL slow query takibi için açılmalı.
- **EB Health Dashboard**: `/actuator/health` endpoint'i her servis için EB tarafından
  düzenli olarak çağrılır.

## Maliyet kontrolü

- t3.small * 2 instance + db.t3.micro RDS + ~5 GB log → ~50 USD/ay hobby ölçeği.
- Production'da RabbitMQ'yu Amazon MQ'ya, RDS'yi multi-AZ'ye taşıyın.
