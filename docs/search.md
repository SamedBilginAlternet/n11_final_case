# Search & Filtering — PostgreSQL Full-Text Search

**Bu doküman:** Ürün search'ün nasıl çalıştığı — niye PostgreSQL FTS, niye Elasticsearch
değil, tsvector + GIN nasıl tasarlandı, faceted filter mantığı.

---

## 1. Niye PostgreSQL FTS?

İlk versiyon `LIKE '%word%'` idi. Üç problem:

1. **Index kullanmıyor**: leading wildcard B-tree index'i bypass ediyor → her query full table scan.
2. **Order-sensitive**: "kablosuz kulaklık" → `'%kablosuz kulaklık%'` arar. Veri "kulaklık kablosuz"
   ise eşleşmez. Word-order bağımsız search yok.
3. **Diakritik problemi**: "şarj" → "sarj" eşleşmesi yok. Türkçe e-ticaret için kritik.

PostgreSQL FTS bu üçünü de çözüyor:
- GIN index = O(log n) arama.
- `tsvector` = token-level matching, word order dert değil.
- `unaccent` extension = "ş→s", "ü→u" otomatik.

### Niye Elasticsearch değil?

Düşünüldü, reddedildi:

| Konu | PostgreSQL FTS | Elasticsearch |
|---|---|---|
| Operasyonel maliyet | 0 (mevcut DB) | +1 cluster, +RAM, +sync logic |
| Multi-tenant search relevance tuning | Sınırlı | Profesyonel |
| Synonym, fuzzy match, "did you mean" | Sınırlı | Built-in |
| Analytics aggregation | OK | Mükemmel |
| Bizim ölçek | < 10K product | Milyonlarca product için |

