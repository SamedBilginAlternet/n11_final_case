#!/usr/bin/env bash
#
# Bootstraps the n11-products bucket on the MinIO container + a scoped
# service account for product-service uploads.  Idempotent: re-running
# is safe and will NOT regenerate the service-account credentials
# (so you don't accidentally invalidate the keys saved in Infisical).
#
# Usage on the droplet:
#   cd /opt/n11
#   bash infra/digitalocean/scripts/bootstrap-minio.sh
#
# Prereq: `docker compose up -d minio` already ran and the container is
# healthy.  /opt/n11/.env must define MINIO_ROOT_USER + MINIO_ROOT_PASSWORD.
#
# Output: on first run, Access Key + Secret Key for the service account
# are printed once.  Save them to Infisical as S3_ACCESS_KEY / S3_SECRET_KEY,
# re-sync .env, then redeploy product-service to pick them up.

set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-/opt/n11/docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-/opt/n11/.env}"
BUCKET="${S3_BUCKET:-n11-products}"

if [[ ! -f "$COMPOSE_FILE" ]]; then
    echo "✗ compose file not found: $COMPOSE_FILE" >&2
    exit 1
fi

# We run mc *inside* the minio container — image already ships /usr/bin/mc
# and the MINIO_ROOT_* env is already injected.  Saves us from publishing
# port 9000 to the host or installing mc on the droplet.
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" exec -T minio sh -e <<EOF
set -e

mc alias set local http://localhost:9000 "\$MINIO_ROOT_USER" "\$MINIO_ROOT_PASSWORD" >/dev/null

if mc ls "local/${BUCKET}" >/dev/null 2>&1; then
    echo "✓ bucket ${BUCKET} already exists"
else
    mc mb "local/${BUCKET}"
    echo "+ bucket ${BUCKET} created"
fi

# Anonymous read at object level — /\${BUCKET}/products/<slug>.jpg GETs
# work without auth.  PUT/DELETE still require the scoped service-account.
mc anonymous set download "local/${BUCKET}" >/dev/null
echo "✓ anonymous read policy applied to ${BUCKET}"

cat > /tmp/uploader-policy.json <<'JSON'
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:DeleteObject", "s3:GetObject"],
      "Resource": ["arn:aws:s3:::${BUCKET}/*"]
    },
    {
      "Effect": "Allow",
      "Action": ["s3:ListBucket"],
      "Resource": ["arn:aws:s3:::${BUCKET}"]
    }
  ]
}
JSON

# svcacct ls returns at least one row when accounts exist.  We treat
# "any existing svcacct on root" as "already bootstrapped" so a
# subsequent run can't print stale or surprise creds.
if mc admin user svcacct ls local "\$MINIO_ROOT_USER" 2>/dev/null | tail -n +2 | grep -q .; then
    echo "✓ service account already exists; skipping create"
    echo "  → To rotate: mc admin user svcacct rm local <ACCESS_KEY>, then re-run this script."
else
    mc admin user svcacct add local "\$MINIO_ROOT_USER" \
        --name "products-uploader" \
        --description "product-service uploader for ${BUCKET} bucket" \
        --policy /tmp/uploader-policy.json
    echo
    echo "↑ Save Access Key + Secret Key above to Infisical as:"
    echo "    S3_ACCESS_KEY  =  <Access Key>"
    echo "    S3_SECRET_KEY  =  <Secret Key>"
    echo
    echo "Then: bash /opt/n11/sync-env.sh && docker compose up -d product-service"
fi

rm -f /tmp/uploader-policy.json
EOF

echo
echo "✓ MinIO bootstrap complete."
