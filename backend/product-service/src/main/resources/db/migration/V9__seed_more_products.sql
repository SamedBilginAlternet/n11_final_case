-- Seed more products so the homepage's category-rail sections aren't empty.
-- V2 only covered 5 of the 9 storefront categories; V7 added the missing
-- categories as empty rows. This migration backfills them with 3-4 products
-- each plus extras for the existing categories so each rail has enough
-- inventory to look populated. Category lookup uses sub-selects so we
-- don't depend on V2/V7 insert order producing specific numeric ids.

-- Elektronik — extras for the "Teknoloji" rail
INSERT INTO products (name, slug, description, price, currency, stock, image_url, category_id) VALUES
    ('AirPods Pro 2', 'airpods-pro-2',
        'Apple AirPods Pro 2. nesil aktif gürültü engelleme.', 8990.00, 'TRY', 60,
        'https://images.unsplash.com/photo-1606220588913-b3aacb4d2f37?w=600&h=600&fit=crop&auto=format&q=80',
        (SELECT id FROM categories WHERE slug = 'elektronik')),
    ('Apple Watch Series 9', 'apple-watch-series-9',
        'GPS, 45 mm alüminyum kasa, sport loop.', 14990.00, 'TRY', 35,
        'https://images.unsplash.com/photo-1546868871-7041f2a55e12?w=600&h=600&fit=crop&auto=format&q=80',
        (SELECT id FROM categories WHERE slug = 'elektronik')),
    ('iPad Air 11" M2', 'ipad-air-11-m2',
        'Apple M2 işlemcili iPad Air 11 inç 128GB Wi-Fi.', 24990.00, 'TRY', 18,
        'https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?w=600&h=600&fit=crop&auto=format&q=80',
        (SELECT id FROM categories WHERE slug = 'elektronik')),
    ('Logitech MX Master 3S', 'logitech-mx-master-3s',
        'Sessiz tıklamalı, ergonomik kablosuz mouse.', 3299.00, 'TRY', 50,
        'https://images.unsplash.com/photo-1527864550417-7fd91fc51a46?w=600&h=600&fit=crop&auto=format&q=80',
        (SELECT id FROM categories WHERE slug = 'elektronik'));

-- Moda — extras
INSERT INTO products (name, slug, description, price, currency, stock, image_url, category_id) VALUES
    ('Beyaz Sneaker', 'beyaz-sneaker',
        'Klasik kesim, deri görünümlü beyaz spor ayakkabı.', 2499.00, 'TRY', 90,
        'https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=600&h=600&fit=crop&auto=format&q=80',
        (SELECT id FROM categories WHERE slug = 'moda')),
    ('Deri Kadın Çantası', 'deri-kadin-cantasi',
        'Hakiki deri orta boy kol çantası.', 1899.00, 'TRY', 45,
        'https://images.unsplash.com/photo-1584917865442-de89df76afd3?w=600&h=600&fit=crop&auto=format&q=80',
        (SELECT id FROM categories WHERE slug = 'moda')),
    ('Klasik Kol Saati', 'klasik-kol-saati',
        'Paslanmaz çelik, deri kayış, su geçirmez.', 2299.00, 'TRY', 30,
        'https://images.unsplash.com/photo-1524805444758-089113d48a6d?w=600&h=600&fit=crop&auto=format&q=80',
        (SELECT id FROM categories WHERE slug = 'moda'));

