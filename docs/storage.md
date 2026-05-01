# Object Storage — MinIO + AWS S3 SDK

**Bu doküman:** Ürün resimleri nerede duruyor, nasıl yükleniyor, nasıl
servis ediliyor. AWS S3 SDK'yı production'da MinIO ile çalıştırma kararı
ve gerçek S3'e geçiş yolu.

- [1. Niye MinIO](#1-niye-minio)
- [2. Mimari](#2-mimari)
- [3. Kod Yolu — S3 SDK](#3-kod-yolu--s3-sdk)
- [4. Bucket Düzeni & İsimlendirme](#4-bucket-düzeni--isimlendirme)
- [5. Secrets](#5-secrets)
- [6. Bootstrap — İlk Kurulum](#6-bootstrap--ilk-kurulum)
- [7. Migration — Unsplash → MinIO](#7-migration--unsplash--minio)
- [8. AWS S3'e Geçiş](#8-aws-s3e-geçiş)
- [9. Trade-off'lar](#9-trade-offlar)

---

## 1. Niye MinIO

Önceki durum: ürün resimleri `images.unsplash.com` hot-link'leriyle
geliyordu. Sorunlar:
- Unsplash photo'su author tarafından silinince catalog tile'ı patlıyor
- Hot-link'lere CDN rate-limit + occasional 5xx
- Branding zayıf — `images.unsplash.com` URL'leri görünüyor

Üç alternatif değerlendirildi:

| Çözüm | Pro | Con |
|---|---|---|
| **AWS S3 (managed)** | Industry-standard, jüri tanır | Kart bilgisi gerek, free tier sonrası faturalanır, portfolio için over-provision |
| **Caddy `file_server`** | Sıfır kurulum | Upload API yok, S3 SDK kullanılmıyor (CV bullet kaybı) |
| **MinIO (self-host)** | $0, S3-compatible API → AWS SDK aynen çalışır, kendi domain | Ekstra container, droplet ölünce backup sen sorumlu |

Seçim: **MinIO**. Code path AWS SDK kullandığı için "S3 SDK ile yazıldım"
yetkinliği gerçek — sadece `endpoint` config değişince real S3'e
taşınabilir (bkz. [§ 8](#8-aws-s3e-geçiş)).

---

## 2. Mimari

```mermaid
flowchart LR
    subgraph Browser
        UI[Frontend]
        Admin[Admin Panel]
    end

    subgraph Droplet[DigitalOcean Droplet]
        Caddy[Caddy<br/>auto-TLS]
        subgraph Network[docker network]
            PS[product-service<br/>S3Client]
            MinIO[(MinIO<br/>:9000 API<br/>:9001 console)]
        end
    end

    UI -->|GET image<br/>cdn.n11proje...| Caddy
    Caddy -->|reverse_proxy| MinIO
    Admin -->|POST multipart<br/>/api/products/admin/{id}/image| Caddy
    Caddy -->|api-gateway| PS
    PS -.->|putObject<br/>S3 SDK| MinIO
```

**İki ayrı yol:**
- **Read** (browser): `https://cdn.n11proje.samedbilgin.com/n11-products/products/<slug>.jpg`
  → Caddy → MinIO `:9000` → bucket'tan dosya. Anonymous read policy
  sayesinde auth yok; CDN davranışı (immutable cache header).
- **Write** (admin): `POST /api/products/admin/{id}/image` (multipart)
  → api-gateway → product-service → S3 SDK `putObject` → MinIO
  internal API. PUT scoped service-account key kullanır, anonymous değil.

---

## 3. Kod Yolu — S3 SDK

| Sorumluluk | Dosya |
|---|---|
| SDK config (endpointOverride, path-style) | [`backend/product-service/src/main/java/com/n11/product/config/S3Config.java`](../backend/product-service/src/main/java/com/n11/product/config/S3Config.java) |
| Properties binding | [`backend/product-service/src/main/java/com/n11/product/config/S3Properties.java`](../backend/product-service/src/main/java/com/n11/product/config/S3Properties.java) |
| Upload service (validation + putObject) | [`backend/product-service/src/main/java/com/n11/product/service/ProductImageService.java`](../backend/product-service/src/main/java/com/n11/product/service/ProductImageService.java) |
| HTTP endpoint (multipart, ADMIN gate) | [`backend/product-service/src/main/java/com/n11/product/api/admin/ProductImageController.java`](../backend/product-service/src/main/java/com/n11/product/api/admin/ProductImageController.java) |
| Integration test (real MinIO container) | [`backend/product-service/src/test/java/com/n11/product/service/ProductImageServiceIT.java`](../backend/product-service/src/test/java/com/n11/product/service/ProductImageServiceIT.java) |

**Kritik ayrıntılar:**
- `S3Config` üstündeki `@ConditionalOnProperty(prefix = "storage.s3", name = "endpoint")`
  — boş endpoint'te bean wire edilmez, local dev MinIO'suz boot eder.
  ProductImageService o durumda 503 atar (upload hariç her şey çalışır).
- `pathStyleAccessEnabled(true)` — MinIO virtual-hosted-style adresleme
  yapmıyor. AWS SDK v2 default'u virtual-hosted; bu flag olmadan
  `SignatureDoesNotMatch` alırdık.
- `UrlConnectionHttpClient` — Apache HC + Netty transitive'leri (~6 MB)
  exclude edildi; admin upload düşük QPS olduğu için JDK built-in
  HttpURLConnection yeterli.

---

## 4. Bucket Düzeni & İsimlendirme

```
n11-products/                       ← bucket
└── products/
    ├── iphone-15-pro-256gb-1715000000000.jpg
    ├── samsung-galaxy-s24-1715000001234.jpg
    └── ...
```

**Key formatı:** `products/{slug}-{timestamp}.{ext}`
- `slug`: human-debuggable URL (`iphone-15-pro-256gb`)
- `timestamp` (ms): aynı ürüne re-upload yeni URL üretir → CDN cache
  invalidation explicit API call gerektirmez (URL zaten farklı)
- `ext`: jpg / png / webp — `MIME → ext` switch'le gelir

**Bucket policy:** anonymous **read** (object-level GET auth'suz),
PUT/DELETE service-account key gerektirir. Bu Caddy'nin `cdn.<domain>`
endpoint'ini imzalama gereği duymadan reverse-proxy etmesini sağlar.

---

## 5. Secrets

| Anahtar | Yer | Kim Üretir | Kim Tüketir |
|---|---|---|---|
| `MINIO_ROOT_USER` | Infisical | Manuel (bootstrap'ta) | minio container ENV |
| `MINIO_ROOT_PASSWORD` | Infisical | Manuel (32+ char random) | minio container ENV |
| `MINIO_CONSOLE_PASSWORD_HASH` | Infisical | `caddy hash-password` | Caddy basic_auth (admin UI) |
| `S3_ACCESS_KEY` | Infisical | bootstrap-minio.sh çıktısı | product-service |
| `S3_SECRET_KEY` | Infisical | bootstrap-minio.sh çıktısı | product-service |
| `S3_ENDPOINT` | Infisical (default `http://minio:9000`) | Sabit | product-service |
| `S3_BUCKET` | Infisical (default `n11-products`) | Sabit | product-service |
| `S3_PUBLIC_BASE_URL` | Infisical (`https://cdn.<domain>/n11-products`) | Sabit | product-service (URL composer) |
| `CDN_DOMAIN`, `MINIO_DOMAIN` | Infisical | Manuel (DNS A-record sonrası) | Caddy site blokları |

**Önemli:** `S3_ACCESS_KEY` / `S3_SECRET_KEY` MinIO root değil — bucket-scoped
service-account. `bootstrap-minio.sh` policy'si:
```json
{
  "Action": ["s3:PutObject", "s3:DeleteObject", "s3:GetObject"],
  "Resource": ["arn:aws:s3:::n11-products/*"]
}
```

---

## 6. Bootstrap — İlk Kurulum

```bash
# 0) DNS A-record (manuel, panel'den)
#    cdn.n11proje.samedbilgin.com    → droplet IP
#    minio.n11proje.samedbilgin.com  → droplet IP

# 1) MinIO root creds + console hash'ini Infisical'a yaz
#    MINIO_ROOT_USER, MINIO_ROOT_PASSWORD, MINIO_CONSOLE_PASSWORD_HASH
docker run --rm caddy:2-alpine caddy hash-password --plaintext '<parola>'

# 2) Sync + minio'yu ayağa kaldır
ssh deploy@droplet
cd /opt/n11
bash sync-env.sh
docker compose up -d minio caddy

# 3) Bucket + service-account oluştur
bash infra/digitalocean/scripts/bootstrap-minio.sh
# → Access Key + Secret Key yazdırılır.  İkisini Infisical'a:
#    S3_ACCESS_KEY, S3_SECRET_KEY
#    S3_PUBLIC_BASE_URL=https://cdn.n11proje.samedbilgin.com/n11-products

# 4) Yeni secrets'ı sync edip product-service'i restart
bash sync-env.sh
docker compose up -d product-service
```

---

## 7. Migration — Unsplash → MinIO

[`migrate-images-to-minio.sh`](../infra/digitalocean/scripts/migrate-images-to-minio.sh)
mevcut Unsplash hot-link'lerini indirir, bucket'a yükler, `V11__minio_image_urls.sql`
Flyway migration'ı üretir. Akış [`docs/storage.md` § 7] script'in başındaki
yorumda detaylı; özet:

```
postgres → SELECT slug, image_url WHERE image_url LIKE '%unsplash%'
       ↓
curl    → indir
       ↓
docker cp + mc cp → MinIO bucket'a yükle
       ↓
echo UPDATE … >> /tmp/V11__minio_image_urls.sql
       ↓
scp + git commit + deploy → Flyway prod DB'yi günceller
```

Re-run safe: zaten migrate edilmiş ürünler `LIKE '%unsplash%'` filter'ından
düşer, atlanır.

---

## 8. AWS S3'e Geçiş

Kod değişikliği yok, sadece env:

```bash
# Infisical → prod env güncelle
S3_ENDPOINT=https://s3.eu-central-1.amazonaws.com
S3_REGION=eu-central-1
S3_ACCESS_KEY=<IAM user access key>
S3_SECRET_KEY=<IAM user secret>
S3_BUCKET=n11-products-prod
S3_PUBLIC_BASE_URL=https://n11-products-prod.s3.eu-central-1.amazonaws.com
```

`pathStyleAccessEnabled(true)` AWS S3'te de geçerli — virtual-hosted
default ama path-style hâlâ kabul ediliyor. SDK call'ları aynı kalır.

Compose'dan `minio` servisi + `n11_minio_data` volume + `cdn.` /
`minio.` Caddy site blokları kaldırılır. Bucket migration'ı için
`mc mirror local/n11-products s3/n11-products-prod` (mc'nin AWS S3
support'u var).

---

## 9. Trade-off'lar

**Pro:**
- $0 operasyonel maliyet (mevcut droplet)
- AWS S3 SDK koddaki gerçek path → CV bullet veri var
- Kendi domain (`cdn.n11proje.samedbilgin.com`) — branding
- "Patlama" yok (Unsplash'in author silmesi ile alakasız)

**Con:**
- Droplet öldüğünde resimler de ölür (DB snapshot var, MinIO volume backup yok)
- Tek node — replication yok (AWS S3'ün 11-9 dayanıklılığı yok)
- Mitigasyon: portfolio için kabul; gerçek prod'da `restic` ile
  haftalık `n11_minio_data` snapshot DigitalOcean Spaces'e gider

**Bilinmeyen Limit:**
- DigitalOcean droplet bandwidth quota'sı: 1 TB/ay (basic plan).
  ~50 KB ortalama image × 41 ürün × hot reload sayısı = pratikte
  hiçbir zaman dolmaz, ama sınır var.

---

## İlgili Dokümanlar

- [`docs/secrets-management.md`](secrets-management.md) — Infisical akışı
- [`docs/services/product-service.md`](services/product-service.md) — product-service genel
- [`docs/deployment.md`](deployment.md) — droplet bootstrap playbook
- [`infra/digitalocean/scripts/bootstrap-minio.sh`](../infra/digitalocean/scripts/bootstrap-minio.sh) — bucket + svcacct
- [`infra/digitalocean/scripts/migrate-images-to-minio.sh`](../infra/digitalocean/scripts/migrate-images-to-minio.sh) — Unsplash → bucket migrator
