# Deployment — DigitalOcean Droplet (free-tier friendly)

> Tüm pipeline ücretsiz tier'larla çalışır:
> - **GHCR (GitHub Container Registry)** image push'ları için ücretsiz
> - **GitHub Actions** public repo'da 2000 dakika/ay ücretsiz
> - **Slack incoming webhook** ücretsiz
> - **DigitalOcean** yeni hesap için 200 USD / 60 gün kredi; sonrasında en küçük droplet ~4 USD/ay
> - **Groq** ücretsiz LLM API (`console.groq.com`)

## Hedef Mimari

```
                  GitHub Actions                              DigitalOcean
   ┌─────────────────────────────────┐         ┌──────────────────────────────────┐
   │  push to main / tag v*          │         │  Droplet (Ubuntu 24.04, 1-2 GB)  │
   │   ├─► mvn jib:build → GHCR      │  SSH    │   ┌──────────────────────────┐   │
   │   ├─► docker build  → GHCR      │ ──────► │   │  docker compose pull     │   │
   │   ├─► scp infra/ → /opt/n11     │  scp    │   │  docker compose up -d    │   │
   │   ├─► ssh: compose up -d        │         │   └──────────────────────────┘   │
   │   └─► Slack webhook             │         │   Caddy 80/443 → frontend + GW  │
   └─────────────────────────────────┘         └──────────────────────────────────┘
```

## İlk kurulum

### 1. DigitalOcean droplet aç

- Region: kullanıcılarına en yakın olan (Frankfurt / London for TR)
- OS: Ubuntu 24.04 LTS
- Plan: **Basic — Regular Intel — $6/mo (1 vCPU, 1 GB RAM)** test için yeterli; üretim trafiği için **2 GB RAM** önerilir
- SSH key: GitHub Actions'ın kullanacağı key'i ekle (aşağıdaki adımda üreteceğiz)

### 2. SSH key oluştur (yerelde)

```bash
ssh-keygen -t ed25519 -f ~/.ssh/n11_deploy -C "n11-deploy"
# public key (~/.ssh/n11_deploy.pub) — droplet'e ekle
# private key (~/.ssh/n11_deploy)     — GitHub secret'a koy (DO_SSH_KEY)
```

### 3. Droplet'i bootstrap et

```bash
ssh root@DROPLET_IP 'bash -s' < infra/digitalocean/setup-droplet.sh
```

Bu script:
- Docker + Compose plugin kurar
- `deploy` user'ı oluşturup `docker` group'una ekler
- `/opt/n11` dizinini hazırlar
- `ufw` firewall ile 22/80/443 açar

### 4. `.env` dosyasını koy

`infra/digitalocean/.env.example` şablonunu kullan, gerçek değerlerle doldur ve droplet'e yerleştir:

```bash
scp infra/digitalocean/.env.example root@DROPLET_IP:/opt/n11/.env
ssh root@DROPLET_IP 'chown deploy:deploy /opt/n11/.env && chmod 600 /opt/n11/.env'
ssh root@DROPLET_IP 'nano /opt/n11/.env'    # gerçek key'leri gir
```

### 5. GitHub Actions secrets

Repo → Settings → Secrets and variables → Actions:

| Secret | Değer |
|---|---|
| `DO_DROPLET_HOST` | Droplet'in public IP'si |
| `DO_DROPLET_USER` | `deploy` |
| `DO_SSH_KEY` | `~/.ssh/n11_deploy`'ın içeriği (private key) |
| `SLACK_WEBHOOK_URL` | Slack incoming webhook URL'i |

GitHub Actions'ın GHCR'a push yetkisi için ayrıca bir şeye ihtiyaç yok — `secrets.GITHUB_TOKEN`
ve `permissions.packages: write` workflow'da zaten ayarlı.

### 6. GHCR paketlerini public yap (ücretsiz pull için)

İlk push'tan sonra Settings → Packages → her paket için **Change visibility → Public**.
Böylece droplet için GHCR_TOKEN'a gerek kalmaz, anonim pull yapılabilir.

(Private kalırsa `.env`'a `GHCR_OWNER` ve `GHCR_TOKEN` ekle; deploy step otomatik login olur.)

### 7. Domain (opsiyonel)

`.env`'da `DOMAIN=n11.example.com` set et. Caddy otomatik Let's Encrypt sertifikası alır.
DNS A record'unu droplet IP'sine yönlendir.

## Deploy akışı

`main`'e push veya `v*` tag → `deploy.yml` çalışır:

1. **build-images** — her servis için `mvn jib:build` ile GHCR'a push (`<owner>/<svc>:<sha>` ve `latest`)
2. **deploy-droplet** — `scp` ile compose dosyalarını droplet'e gönderir, `ssh` ile pull + restart
3. **notify** — Slack'e renkli attachment gönderir (build/deploy durumu, commit, run linki)

Manual deploy için: Actions → deploy → Run workflow → opsiyonel image_tag.

## Slack bildirimi

Her workflow sonu (success veya failure) Slack'e mesaj gider — `secrets.SLACK_WEBHOOK_URL` ile.
Mesaj formatı:

```
🚀 n11-final-case — Deploy OK
Tag: a1b2c3d   Ref: main
Build: success  Deploy: success
Run #42 · a1b2c3d · samed
```

Webhook ayarlanmamışsa notify step "skipping" diyerek başarıyla geçer — pipeline kırılmaz.

## Maliyet özeti

| Kalem | Ücret |
|---|---|
| GitHub Actions (public repo) | **Free** (2000 min/mo) |
| GHCR storage + bandwidth | **Free** (public images) |
| DO droplet (1 GB) | ~$4-6/mo (yeni hesap 60 gün $200 kredi) |
| Slack webhook | **Free** |
| Groq API | **Free** (rate-limited) |
| Iyzico sandbox | **Free** |
| **Toplam** (ilk 60 gün) | **$0** |
| **Toplam** (sonrası) | **~$4-6/ay** |

## Sorun giderme

```bash
# droplet'te servisleri görüntüle
ssh deploy@DROPLET_IP 'cd /opt/n11 && docker compose -f docker-compose.prod.yml --env-file .env ps'

# tek servis logu
ssh deploy@DROPLET_IP 'cd /opt/n11 && docker compose -f docker-compose.prod.yml --env-file .env logs -f order-service'

# rebuild olmadan elle deploy (CI bypass)
ssh deploy@DROPLET_IP 'cd /opt/n11 && docker compose -f docker-compose.prod.yml --env-file .env pull && docker compose -f docker-compose.prod.yml --env-file .env up -d'
```
