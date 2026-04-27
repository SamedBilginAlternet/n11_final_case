ALTER TABLE products
    ADD COLUMN rating_average NUMERIC(3,2) NOT NULL DEFAULT 0,
    ADD COLUMN rating_count   INTEGER       NOT NULL DEFAULT 0;

UPDATE products SET rating_average = 4.7, rating_count =  214 WHERE slug = 'iphone-15-pro-256gb';
UPDATE products SET rating_average = 4.5, rating_count =  178 WHERE slug = 'samsung-galaxy-s24';
UPDATE products SET rating_average = 4.8, rating_count =  102 WHERE slug = 'macbook-air-m3-13';
UPDATE products SET rating_average = 4.6, rating_count =  331 WHERE slug = 'sony-wh-1000xm5';
UPDATE products SET rating_average = 4.3, rating_count =  892 WHERE slug = 'beyaz-pamuklu-tisort';
UPDATE products SET rating_average = 4.4, rating_count =  504 WHERE slug = 'slim-fit-kot-pantolon';
UPDATE products SET rating_average = 4.2, rating_count =  273 WHERE slug = 'deri-cuzdan';
UPDATE products SET rating_average = 4.5, rating_count =   88 WHERE slug = '3-kisilik-kanepe';
UPDATE products SET rating_average = 4.6, rating_count =  410 WHERE slug = 'pamuk-nevresim';
UPDATE products SET rating_average = 4.4, rating_count =  617 WHERE slug = 'kosu-ayakkabisi';
UPDATE products SET rating_average = 4.7, rating_count = 1208 WHERE slug = 'yoga-mati';
UPDATE products SET rating_average = 4.8, rating_count =  462 WHERE slug = 'sefiller';
UPDATE products SET rating_average = 4.9, rating_count = 2031 WHERE slug = 'atomic-habits';