Bizim ölçeğimizde PostgreSQL FTS **hızla yeterli**. Sync problemi yok (data Postgres'te zaten).
Reindex? Generated column → otomatik. ES eklemek operasyonel bagaj eklerdi, kazanç scope'u
ile orantılı değil.

Volume büyürse + relevance tuning gerekirse + "did you mean" istenirse → o zaman ES.

---

## 2. tsvector Generated Column

```sql
-- V5__product_full_text_search.sql
CREATE EXTENSION IF NOT EXISTS unaccent;

ALTER TABLE products
    ADD COLUMN search_tsv tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('turkish', unaccent(coalesce(name, ''))),        'A') ||
        setweight(to_tsvector('turkish', unaccent(coalesce(description, ''))), 'B')
    ) STORED;

CREATE INDEX ix_products_search_tsv ON products USING GIN (search_tsv);
```

### Anatomi

**`unaccent(coalesce(name, ''))`**: aksanları kaldır, null safety.
- `unaccent('şarj cihazı')` → `'sarj cihazi'`
- `unaccent('İPHONE')` → `'IPHONE'` (büyük İ → I)

**`to_tsvector('turkish', '...')`**: text'i token'lara böl + lemma'larını al.
- `to_tsvector('turkish', 'kablosuz kulaklıklar')` → `'kablosuz':1 'kulaklik':2`
- "kulaklıklar" → "kulaklik" (lemmatization, çoğul → tekil).

**`setweight(..., 'A')` ve `'B'`**: relevance ağırlığı.
- `'A'` (en yüksek): name. Title hit > body hit.
- `'B'`: description.
- Sonra `ts_rank` bu ağırlıkları skorlamada kullanır.

**`||` operator**: tsvector'ları birleştir.

**`STORED`**: hesaplanmış değer DB'de tutulur (vs `VIRTUAL` her okumada hesaplanır). Read-heavy
workload için STORED daha hızlı.

**`GENERATED ALWAYS AS ... STORED`**: Postgres her INSERT/UPDATE'te otomatik günceller.
Application-side denormalisation drift yok — `name` değişti ama `search_tsv` eski → imkansız.

### Niye 'turkish' config?

PostgreSQL'in built-in text search config'leri var: `english`, `turkish`, `german`, vb. Her biri
o dilin stopword listesi + stemmer'ı içerir.

`turkish` config:
- "ve", "ile", "için" gibi stopword'leri tsvector'a koymaz.
- "kullanıcılar" → "kullanici" (Turkish stemmer, suffix kaldırma).

Sınırlamalar:
- Türkçe stemmer **modest** — agglutinative dil için tam yeterli değil. "alıcılar" düzgün
  çalışır ama "alabilirsen" → "alabilirsen" (kompleks suffix kombinasyonu zor).
- Compound words handle edilmiyor: "iphone15" tek token. Ayrı yazılırsa tamam.

`unaccent` ile birleştirince Türkçe e-ticaret için **iyi yeterli**.

---

## 3. Search Query

### `plainto_tsquery` ile basit kullanıcı input'u

```java
// ProductSearchRepository
where.append(" AND p.search_tsv @@ plainto_tsquery('turkish', unaccent(:q)) ");
args.put("q", c.q().trim());
```

`plainto_tsquery('turkish', 'şarjlı kulaklık')`:
- unaccent applied: `'sarjli kulaklik'`
- Turkish stemmer: `'sarj' & 'kulaklik'`
- Dönen `tsquery` = `'sarj' & 'kulaklik'` (AND).

`@@` operator: tsvector ile tsquery match? `'sarj' & 'kulaklik'` ikisinin de kayıtta olması
şartı.

`unaccent(:q)`: query string'i de unaccent et — kayıt "sarj" diye saklanır, kullanıcı "şarj"
yazsa da match olsun.

### `to_tsquery` (advanced) reddedildi

`to_tsquery` operator'lar sunar (`'sarj' & !'eski'`, vb.) ama **kullanıcı input'u sanitize
etmek zor**. Yanlış syntax ile `to_tsquery` exception fırlatır → 500. `plainto_tsquery` her
input'u kabul eder, AND'le birleştirir, exception yok.

Trade-off: kullanıcı "OR" araması yapamaz. Bizim UX'imizde gerek yok.

### `phraseto_tsquery` bilinçli olmadı

Phrase search ("kablosuz kulaklık" — bu sırada) için `phraseto_tsquery` var. Çoğu kullanıcı
bunu beklemiyor — order-sensitive matching aslında kötü UX. AND yeterli.

---

## 4. Relevance Ranking — `ts_rank`

```java
case RELEVANCE -> (c.q() != null && !c.q().isBlank())
    ? " ORDER BY ts_rank(p.search_tsv, plainto_tsquery('turkish', unaccent(:q))) DESC, p.rating_count DESC "
    : " ORDER BY p.rating_count DESC, p.created_at DESC ";
```

Query var: `ts_rank` skorla, popularity ile tie-break.

Query yok: relevance anlamsız → popularity'ye göre sırala.

### `ts_rank` Hesaplaması

`ts_rank(vector, query, normalization=0)` defaults:
- Frequency'i sayar (token kaç kez vector'de geçti).
- `setweight` ağırlıklarını çoğaltır (A=1.0, B=0.4, C=0.2, D=0.1).
- Cover (kelime yakınlığı) hesaplar.

Bizim için:
- "iPhone 15 Pro Max" arandığında, `name` field'ında geçen ürünler **B-only** (description'da
  geçen) ürünlerden öne geçer. Çünkü name=A weight.
- Aynı document'te 2 token bulan, 1 token bulandan öne geçer.

### Normalization — defaults yeterli

`ts_rank` 5 normalization mode'u var (document length normalize etme). Default 0 = no
normalize. Document length'e göre penalty istenirse 1 (`log(doc_length)`) veya 32 mantıklı.
Bizde gerek yok — ürün açıklamaları benzer uzunlukta.

---

## 5. Filter Pipeline — Native SQL with Dynamic Predicate

