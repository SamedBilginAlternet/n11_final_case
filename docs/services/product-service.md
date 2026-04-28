# `product-service`

**Bu doküman:** Ürün katalogu, kategoriler, yorumlar, full-text search, AI öneriler, düşük stok scanner.

**Port:** 8082
**DB:** `productdb`
**Stack:** Spring Boot 3 + JPA + Flyway + Redis cache + RabbitMQ (publish-only) + native FTS
**External:** Groq API (opsiyonel, recommendations için)

---

## 1. Sorumluluklar

| Concern | Endpoint(ler) | Erişim |
|---|---|---|
| Browse + search | `GET /api/products?...` | Public |
| Faceted filter sidebar | `GET /api/products/facets?...` | Public |
| Detail | `GET /api/products/{id}`, `GET /api/products/slug/{slug}` | Public |
| Search-bar autocomplete | `GET /api/products/autocomplete?q=...` | Public |
| Reviews | `GET /api/products/{id}/reviews`, `PUT /api/products/{id}/reviews` | Public read, auth write |
| AI recommendations | `GET /api/products/{id}/recommendations` | Public |
| Categories list | `GET /api/categories` | Public |
| **Admin: product CRUD** | `POST/PUT/DELETE /api/products[/{id}]` | ADMIN |
| **Admin: category CRUD** | `POST/PUT/DELETE /api/categories[/{id}]` | ADMIN |
| **Admin: metrics** | `GET /api/products/admin/metrics?lowStockThreshold=10` | ADMIN |
| **Scheduled**: low-stock scan → RabbitMQ publish | (cron) | — |

---

## 2. Domain — `Product`, `Category`, `Review`

```java
// Product
@Entity @Table(name = "products", indexes = {
    @Index(name = "ix_products_category", columnList = "category_id"),
    @Index(name = "ix_products_name",     columnList = "name")
})
public class Product {
    @Id @GeneratedValue private Long id;
    private String name;          // 200
    private String slug;          // 220, UNIQUE
    private String description;   // TEXT
    private BigDecimal price;     // 12,2
    private String currency;      // 3 chars (TRY default)
    private Integer stock;        // ≥ 0
    private String imageUrl;
    private BigDecimal ratingAverage;  // 3,2 — 0.00..5.00
    private Integer ratingCount;
    @ManyToOne @JoinColumn(name = "category_id") private Category category;
    private Instant createdAt, updatedAt;
}
```

`ratingAverage` + `ratingCount` aggregat fields — review write'da `ReviewService.recomputeAggregate`
ile güncellenir. Dashboard query'leri (`top categories`, `low stock`) aggregate'leri direkt
`products` tablosundan okur — `reviews` tablosuna join gerekmez.

Detay: section §6 (Reviews).

### `search_tsv` Generated Column

V5 migration:

```sql
ALTER TABLE products
    ADD COLUMN search_tsv tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('turkish', unaccent(coalesce(name, ''))),        'A') ||
        setweight(to_tsvector('turkish', unaccent(coalesce(description, ''))), 'B')
    ) STORED;
CREATE INDEX ix_products_search_tsv ON products USING GIN (search_tsv);
```

Hibernate entity'de **mapped değil** — sadece DB'de var, Postgres otomatik güncelliyor.
`search()` query'si native SQL ile bu kolonu kullanır.

Detay: [`docs/search.md`](../search.md).

---

## 3. Search & Filtering

### `ProductController.list`

```java
@GetMapping
public PageResponse<ProductSummaryDto> list(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) String category,                // slug
        @RequestParam(name = "categoryIds", required = false) Set<Long> categoryIds,
        @RequestParam(required = false) BigDecimal minPrice,
        @RequestParam(required = false) BigDecimal maxPrice,
        @RequestParam(required = false) BigDecimal minRating,
        @RequestParam(name = "inStockOnly", defaultValue = "false") boolean inStockOnly,
        @RequestParam(required = false) String sort,
        @PageableDefault(size = 12) Pageable pageable
) { ... }
```

### `ProductSearchRepository` — Native SQL + Dynamic Predicate

