-- Frontend nav (frontend/src/data/navCategories.js) lists 9 slugs, the V2
-- seed only created 5.  Without these rows /api/products?category=<slug>
-- returns 404 the moment the user clicks the corresponding tile.  Insert
-- the missing 4 as empty categories — products can be backfilled later
-- via the admin panel.
INSERT INTO categories (name, slug, description) VALUES
    ('Süper Fırsatlar', 'super-firsatlar', 'Günün öne çıkan ürünleri'),
    ('Kozmetik',        'kozmetik',        'Cilt bakım, makyaj, parfüm'),
    ('Anne & Bebek',    'anne-bebek',      'Bebek bakımı ve oyuncak'),
    ('Oto & Bahçe',     'oto-bahce',       'Oto aksesuar ve bahçe ürünleri')
ON CONFLICT (slug) DO NOTHING;