```java
public Page<Product> search(SearchCriteria c, Pageable pageable) {
    StringBuilder where = new StringBuilder(" WHERE 1=1 ");
    Args args = new Args();

    if (c.q() != null && !c.q().isBlank()) {
        where.append(" AND p.search_tsv @@ plainto_tsquery('turkish', unaccent(:q)) ");
        args.put("q", c.q().trim());
    }
    if (c.categoryIds() != null && !c.categoryIds().isEmpty()) {
        where.append(" AND p.category_id IN (:categoryIds) ");
        args.put("categoryIds", c.categoryIds());
    }
    if (c.minPrice() != null) { where.append(" AND p.price >= :minPrice "); args.put("minPrice", c.minPrice()); }
    if (c.maxPrice() != null) { where.append(" AND p.price <= :maxPrice "); args.put("maxPrice", c.maxPrice()); }
    if (c.minRating() != null) { where.append(" AND p.rating_average >= :minRating "); args.put("minRating", c.minRating()); }
    if (c.inStockOnly()) { where.append(" AND p.stock > 0 "); }
    ...
}
```

### Niye Native SQL, JPQL Değil

JPQL'de `tsvector @@ plainto_tsquery(...)` yazılamaz — JPQL custom function bilmiyor.
Workaround'lar (Hibernate `@FunctionContributor` ile fonksiyon register et) var ama:
- Tek query için ayrı bir Hibernate config dosyası karmaşıklığı.
- `ts_rank` `ORDER BY` clause'unda — JPQL `ORDER BY function(...)` parsing limit'ler.

Native SQL **basit ve okunabilir**.

### Niye `Specifications` (JPA Criteria) değil

5 opsiyonel filter — Criteria API ile:
- Her filter için `Predicate p = ...; predicates.add(p);`
- Tip-güvenli ama 50 satır boilerplate.
- `tsvector` zaten yok.

`StringBuilder` + map argument bizim case için **sade ve ölçeklenebilir**.

### SQL Injection

`StringBuilder` ile inşa edilmiş SQL — injection riski? **Hayır**:
- Tüm değerler `:param` placeholder ile geçiyor — JDBC PreparedStatement ile bind ediliyor.
- WHERE clause **string concat** ediliyor ama **kullanıcı input'u SQL'e değil parametreye gidiyor**.
- Kullanıcı "OR 1=1" yazsa bile parameter olarak gider, SQL'e parsing edilmez.

Tehlike olur eğer `where.append(c.q())` yazsam (yapmıyorum). Burada `where.append(" AND ... :q ")`
+ `args.put("q", c.q().trim())` — fark kritik.

---

## 6. Faceted Search — Counts in Sidebar

User filter sidebar'da kategorileri görsün:
- Telefon (24)
- Kulaklık (12)
- Şarj (8)

Her birinin yanındaki sayı = "bu kategoriye geçersem kaç ürün matched olur?".

### Endpoint

`GET /api/products/facets?q=...&minPrice=...&...`

Response:
```json
{
  "categories": [
    {"id": 1, "name": "Telefon", "count": 24},
    {"id": 2, "name": "Kulaklık", "count": 12}
  ],
  "minPrice": 99,
  "maxPrice": 49999,
  "totalMatches": 36
}
```

### Implementasyon

```java
@SuppressWarnings("unchecked")
public List<Object[]> categoryFacets(SearchCriteria c) {
    StringBuilder where = ...;  // q + price + rating + stock applied
    Args args = ...;
    
    String sql = "SELECT c.id, c.name, count(p.id) "
            + "FROM categories c LEFT JOIN products p ON p.category_id = c.id "
            + where.toString().replace("WHERE 1=1", "AND 1=1")
            + " GROUP BY c.id, c.name ORDER BY count(p.id) DESC, c.name ASC ";
    Query q = em.createNativeQuery(sql);
    args.applyTo(q);
    return q.getResultList();
}
```

**Önemli detay**: `categoryIds` filter facet hesabında **uygulanmaz**. Niye:
- Kullanıcı "Telefon" seçti, ürün listesi "Telefon" + diğer filter'lar ile sınırlı.
- Sidebar'daki "Kulaklık (12)" değerini görmek isterse, hesap "şu anda Kulaklık seçilseydi
  kaç olur" sorusunu cevaplamalı. Yani **`categoryIds` filter'i çıkarılmış** halde count.
