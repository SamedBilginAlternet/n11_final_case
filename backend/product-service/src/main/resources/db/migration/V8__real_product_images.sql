-- Replace the V2 picsum.photos placeholder URLs with curated Unsplash photos
-- that actually represent each product. picsum returned random landscapes
-- which made the catalog look like a stock-photo dump (an "iPhone" tile
-- rendering a sunset is the worst kind of demo polish).
--
-- All URLs are direct images.unsplash.com links with a uniform query string
-- to crop to 600×600, auto-format (WebP/AVIF when supported) and quality=80.
-- Unsplash's CDN serves these unauthenticated; if any specific photo gets
-- removed by its author, swap the photo-id segment with a fresh search hit
-- from https://unsplash.com/s/photos/<keyword>.
--
-- If we ever start caring about offline availability or hotlink reliability,
-- the right next step is downloading these into frontend/public/products/
-- and pointing image_url at /products/<slug>.jpg (a one-liner update here).

UPDATE products SET image_url = 'https://images.unsplash.com/photo-1696446702183-be01a4f01097?w=600&h=600&fit=crop&auto=format&q=80'
    WHERE slug = 'iphone-15-pro-256gb';

UPDATE products SET image_url = 'https://images.unsplash.com/photo-1610945265064-0e34e5519bbf?w=600&h=600&fit=crop&auto=format&q=80'
    WHERE slug = 'samsung-galaxy-s24';

UPDATE products SET image_url = 'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=600&h=600&fit=crop&auto=format&q=80'
    WHERE slug = 'macbook-air-m3-13';

UPDATE products SET image_url = 'https://images.unsplash.com/photo-1618366712010-f4ae9c647dcb?w=600&h=600&fit=crop&auto=format&q=80'
    WHERE slug = 'sony-wh-1000xm5';

UPDATE products SET image_url = 'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=600&h=600&fit=crop&auto=format&q=80'
    WHERE slug = 'beyaz-pamuklu-tisort';

UPDATE products SET image_url = 'https://images.unsplash.com/photo-1542272604-787c3835535d?w=600&h=600&fit=crop&auto=format&q=80'
    WHERE slug = 'slim-fit-kot-pantolon';

UPDATE products SET image_url = 'https://images.unsplash.com/photo-1627123424574-724758594e93?w=600&h=600&fit=crop&auto=format&q=80'
    WHERE slug = 'deri-cuzdan';

UPDATE products SET image_url = 'https://images.unsplash.com/photo-1555041469-a586c61ea9bc?w=600&h=600&fit=crop&auto=format&q=80'
    WHERE slug = '3-kisilik-kanepe';

UPDATE products SET image_url = 'https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?w=600&h=600&fit=crop&auto=format&q=80'
    WHERE slug = 'pamuk-nevresim';

UPDATE products SET image_url = 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=600&h=600&fit=crop&auto=format&q=80'
    WHERE slug = 'kosu-ayakkabisi';

UPDATE products SET image_url = 'https://images.unsplash.com/photo-1592432678016-e910b452f9a2?w=600&h=600&fit=crop&auto=format&q=80'
    WHERE slug = 'yoga-mati';

UPDATE products SET image_url = 'https://images.unsplash.com/photo-1544947950-fa07a98d237f?w=600&h=600&fit=crop&auto=format&q=80'
    WHERE slug = 'sefiller';

UPDATE products SET image_url = 'https://images.unsplash.com/photo-1550399105-c4db5fb85c18?w=600&h=600&fit=crop&auto=format&q=80'
    WHERE slug = 'atomic-habits';