-- Ev & Yaşam — extras
INSERT INTO products (name, slug, description, price, currency, stock, image_url, category_id) VALUES
    ('Aromaterapi Mum Seti', 'aromaterapi-mum',
        'Soya wax üçlü mum seti, lavanta + sandal + vanilya.', 449.00, 'TRY', 80,
        'https://images.unsplash.com/photo-1602874801007-aa30c9d3a4f9?w=600&h=600&fit=crop&auto=format&q=80',
        (SELECT id FROM categories WHERE slug = 'ev-yasam')),
    ('Dijital Kahve Makinesi', 'dijital-kahve-makinesi',
        '15 bar basınç, otomatik süt köpürtücü.', 6999.00, 'TRY', 22,
        'https://images.unsplash.com/photo-1572119865084-43c285814d63?w=600&h=600&fit=crop&auto=format&q=80',
        (SELECT id FROM categories WHERE slug = 'ev-yasam')),
    ('Modern Yer Lambası', 'modern-yer-lambasi',
        'Pirinç gövde, mermer taban, dimmerli LED.', 1799.00, 'TRY', 25,
        'https://images.unsplash.com/photo-1507473885765-e6ed057f782c?w=600&h=600&fit=crop&auto=format&q=80',
        (SELECT id FROM categories WHERE slug = 'ev-yasam'));

-- Spor & Outdoor — extras
INSERT INTO products (name, slug, description, price, currency, stock, image_url, category_id) VALUES
    ('Dumbell Set 2x10kg', 'dumbell-set-10kg',
        'Kauçuk kaplı çift dambıl seti, 2x10 kg.', 1299.00, 'TRY', 40,
        'https://images.unsplash.com/photo-1638536532686-d610adfc8e5c?w=600&h=600&fit=crop&auto=format&q=80',
        (SELECT id FROM categories WHERE slug = 'spor')),
    ('Akıllı Su Şişesi', 'akilli-su-sisesi',
        '750ml termal, sıcaklık göstergeli paslanmaz çelik.', 599.00, 'TRY', 110,
        'https://images.unsplash.com/photo-1602143407151-7111542de6e8?w=600&h=600&fit=crop&auto=format&q=80',
        (SELECT id FROM categories WHERE slug = 'spor')),
    ('Pilates Bandı Seti', 'pilates-bandi',
        '3 farklı direnç seviyesinde 3''lü pilates bandı.', 249.00, 'TRY', 200,
        'https://images.unsplash.com/photo-1518611012118-696072aa579a?w=600&h=600&fit=crop&auto=format&q=80',
        (SELECT id FROM categories WHERE slug = 'spor'));

