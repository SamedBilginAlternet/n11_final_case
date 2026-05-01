#!/usr/bin/env bash
#
# One-shot tool: walks every product whose image_url still points at
# Unsplash, downloads the photo, re-uploads it to the n11-products
# MinIO bucket, and emits a Flyway V11 migration that flips image_url
# at the CDN URL.
#
# Why we do this once: Unsplash hot-links are unstable — photos get
# removed by their authors, the CDN occasionally rate-limits, and we
# don't control any of it.  Self-hosting the bytes makes the catalog
# visually deterministic across deploys.
#
# Run order (one-time, on the droplet):
#   1) docker compose up -d minio
#   2) bash infra/digitalocean/scripts/bootstrap-minio.sh
#      → save the printed S3_ACCESS_KEY / S3_SECRET_KEY to Infisical
#      → bash sync-env.sh && docker compose up -d product-service
#   3) bash infra/digitalocean/scripts/migrate-images-to-minio.sh
#      → copy /tmp/V11__minio_image_urls.sql off the droplet:
#        scp deploy@droplet:/tmp/V11__minio_image_urls.sql \
#            backend/product-service/src/main/resources/db/migration/
#      → git add + commit + push → next deploy runs Flyway V11 against productdb
#
# Re-running is safe: it skips products whose URL no longer matches Unsplash
# (i.e. already migrated) so a partial run can be resumed.

set -euo pipefail

OUT_SQL="${OUT_SQL:-/tmp/V11__minio_image_urls.sql}"
COMPOSE_FILE="${COMPOSE_FILE:-/opt/n11/docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-/opt/n11/.env}"

if [[ ! -f "$ENV_FILE" ]]; then
    echo "✗ env file not found: $ENV_FILE" >&2
    exit 1
fi

# Source the env file so MINIO_ROOT_USER / POSTGRES_USER / S3_PUBLIC_BASE_URL
# are visible to this shell.  set -a auto-exports each var.
set -a; . "$ENV_FILE"; set +a

BUCKET="${S3_BUCKET:-n11-products}"
CDN_BASE="${S3_PUBLIC_BASE_URL:-}"

if [[ -z "$CDN_BASE" ]]; then
    echo "✗ S3_PUBLIC_BASE_URL is empty — set it in $ENV_FILE first" >&2
    echo "  (e.g. S3_PUBLIC_BASE_URL=https://cdn.n11proje.samedbilgin.com/n11-products)" >&2
    exit 1
fi

DC="docker compose -f $COMPOSE_FILE --env-file $ENV_FILE"
MINIO_CTR=$($DC ps -q minio || true)
if [[ -z "$MINIO_CTR" ]]; then
    echo "✗ minio container not running.  Start it first: docker compose up -d minio" >&2
    exit 1
fi

# Configure mc alias once; it persists in /root/.mc inside the container.
$DC exec -T minio mc alias set local http://localhost:9000 \
    "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null

# Fetch (slug, url) pairs in TSV form so a tab-split read loop handles
# them safely (URLs can contain spaces in query strings).
PAIRS=$($DC exec -T -e PGPASSWORD="$POSTGRES_PASSWORD" postgres \
    psql -U "$POSTGRES_USER" -d productdb -At -F$'\t' -c \
    "SELECT slug, image_url FROM products WHERE image_url LIKE '%unsplash%' ORDER BY id;")

if [[ -z "$PAIRS" ]]; then
    echo "✓ No Unsplash URLs left in products — nothing to migrate."
    exit 0
fi

TMP=$(mktemp -d)
trap "rm -rf $TMP" EXIT

cat > "$OUT_SQL" <<HDR
-- Generated $(date -u +%Y-%m-%dT%H:%M:%SZ) by migrate-images-to-minio.sh
-- Replaces unstable Unsplash hot-links with our own CDN URLs (MinIO
-- behind Caddy at cdn.<domain>).  See docs/storage.md for the rationale.
HDR

COUNT=0
FAILED=0
# fd 3 carries the loop input so the docker cp / docker exec calls
# inside the body don't accidentally consume our heredoc bytes (which
# would terminate the loop after the first iteration that uses docker —
# the bug that limited an earlier run to 2 of 41 rows).
while IFS=$'\t' read -r SLUG URL <&3; do
    [[ -z "$SLUG" ]] && continue
    LOCAL="$TMP/${SLUG}.jpg"
    echo "→ $SLUG"

    if ! curl -fsSL --max-time 30 "$URL" -o "$LOCAL"; then
        echo "  ✗ download failed — skipping" >&2
        FAILED=$((FAILED + 1))
        continue
    fi

    # docker cp to land the bytes in the container, then mc cp to land
    # them in MinIO.  Belt-and-suspenders: also redirect their stdin to
    # /dev/null so even if fd 3 weren't enough, docker can't reach the
    # loop input.
    docker cp "$LOCAL" "$MINIO_CTR:/tmp/upload.bin" </dev/null
    $DC exec -T minio mc cp --quiet \
        "/tmp/upload.bin" "local/${BUCKET}/products/${SLUG}.jpg" </dev/null >/dev/null
    $DC exec -T minio rm -f /tmp/upload.bin </dev/null

    echo "UPDATE products SET image_url = '${CDN_BASE}/products/${SLUG}.jpg' WHERE slug = '${SLUG}';" >> "$OUT_SQL"
    COUNT=$((COUNT + 1))
done 3<<< "$PAIRS"

echo
echo "✓ Migrated ${COUNT} images (failed: ${FAILED})"
echo "✓ Generated SQL: $OUT_SQL"
echo
echo "Next:"
echo "  scp deploy@<droplet>:${OUT_SQL} \\"
echo "      backend/product-service/src/main/resources/db/migration/"
echo "  git add + commit + push → Flyway V11 runs on next deploy"