- Eğer dahil edilseydi, "Telefon" seçiliyken "Kulaklık (0)" gösterirdi → kullanıcı yanlış
  yorumlardı.

`q`, `minPrice`, `maxPrice`, `minRating`, `inStockOnly` korunur — bunlar tüm sonuç set'ini
narrow eder, kategori switch yaparken de geçerli.

### Price Range — Live Slider Bounds

```java
private BigDecimal[] priceRange(...) {
    String sql = "SELECT COALESCE(MIN(p.price), 0), COALESCE(MAX(p.price), 0) FROM products p WHERE 1=1 ";
    // (tüm filter uygulanır)
    Object[] row = (Object[]) query.getSingleResult();
    return new BigDecimal[] { ..., ... };
}
```

Slider 0..99999 hardcoded değil, **mevcut sonuç set'inin** min..max'ı. Kullanıcı "stoktakiler"
toggle'ladığında slider otomatik narrow olabilir (eğer stoğu varlar daha dar bir aralıktaysa).

UX detayı: kullanıcı 5000-10000 arası filtre koymuş, ardından kategori değiştiriyor — facet
endpoint kategori filter'sız çalıştığı için **bu filter'ları kayıtlı tutar** ve switch sonrası
da geçerli olur.

---

## 7. URL-Driven State (Frontend)

```
/catalog?q=iphone&categoryIds=1,2&minPrice=10000&maxPrice=50000&minRating=4&inStockOnly=true&sort=price_asc&page=2
```

Niye URL state, niye React state değil:
- **Deep link**: paylaşılabilir search.
- **Back/forward**: browser nav otomatik state restore.
- **No prop drilling**: filter sidebar URL okur, parent re-fetch eder, çocuk state senkron.

```js
function patchParams(patch) {
    const next = new URLSearchParams(params);
    Object.entries(patch).forEach(([k, v]) => {
        if (v == null || v === '') next.delete(k);
        else next.set(k, String(v));
    });
    next.set('page', '0');  // any filter change resets page
    setParams(next);
}
```

Page reset on filter change: kullanıcı 5. sayfadayken kategori değiştirirse 1. sayfaya döner —
yoksa "5. sayfadayım ama yeni filter set'inde 5. sayfa yok" empty result alır.

---

## 8. Pagination Performance

```java
String pageSql = "SELECT p.* FROM products p " + where + orderBy + " LIMIT :limit OFFSET :offset";
```

`OFFSET` derin sayfalarda yavaş — Postgres her seferinde tüm matching row'ları sayıp ilk N'i
atlar. Bizim ölçeğimizde (max 10K product, max 100 sayfa) acil değil.

**Keyset pagination** alternatifi: `WHERE p.id > :lastId ORDER BY p.id LIMIT N`. Sürekli
forward-pagination için ideal ama random-access ("sayfa 7'ye git") imkansız. UI sayfa numaraları
gösteriyor → keyset uygun değil.

---

## 9. Bilinçli Olarak Yapmadıklarımız

- **Synonym dictionary yok**: "tel" → "telefon" eşleşmesi yok. `tsearch synonyms` ile eklenir,
  scope dışı.
- **"Did you mean" / fuzzy match**: yazım hatasına tölerans yok. `pg_trgm` extension ile
  trigram similarity eklenebilir; volume büyürse.
- **Faceted price histogram**: bucket histogram (0-100 / 100-500 / ...) yok. Sadece min/max.
  UI tarafında gerekirse client-side bucket yapılabilir.
- **Search analytics**: Hangi query'ler çok arandı, hangileri 0 sonuçla bitti — log yok.
  Future feature.
- **Multi-language**: Sadece Türkçe config. Çoklu dil için per-language tsvector kolonu
  gerekir.

---

## İlgili Dokümanlar

- [`docs/services/product-service.md`](services/product-service.md) — ProductSearchRepository implementasyonu
- [`docs/caching.md`](caching.md) — search results cache'lenmiyor (niye)