```java
public Page<Product> search(SearchCriteria c, Pageable pageable) {
    StringBuilder where = new StringBuilder(" WHERE 1=1 ");
    Args args = new Args();

    if (c.q() != null && !c.q().isBlank()) {
        where.append(" AND p.search_tsv @@ plainto_tsquery('turkish', unaccent(:q)) ");
        args.put("q", c.q().trim());
    }
    // ... categoryIds, minPrice, maxPrice, minRating, inStockOnly
    
    String pageSql = "SELECT p.* FROM products p " + where + orderBy(c)
                   + " LIMIT :limit OFFSET :offset";
    // ... query.setParameter(...)
}
```

Niye native SQL: `tsvector @@ plainto_tsquery` JPQL'de yok. Dynamic 6-filter predicate
StringBuilder ile sade. Detay: [`docs/search.md`](../search.md#5-filter-pipeline--native-sql-with-dynamic-predicate).

### Sort Options

```java
private String orderClause(SearchCriteria c) {
    SearchSort sort = c.sort() == null ? SearchSort.RELEVANCE : c.sort();
    return switch (sort) {
        case RELEVANCE -> (c.q() != null && !c.q().isBlank())
                ? " ORDER BY ts_rank(p.search_tsv, plainto_tsquery('turkish', unaccent(:q))) DESC, p.rating_count DESC "
                : " ORDER BY p.rating_count DESC, p.created_at DESC ";
        case PRICE_ASC  -> " ORDER BY p.price ASC, p.id ASC ";
        case PRICE_DESC -> " ORDER BY p.price DESC, p.id DESC ";
        case RATING     -> " ORDER BY p.rating_average DESC, p.rating_count DESC ";
        case NEWEST     -> " ORDER BY p.created_at DESC, p.id DESC ";
    };
}
```

Tie-break columns (`p.id ASC` after `p.price ASC`): aynı fiyatlı ürünler **deterministic
sırada** gelsin → pagination tutarlı (sayfa 2'de aynı ürün tekrar görünmesin, sayfa 1'deki
yer almayan görünsün).

### Faceted Search

`/api/products/facets` aynı filter param'larını alır, kategoriye göre **count** verir, mevcut
result set'in price min/max'ını döner. Kategori facet'lerinde `categoryIds` filter **ignore
edilir** — kullanıcı "switch category" preview'i görsün diye.

Detay: [`docs/search.md`](../search.md#6-faceted-search--counts-in-sidebar).

---

## 4. Reviews

### Schema

```sql
-- V4__reviews.sql
CREATE TABLE reviews (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id),
    user_id BIGINT NOT NULL,
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    body TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (product_id, user_id)
);
```

`UNIQUE(product_id, user_id)` — bir kullanıcı bir ürüne **bir** review yazabilir. İkincisi
update olur (upsert pattern).

### `ReviewService.upsert`

```java
@Transactional
public ReviewDto upsert(Long productId, Long userId, ReviewRequest req) {
    Review existing = reviewRepository.findByProductIdAndUserId(productId, userId).orElse(null);
    if (existing != null) {
        existing.setRating(req.rating());
        existing.setBody(req.body());
        existing.setUpdatedAt(Instant.now());
    } else {
        Review newReview = Review.builder()
                .productId(productId).userId(userId)
                .rating(req.rating()).body(req.body())
                .build();
        reviewRepository.save(newReview);
    }
    recomputeAggregate(productId);   // ← side effect
    return mapper.toDto(...);
}

private void recomputeAggregate(Long productId) {
    AvgCount agg = reviewRepository.computeAggregate(productId);
    Product p = productRepository.findById(productId).orElseThrow();
    p.setRatingAverage(agg.avg() == null ? BigDecimal.ZERO : agg.avg().setScale(2, HALF_UP));
    p.setRatingCount(agg.count() == null ? 0 : agg.count().intValue());
}

@CacheEvict(cacheNames = {"products:byId", "products:bySlug"}, allEntries = true)
public ReviewDto upsert(...) { ... }
```

### Niye Aggregate'leri Denormalize Et

Alternatif: ürün listede review'leri JOIN + AVG yap. Sorun:
- 12 ürünlü sayfa için 12 ayrı subquery veya 1 büyük JOIN.
- Liste endpoint'i her hit'te yavaş.
- `rating_average DESC` order by için index kurmak zor.

Çözüm: `products.rating_average` ve `rating_count` denormalize. Review yazıldığında
`recomputeAggregate` çağrılır. Race koşulunda (iki user aynı anda review yazıyor):
- Her transaction kendi `recomputeAggregate`'ini yapar.
- Son commit eden DB-side değer doğru kalır (PostgreSQL row lock).
- En kötü ihtimal: 1 dakikalık tutarsızlık varsa zaten cache TTL'inde.

Eventual consistency kabul.

### `@CacheEvict` 

Review write'da `products:byId` ve `products:bySlug` cache'leri patlatılır → ürün detay
sayfasının "rating" alanı güncel görünür.

---

## 5. Recommendations

`RecommendationService.recommendFor(seedId)`:

1. SQL aday seti: order-service `/internal/co-purchases?productId=X` + same-category fallback.
2. Groq `llama-3.1-8b-instant` ile re-rank + Türkçe "neden" cümlesi.
3. Redis 5dk cache.

Detaylı pipeline: [`docs/recommendations.md`](../recommendations.md).

### `CoPurchaseClient` — Defensive

```java
public List<CoPurchase> topCoPurchasesFor(Long productId, int limit) {
    try {
        return client.get()
                .uri(uri -> uri.path("/internal/co-purchases").queryParam("productId", productId).build())
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    log.warn("co-purchase returned {} for productId={}", res.getStatusCode(), productId);
                })
                .body(new ParameterizedTypeReference<List<CoPurchase>>() {});
    } catch (Exception ex) {
        log.warn("co-purchase fetch failed for productId={}: {}", productId, ex.getMessage());
        return List.of();
    }
}
```

Her hata **yutulur** — ürün detay sayfası recommendation yüzünden break olmaz.

### `GroqRecommendationClient` — `@ConditionalOnProperty`

```java
@Component
@ConditionalOnProperty(prefix = "n11.recommendations.groq", name = "api-key", matchIfMissing = false)
public class GroqRecommendationClient { ... }
```

`GROQ_API_KEY` boş → bean inşa edilmez → `RecommendationService` `ObjectProvider.getIfAvailable()`
ile null check yapar → reasons empty Map → SQL sırası ile çalışır.

---

## 6. Cache — Redis

| Cache | TTL | Eviction Trigger |
|---|---|---|
| `categories` | 1h | Admin category CRUD |
| `products:byId` | 5m | Admin product write OR review write |
| `products:bySlug` | 5m | Admin product write OR review write |
| `products:autocomplete` | 1m | Admin product write |
| `recommendations` | 5m | Admin product write (allEntries) |

`CacheConfig`:
```java
Map<String, RedisCacheConfiguration> perCache = new LinkedHashMap<>();
perCache.put("categories",            base.entryTtl(Duration.ofHours(1)));
perCache.put("products:byId",         base.entryTtl(Duration.ofMinutes(5)));
perCache.put("products:bySlug",       base.entryTtl(Duration.ofMinutes(5)));
perCache.put("products:autocomplete", base.entryTtl(Duration.ofMinutes(1)));
perCache.put("recommendations",       base.entryTtl(Duration.ofMinutes(5)));
```

Polymorphic type validator + JSON serialization — detay [`docs/caching.md`](../caching.md).

---

## 7. Admin Metrics

`GET /api/products/admin/metrics?lowStockThreshold=10`:

```java
@RestController
@RequestMapping("/api/products/admin/metrics")
@PreAuthorize("hasRole('ADMIN')")
public class ProductMetricsController {
    @GetMapping
    @Transactional(readOnly = true)
    public ProductMetricsDto metrics(@RequestParam(defaultValue = "10") int lowStockThreshold) {
        // total products
        // low stock count + top 12 list
        // top 8 categories by product count
        return new ProductMetricsDto(total, lowCount, threshold, lowStock, topCategories);
    }
}
```

Bütün native query, bounded LIMIT'ler ile (12 low stock, 8 top categories).

### SecurityConfig Pattern Order Trap

```java
.requestMatchers(HttpMethod.GET, "/api/products/admin/**").authenticated()  // ← önce
.requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**").permitAll()
```

`/api/products/admin/**` matcher'ı `/api/products/**` permitAll'dan **önce** olmalı; yoksa
admin endpoint'ler de public olur. Order-sensitive Spring Security pattern matching.

---

## 8. Low-Stock Scanner — Scheduled

`LowStockScanner`:

```java
@Component
@ConditionalOnProperty(prefix = "n11.inventory.low-stock", name = "enabled", havingValue = "true")
@RequiredArgsConstructor @Slf4j
public class LowStockScanner {
    private final InventoryProperties properties;
    private final RabbitTemplate rabbitTemplate;
    private final EntityManager em;

    @Scheduled(cron = "${n11.inventory.low-stock.cron:0 0 9 * * *}")
    @Transactional(readOnly = true)
    public void scanAndPublish() {
        List<Object[]> rows = em.createNativeQuery("""
                SELECT id, name, slug, stock FROM products
                 WHERE stock <= :threshold ORDER BY stock ASC, id ASC LIMIT :limit
                """)
                .setParameter("threshold", properties.threshold())
                .setParameter("limit", properties.maxItemsPerReport())
                .getResultList();
        if (rows.isEmpty()) return;        // hiç yoksa mail atma
        
        List<LowStockReportEvent.Item> items = rows.stream()
                .map(r -> new LowStockReportEvent.Item(...))
                .toList();
        rabbitTemplate.convertAndSend(SagaTopology.EXCHANGE,
                SagaTopology.RoutingKey.LOW_STOCK_REPORT,
                LowStockReportEvent.of(properties.threshold(), items));
    }
}
```

Niye `@ConditionalOnProperty`: scanner default'ta **kapalı**. Yeni clone fresh compose
up'ta bir saatlik mail flood'u olmasın. `LOW_STOCK_ALERTS_ENABLED=true` ile aktive edilir.

Niye günlük mail değil "hiçbir şey yoksa": "her şey yolunda" daily email cluttering. Sadece
**aksiyon gerektiren** sinyal mail.

Detay (consumer tarafı): [`docs/services/notification-service.md`](notification-service.md).

---

## 9. Admin CRUD Implementation Notları

### Cache Eviction Geniş

```java
@Caching(evict = {
    @CacheEvict(cacheNames = "products:bySlug",       allEntries = true),
    @CacheEvict(cacheNames = "products:byId",         allEntries = true),
    @CacheEvict(cacheNames = "products:autocomplete", allEntries = true),
    @CacheEvict(cacheNames = "recommendations",      allEntries = true),
})
public ProductDetailDto update(Long id, ProductWriteRequest req) { ... }
```

Niye `allEntries=true` her cache için: granular invalidation reverse-index gerekir
(recommendations namespace'inde bu ürünü hangi seed'lerin reference ettiğini bilmek için).
5dk TTL ile zaten otomatik refresh; admin write'da geniş evict ek temizlik.

### Slug Uniqueness — TX Içi Re-check

```java
public ProductDetailDto update(Long id, ProductWriteRequest req) {
    Product p = productRepository.findById(id).orElseThrow(...);
    if (!p.getSlug().equals(req.slug())) {
        Optional<Product> other = productRepository.findBySlug(req.slug());
        if (other.isPresent() && !other.get().getId().equals(id)) {
            throw new ResponseStatusException(CONFLICT, "Slug zaten kullanılıyor: " + req.slug());
        }
    }
    // ... update fields
}
```

`!p.getSlug().equals(req.slug())` short-circuit: aynı slug ile update'te DB query yapma
(false-positive engelle). Slug değiştiyse: başkası kullanıyor mu (kendisi hariç) check.

Trade-off: race koşulunda iki paralel update → ikisi de check'i geçer → ikinci save UNIQUE
constraint violation alır. App-side check şu an yetiyor; daha sıkı için pessimistic lock
veya retry-on-conflict.

### Category Delete: FK RESTRICT'i 409'a Çevir

```java
@Transactional
public void delete(Long id) {
    Category c = categoryRepository.findById(id).orElseThrow(...);
    long inUse = productRepository.countByCategoryId(id);
    if (inUse > 0) {
        throw new ResponseStatusException(CONFLICT,
                "Bu kategoride " + inUse + " ürün var, önce ürünleri başka kategoriye taşı.");
    }
    categoryRepository.delete(c);
}
```

DB-side `ON DELETE RESTRICT` zaten reddederdi ama generic `SQLException` user-friendly
değil. App-side count + custom 409 mesajı admin'e net rehber verir.

---

## 10. RabbitMQ — Sadece Publisher

product-service **consumer değil** — sadece `LowStockReportEvent` publish eder. Bu yüzden:
- `RabbitConfig` queue declare etmez, sadece `TopicExchange` + `RabbitTemplate`.
- `@RabbitListener` yok.
- Compose'da `rabbitmq.condition: service_healthy` depend etse de listener pool yok.

```java
@Configuration
public class InventoryRabbitConfig {
    @Bean public TopicExchange sagaExchange() { return new TopicExchange(SagaTopology.EXCHANGE, true, false); }
    @Bean public MessageConverter jsonMessageConverter() { return new Jackson2JsonMessageConverter(); }
    @Bean public RabbitTemplate rabbitTemplate(...) { ... }
}
```

---

## 11. Bilinçli Olarak Yapmadıklarımız

- **Image upload**: Stock photo URL field var, upload endpoint yok. RAM bütçesi + S3/MinIO
  ek container kompleksitesi nedeniyle ertelendi. Seed data'da gerçek CDN URL'leri yeterli.
- **Stock reservation timeout**: Cart'a ekleyip 30dk bırakırsa stok bloke olmaz. Race koşulunda
  iki user son ürünü cart'a ekler — checkout'ta biri kaybeder. Sipariş volume'umuzda risk yok.
- **Variants (size, color)**: Tek-variant ürün modeli. Multi-variant için `product_variants`
  tablosu + cart variant_id field'ı gerekir.
- **Inventory cross-warehouse**: Tek depo varsayımı.
- **Ürün versioning**: Fiyat geçmişi tutmuyoruz.

---

## 12. Klasör Yapısı

```
backend/product-service/
├── pom.xml
└── src/main/java/com/n11/product/
    ├── ProductApplication.java       # @EnableScheduling, @ConfigurationPropertiesScan
    ├── api/
    │   ├── ProductController.java    # browse + admin CRUD
    │   ├── CategoryController.java   # list + admin CRUD
    │   ├── ReviewController.java
    │   ├── GlobalExceptionHandler.java
    │   ├── admin/
    │   │   ├── ProductMetricsController.java
    │   │   └── ProductMetricsDto.java
    │   └── dto/                      # Summary, Detail, Write, Search, Facets
    ├── config/
    │   ├── SecurityConfig.java
    │   ├── CacheConfig.java          # Redis topology
    │   ├── OpenApiConfig.java
    │   └── JwtProperties.java
    ├── domain/
    │   ├── Product.java
    │   ├── Category.java
    │   └── Review.java
    ├── repository/
    │   ├── ProductRepository.java
    │   ├── ProductSearchRepository.java   # native SQL FTS + facets
    │   ├── CategoryRepository.java
    │   └── ReviewRepository.java
    ├── service/
    │   ├── ProductQueryService.java
    │   ├── ProductAdminService.java
    │   └── ReviewService.java
    ├── recommendation/
    │   ├── RecommendationService.java
    │   ├── RecommendationController.java
    │   ├── CoPurchaseClient.java
    │   ├── GroqRecommendationClient.java
    │   ├── RecommendationProperties.java
    │   └── RecommendedItemDto.java
    └── inventory/
        ├── LowStockScanner.java         # @Scheduled
        ├── InventoryRabbitConfig.java
        └── InventoryProperties.java
```

---

## İlgili Dokümanlar

- [`docs/search.md`](../search.md) — FTS + facets detayı
- [`docs/recommendations.md`](../recommendations.md) — AI öneri pipeline
- [`docs/caching.md`](../caching.md) — Redis namespace + invalidation
- [`docs/services/order-service.md`](order-service.md) — Co-purchase consumer + admin metrics