-- Kozmetik (yeni kategori — V2'de yoktu)
INSERT INTO products (name, slug, description, price, currency, stock, image_url, category_id) VALUES
    ('Chanel No 5 EDP 100ml', 'chanel-no5-edp-100ml',
        'Kadın klasik parfüm, eau de parfum 100 ml.', 6499.00, 'TRY', 15,
        'https://images.unsplash.com/photo-1541643600914-78b084683601?w=600&h=600&fit=crop&auto=format&q=80',
        (SELECT id FROM categories WHERE slug = 'kozmetik')),
    ('The Ordinary Niacinamide 30ml', 'the-ordinary-niacinamide',
        'Niacinamide 10% + Zinc 1% gözenek bakım serumu.', 299.00, 'TRY', 250,
        'https://images.unsplash.com/photo-1612817288484-6f916006741a?w=600&h=600&fit=crop&auto=format&q=80',
        (SELECT id FROM categories WHERE slug = 'kozmetik')),
    ('Maybelline Lipstick Color Sensational', 'maybelline-lipstick',
        'Mat bitişli kremsi ruj, klasik kırmızı ton.', 159.00, 'TRY', 300,
        'https://images.unsplash.com/photo-1586495777744-4413f21062fa?w=600&h=600&fit=crop&auto=format&q=80',
        (SELECT id FROM categories WHERE slug = 'kozmetik')),
    ('L''Oréal Elseve Şampuan 400ml', 'loreal-elseve-sampuan',
        'Yıpranmış saçlar için onarıcı şampuan.', 89.00, 'TRY', 400,
        'https://images.unsplash.com/photo-1556228720-195a672e8a03?w=600&h=600&fit=crop&auto=format&q=80',
        (SELECT id FROM categories WHERE slug = 'kozmetik'));

-- Anne & Bebek (yeni kategori)
INSERT INTO products (name, slug, description, price, currency, stock, image_url, category_id) VALUES
    ('Prima Aktif Bebek Bezi 5 No 90 lı', 'prima-bebek-bezi-5-no',
        '11-16 kg, 90 adet jumbo paket.', 549.00, 'TRY', 120,
        'https://images.unsplash.com/photo-1555252333-9f8e92e65df9?w=600&h=600&fit=crop&auto=format&q=80',
        (SELECT id FROM categories WHERE slug = 'anne-bebek')),
    ('Philips Avent Natural Biberon 260ml', 'avent-biberon-260ml',
        'Doğal emzik, 1+ ay, BPA içermez.', 219.00, 'TRY', 180,
        'https://images.unsplash.com/photo-1564594985645-26d6e51d40b8?w=600&h=600&fit=crop&auto=format&q=80',
        (SELECT id FROM categories WHERE slug = 'anne-bebek')),
    ('Chicco Goody Plus Bebek Arabası', 'chicco-bebek-arabasi',
        'Tek elle katlanabilir kompakt bebek arabası.', 5499.00, 'TRY', 12,
        'https://images.unsplash.com/photo-1576091160550-2173dba999ef?w=600&h=600&fit=crop&auto=format&q=80',
        (SELECT id FROM categories WHERE slug = 'anne-bebek')),
    ('LEGO Duplo Eğitici Tren Seti', 'lego-duplo-tren',
        '2+ yaş, 59 parçalı eğitici yapı seti.', 899.00, 'TRY', 65,
        'https://images.unsplash.com/photo-1518946222227-364f22132616?w=600&h=600&fit=crop&auto=format&q=80',
        (SELECT id FROM categories WHERE slug = 'anne-bebek'));

-- Oto & Bahçe (yeni kategori)
INSERT INTO products (name, slug, description, price, currency, stock, image_url, category_id) VALUES
    ('Maxi-Cosi Tobi Oto Koltuğu', 'maxicosi-oto-koltugu',
        '9-18 kg grup 1 araç oto koltuğu.', 6999.00, 'TRY', 14,
        'https://images.unsplash.com/photo-1612538498456-e861df91d4d0?w=600&h=600&fit=crop&auto=format&q=80',
        (SELECT id FROM categories WHERE slug = 'oto-bahce')),
    ('Bahçe Makas Seti 3''lü', 'bahce-makasi-3lu',
        'Budama, çiçek, dal makası — paslanmaz.', 449.00, 'TRY', 90,
        'https://images.unsplash.com/photo-1416879595882-3373a0480b5b?w=600&h=600&fit=crop&auto=format&q=80',
        (SELECT id FROM categories WHERE slug = 'oto-bahce')),
    ('Esnek Sulama Hortumu 25m', 'sulama-hortumu-25m',
        'Genişleyen tip 25 metre bahçe sulama hortumu.', 549.00, 'TRY', 70,
        'https://images.unsplash.com/photo-1599629954294-14df9f8291bc?w=600&h=600&fit=crop&auto=format&q=80',
        (SELECT id FROM categories WHERE slug = 'oto-bahce'));

-- Kitap & Müzik — extras
INSERT INTO products (name, slug, description, price, currency, stock, image_url, category_id) VALUES
    ('1984', 'orwell-1984',
        'George Orwell, 1984 (Türkçe baskı).', 199.00, 'TRY', 80,
        'https://images.unsplash.com/photo-1495640388908-05fa85288e61?w=600&h=600&fit=crop&auto=format&q=80',
        (SELECT id FROM categories WHERE slug = 'kitap')),
    ('Suç ve Ceza', 'suc-ve-ceza',
        'Dostoyevski, Suç ve Ceza (TR).', 249.00, 'TRY', 60,
        'https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=600&h=600&fit=crop&auto=format&q=80',
        (SELECT id FROM categories WHERE slug = 'kitap')),
    ('Sapiens: İnsan Türünün Kısa Tarihi', 'sapiens',
        'Yuval Noah Harari, Sapiens (TR).', 279.00, 'TRY', 90,
        'https://images.unsplash.com/photo-1589998059171-988d887df646?w=600&h=600&fit=crop&auto=format&q=80',
        (SELECT id FROM categories WHERE slug = 'kitap'));
