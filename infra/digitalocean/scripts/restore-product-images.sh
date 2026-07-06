#!/usr/bin/env bash
#
# One-shot recovery: re-populates the n11-products MinIO bucket with the
# 40 catalog product images after a server migration wiped object storage.
# Downloads each image from its original Unsplash source (the URLs the
# V8/V9 seed migrations used before V11 flipped image_url to the CDN) and
# uploads it to products/<slug>.jpg, then ensures the bucket is public-read.
#
# Run on the droplet:  bash /opt/n11/scripts/restore-product-images.sh
# Idempotent: re-running just re-uploads (overwrites) the same objects.

set -euo pipefail

DC="docker compose -f /opt/n11/docker-compose.prod.yml --env-file /opt/n11/.env"

echo "==> ensuring bucket + public-read policy"
$DC exec -T minio sh -c '
  mc alias set local http://localhost:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null
  mc mb -p local/n11-products >/dev/null 2>&1 || true
  mc anonymous set download local/n11-products >/dev/null
'

ok=0; fail=0
up() {
  if curl -fsSL --max-time 45 "$2" | $DC exec -T minio mc pipe "local/n11-products/products/$1.jpg" >/dev/null 2>&1; then
    echo "  OK  $1"; ok=$((ok+1))
  else
    echo "  FAIL $1"; fail=$((fail+1))
  fi
}

echo "==> uploading 40 product images"
up "iphone-15-pro-256gb" "https://images.unsplash.com/photo-1696446702183-be01a4f01097?w=600&h=600&fit=crop&auto=format&q=80"
up "samsung-galaxy-s24" "https://images.unsplash.com/photo-1610945265064-0e34e5519bbf?w=600&h=600&fit=crop&auto=format&q=80"
up "macbook-air-m3-13" "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=600&h=600&fit=crop&auto=format&q=80"
up "sony-wh-1000xm5" "https://images.unsplash.com/photo-1618366712010-f4ae9c647dcb?w=600&h=600&fit=crop&auto=format&q=80"
up "beyaz-pamuklu-tisort" "https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=600&h=600&fit=crop&auto=format&q=80"
up "slim-fit-kot-pantolon" "https://images.unsplash.com/photo-1542272604-787c3835535d?w=600&h=600&fit=crop&auto=format&q=80"
up "deri-cuzdan" "https://images.unsplash.com/photo-1627123424574-724758594e93?w=600&h=600&fit=crop&auto=format&q=80"
up "3-kisilik-kanepe" "https://images.unsplash.com/photo-1555041469-a586c61ea9bc?w=600&h=600&fit=crop&auto=format&q=80"
up "pamuk-nevresim" "https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?w=600&h=600&fit=crop&auto=format&q=80"
up "kosu-ayakkabisi" "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=600&h=600&fit=crop&auto=format&q=80"
up "yoga-mati" "https://images.unsplash.com/photo-1592432678016-e910b452f9a2?w=600&h=600&fit=crop&auto=format&q=80"
up "sefiller" "https://images.unsplash.com/photo-1544947950-fa07a98d237f?w=600&h=600&fit=crop&auto=format&q=80"
up "atomic-habits" "https://images.unsplash.com/photo-1550399105-c4db5fb85c18?w=600&h=600&fit=crop&auto=format&q=80"
up "airpods-pro-2" "https://images.unsplash.com/photo-1606220588913-b3aacb4d2f37?w=600&h=600&fit=crop&auto=format&q=80"
up "apple-watch-series-9" "https://images.unsplash.com/photo-1546868871-7041f2a55e12?w=600&h=600&fit=crop&auto=format&q=80"
up "ipad-air-11-m2" "https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?w=600&h=600&fit=crop&auto=format&q=80"
up "logitech-mx-master-3s" "https://images.unsplash.com/photo-1527864550417-7fd91fc51a46?w=600&h=600&fit=crop&auto=format&q=80"
up "beyaz-sneaker" "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=600&h=600&fit=crop&auto=format&q=80"
up "deri-kadin-cantasi" "https://images.unsplash.com/photo-1584917865442-de89df76afd3?w=600&h=600&fit=crop&auto=format&q=80"
up "klasik-kol-saati" "https://images.unsplash.com/photo-1524805444758-089113d48a6d?w=600&h=600&fit=crop&auto=format&q=80"
up "aromaterapi-mum" "https://images.unsplash.com/photo-1602874801007-aa30c9d3a4f9?w=600&h=600&fit=crop&auto=format&q=80"
up "dijital-kahve-makinesi" "https://images.unsplash.com/photo-1572119865084-43c285814d63?w=600&h=600&fit=crop&auto=format&q=80"
up "modern-yer-lambasi" "https://images.unsplash.com/photo-1507473885765-e6ed057f782c?w=600&h=600&fit=crop&auto=format&q=80"
up "dumbell-set-10kg" "https://images.unsplash.com/photo-1638536532686-d610adfc8e5c?w=600&h=600&fit=crop&auto=format&q=80"
up "akilli-su-sisesi" "https://images.unsplash.com/photo-1602143407151-7111542de6e8?w=600&h=600&fit=crop&auto=format&q=80"
up "pilates-bandi" "https://images.unsplash.com/photo-1518611012118-696072aa579a?w=600&h=600&fit=crop&auto=format&q=80"
up "chanel-no5-edp-100ml" "https://images.unsplash.com/photo-1541643600914-78b084683601?w=600&h=600&fit=crop&auto=format&q=80"
up "the-ordinary-niacinamide" "https://images.unsplash.com/photo-1612817288484-6f916006741a?w=600&h=600&fit=crop&auto=format&q=80"
up "maybelline-lipstick" "https://images.unsplash.com/photo-1586495777744-4413f21062fa?w=600&h=600&fit=crop&auto=format&q=80"
up "prima-bebek-bezi-5-no" "https://images.unsplash.com/photo-1555252333-9f8e92e65df9?w=600&h=600&fit=crop&auto=format&q=80"
up "avent-biberon-260ml" "https://images.unsplash.com/photo-1564594985645-26d6e51d40b8?w=600&h=600&fit=crop&auto=format&q=80"
up "chicco-bebek-arabasi" "https://images.unsplash.com/photo-1576091160550-2173dba999ef?w=600&h=600&fit=crop&auto=format&q=80"
up "lego-duplo-tren" "https://images.unsplash.com/photo-1518946222227-364f22132616?w=600&h=600&fit=crop&auto=format&q=80"
up "maxicosi-oto-koltugu" "https://images.unsplash.com/photo-1612538498456-e861df91d4d0?w=600&h=600&fit=crop&auto=format&q=80"
up "sulama-hortumu-25m" "https://images.unsplash.com/photo-1599629954294-14df9f8291bc?w=600&h=600&fit=crop&auto=format&q=80"
up "orwell-1984" "https://images.unsplash.com/photo-1495640388908-05fa85288e61?w=600&h=600&fit=crop&auto=format&q=80"
up "suc-ve-ceza" "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=600&h=600&fit=crop&auto=format&q=80"
up "sapiens" "https://images.unsplash.com/photo-1589998059171-988d887df646?w=600&h=600&fit=crop&auto=format&q=80"
up "loreal-elseve-sampuan" "https://images.unsplash.com/photo-1556228720-195a672e8a03?w=600&h=600&fit=crop&auto=format&q=80"
up "bahce-makasi-3lu" "https://images.unsplash.com/photo-1416879595882-3373a0480b5b?w=600&h=600&fit=crop&auto=format&q=80"

echo
echo "==> done: $ok uploaded, $fail failed"
