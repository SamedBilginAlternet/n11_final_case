# Caching — Redis Topology + TTL Strategy

**Bu doküman:** Redis nerede kullanılıyor, hangi cache name'i hangi TTL ile, niye bu sınırlar
seçildi, eviction stratejileri.

---

## 1. Niye Redis?

İki kullanım case'i:

| Case | Redis kullanımı | Niye Redis |
|---|---|---|
| **Read-mostly** ürün/kategori sorguları | Spring Cache `@Cacheable` | Mikrosaniyelerde GET, p99 stable |
| **Counter coordination** (coupon redemptions) | Spring Cache `@CacheEvict` ile sync | Tek source-of-truth (ama atomik DB UPDATE asıl race-safe) |

Reddedilen alternatifler:

- **In-memory Caffeine**: Servis başına ayrı cache → stale data'ya yol açar (Bob node 1'den okur,
  Alice node 2'den eski snapshot görür). Multi-replica deploy düşünüldüğünde Redis eşit.
- **Memcached**: TTL var ama veri tipi yok (sadece string). Redis'in `HASH`, `SET`, `INCR`
  primitives'i şu anda kullanılmıyor ama gelecekte (rate limiting, leaderboard) açık kapı.

Redis tek-node yeterli — bizim cache hit oranımız %95+ ve cache loss = DB'den re-fetch (yavaş
ama doğru). Cluster + Sentinel HA bizim ölçeğimizde overkill.

---

## 2. Cache Topology — Cache Name'leri

### product-service

```java
// backend/product-service/src/main/java/com/n11/product/config/CacheConfig.java
Map<String, RedisCacheConfiguration> perCache = new LinkedHashMap<>();
perCache.put("categories",            base.entryTtl(Duration.ofHours(1)));
perCache.put("products:byId",         base.entryTtl(Duration.ofMinutes(5)));
perCache.put("products:bySlug",       base.entryTtl(Duration.ofMinutes(5)));
perCache.put("products:autocomplete", base.entryTtl(Duration.ofMinutes(1)));
perCache.put("recommendations",       base.entryTtl(Duration.ofMinutes(5)));
```

| Cache name | TTL | Kullanım | Eviction |
|---|---|---|---|
| `categories` | 1h | `GET /api/categories` | Admin CRUD'da `@CacheEvict allEntries` |
| `products:byId` | 5m | `GET /api/products/{id}` | Admin write'da `allEntries` |
| `products:bySlug` | 5m | `GET /api/products/slug/{slug}` | Admin write'da `allEntries` |
| `products:autocomplete` | 1m | `GET /api/products/autocomplete?q=...` | Sadece TTL |
| `recommendations` | 5m | `GET /api/products/{id}/recommendations` | Admin write'da `allEntries` (ürün değişince öneri güncelleyebilsin) |

### cart-service

```java
@Cacheable(cacheNames = "coupons:byCode",
           key = "#code.toUpperCase()",
           unless = "#result == null or !#result.isPresent()")
Optional<Coupon> findByCodeIgnoreCase(String code);
```

| Cache name | TTL | Niye |
|---|---|---|
| `coupons:byCode` | 5m (default) | Aynı sepet kupon'u 5dk içinde tekrar tekrar query'lenir |

`unless` predicate'i: empty Optional cache'lenmiyor — saldırgan random kod denese cache'i
şişiremez.

### Niye TTL'ler bu sayılar?

**1 saat (categories)**: Admin nadiren kategori ekler/değiştirir. 1 saat sonra cache miss
acceptable, 1 saat içinde her ekleme/değiştirme `@CacheEvict allEntries` ile zaten patlar.

**5 dk (products byId/bySlug, recommendations)**: Fiyat ve stok güncellenir. 5dk gecikme
demek müşteri 5dk eski fiyatı görebilir → kabul edilebilir (ürün detay'a girip "ekle"ye
basana kadar zaten yenilemiyoruz). Daha uzun = stok-out riskini büyütür.

**1 dk (autocomplete)**: Klavye başında her tuşta API hit. 1dk window'da aynı kullanıcı çok
kez aynı prefix'i yazar. Daha uzun = yeni eklenen ürün autocomplete'te 5dk görünmez.

---

## 3. Eviction Patterns

### Yazma sırasında geniş evict

Admin bir ürün güncelliyor — hangi cache'leri etkiler?
- `products:byId` → o ürünü cache'lemiş olabilir
- `products:bySlug` → slug değişmediyse aynı item bayatlamış
- `products:autocomplete` → ürün adı autocomplete sonuçlarında olabilir
- `recommendations` → seed başka bir ürün için cache'lenmiş öneri listesi içinde olabilir

```java
@Caching(evict = {
    @CacheEvict(cacheNames = "products:bySlug",      allEntries = true),
    @CacheEvict(cacheNames = "products:byId",        allEntries = true),
    @CacheEvict(cacheNames = "products:autocomplete", allEntries = true),
    @CacheEvict(cacheNames = "recommendations",      allEntries = true),
})
public ProductDetailDto update(Long id, ProductWriteRequest req) { ... }
```

**Niye `allEntries = true`?** Surgical evict zor:
- `recommendations` cache'i seed productId ile key'lenir, ama her cached value içinde **başka
  ürünler** referans edilir. Ürün X güncellenince hangi seed'lerin cache'ini patlatmalıyım?
  Bilmek için reverse-index gerek — kompleks.
- `autocomplete` her prefix için ayrı entry. Ürün adı 5 karakterse 5 farklı prefix entry'si
  olabilir. Tek-tek silmek pratik değil.

`allEntries=true` her admin write'da N entry siler. Read trafiği 5dk içinde re-populate eder.
Cache miss "ucuz" — DB hit'i 5-15ms.

### Surgical evict — `coupons:byCode`

Coupon save sırasında sadece o kodu evict ediyoruz:

```java
@Modifying
@CacheEvict(cacheNames = "coupons:byCode", key = "#code.toUpperCase()")
@Query("UPDATE Coupon c SET c.redemptions = c.redemptions + 1 ...")
int reserveOne(@Param("code") String code);
```

Niye burada surgical mümkün: her coupon kodunun **kendi entry**'si var, dependent cache yok.

---

## 4. Polymorphic Type Validator

```java
// CacheConfig.java
private GenericJackson2JsonRedisSerializer jsonSerializer() {
    ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .activateDefaultTyping(
                    BasicPolymorphicTypeValidator.builder()
                            .allowIfBaseType(Object.class)
                            .allowIfSubType("com.n11.product.")
                            .allowIfSubType("java.util.")
                            .allowIfSubType("java.time.")
                            .allowIfSubType("java.math.")
                            .build(),
                    ObjectMapper.DefaultTyping.NON_FINAL);
    return new GenericJackson2JsonRedisSerializer(mapper);
}
```

### Niye PolymorphicTypeValidator?

Jackson default-typing açık olduğunda payload `@class` field'ı içerir → deserialize sırasında
o class'ı yükler. Saldırgan `"@class":"com.evil.GadgetClass"` gönderirse → arbitrary class
loading → RCE.

Bu **classic Jackson deserialization gadget** problemidir. Bizimki cache içindeki JSON ama
prensip aynı.

`allowIfSubType` whitelist:
- `com.n11.product.*` — kendi DTO'larımız.
- `java.util.*` — `ArrayList`, `HashMap` gibi gerekli.
- `java.time.*` — `Instant`, `LocalDate`.
- `java.math.*` — `BigDecimal`.

Diğer her şey reddedilir → gadget class load edilemez.

### Default Typing Niye Açık?

Polymorphism: `List<RecommendedItemDto>` cache'lenir; deserialize sırasında ham `LinkedHashMap`
mi yoksa `ArrayList<RecommendedItemDto>` mi olduğunu Jackson **bilemez** — type info olmadan.
Default typing class meta'sını JSON'a yazar:

```json
["java.util.ArrayList", [
    ["com.n11.product.recommendation.RecommendedItemDto", {"product": {...}, "reason": "..."}]
]]
```

Validator olmadan bu **güvenlik açığı**. Validator ile **güvenli** generic-aware cache.

---

## 5. Cache Test Profili

Integration test'te Redis kaldırıyoruz — Testcontainers Redis spawn'lamak yavaş, gereksiz:

```yaml
# src/test/resources/application-it.yml
spring:
  cache:
    type: none
```

`CacheConfig`'in conditional'ı:

```java
@Configuration
@EnableCaching
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
public class CacheConfig { ... }
```

`spring.cache.type=none` → CacheConfig bean'lemez → `NoOpCacheManager` Spring'in default'u
devreye girer → `@Cacheable` no-op olur. Test'lerde **business logic** gerçek davranır,
cache yok demek "her query DB'ye gider", yine doğru sonuç alınır.

---

## 6. Cache Statistics

```yaml
spring:
  cache:
    redis:
      enable-statistics: true
```

Micrometer otomatik bind eder → `/actuator/metrics/cache.gets` endpoint:
- `cache.gets.hit` — hit sayısı
- `cache.gets.miss` — miss sayısı
- `cache.puts` — yazma sayısı

Production debug için: hit ratio %90 altına düşerse TTL ya da eviction stratejisini gözden
geçir.

---

## 7. Cache Schema Versioning — `CACHE_SCHEMA_VERSION`

**Problem:** Cache value shape değiştiğinde (DTO field eklendi/çıktı, Redis serializer
yeniden ayarlandı, Coupon entity'sine yeni field geldi) eski Redis entry'leri **poisoned**
olur — yeni kod onları deserialize edemez ve runtime exception fırlar. Klasik "deploy edince
cart-service patladı" senaryosu.

**Üç olası çözüm + tercihimiz:**

| Strateji | Avantaj | Dezavantaj | Tercih |
|---|---|---|---|
| Her deploy `FLUSHALL` | Basit, deterministik | Her deploy = cold cache → DB'ye burst (cache stampede riski). Aynı zamanda en küçük commit'ler için bile aşırı | ❌ |
| TTL'lerin kendiliğinden expire'ı | Sıfır iş | Bug süresi = en uzun TTL (1 saata kadar). Kullanıcı bug görür | ❌ |
| **Versiyonlu key prefix** | Cold cache yok, sadece etkilenen cache repopulate. Explicit kontrol | Disiplin: schema değişince env'i bump'lamayı unutmamak | ✅ |

**Implementasyon (`*Service/.../config/CacheConfig.java`):**

```java
@Value("${n11.cache.schema-version:1}")
private String schemaVersion;

private RedisCacheConfiguration baseConfig(Duration defaultTtl) {
    String prefix = "product:v" + schemaVersion + ":";
    return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(defaultTtl)
            .computePrefixWith(cacheName -> prefix + cacheName + "::")
            ...
}
```

**Key shape:** `<service>:v<N>:<cacheName>::<key>`
Örnek: `product:v1:products:bySlug::pamuk-nevresim`, `cart:v1:coupons:byCode::KUPON100`

**Bump checklist** — bu durumlarda `.env`'de `CACHE_SCHEMA_VERSION` artır:

- ✅ Cache'lenen DTO/entity'ye field eklendi/çıkarıldı
- ✅ Redis serializer config değişti (Jackson `DefaultTyping`, `PolymorphicTypeValidator`,
  module list, vs.)
- ✅ Cache key formatı değişti (örn: SpEL `key=` ifadesi)
- ✅ Bir cache name silindi/yeniden adlandırıldı
- ❌ Sadece TTL değişti — eski entry'ler de yeni TTL'i alacak doğal yoldan
- ❌ Sadece kod refactor (DTO shape aynı kaldı)
- ❌ Yeni cache name eklendi — yeni prefix zaten otomatik

**Bump sonrası:** Eski entry'ler Redis'te kalır ama hiçbir kod onları okumaz, doğal
yoldan TTL ile evict olurlar. Manuel `FLUSHALL` veya `KEYS pattern | DEL` gerekmez.

**Neden `n11.cache.schema-version` `application.yml`'de?** `spring.cache.redis.key-prefix`
property'si Spring Boot auto-config CacheManager için. Bizim custom `RedisCacheManager`
bean'imiz var, o property silently ignore ediliyor — her halükarda `CacheConfig.java` içinde
açıkça `computePrefixWith()` çağrısı yapmamız lazım. Dolayısıyla bizim kendi property'mizle
inject ediyoruz.

**Mülakatta:**
> "Cache schema versioning kullanıyorum — key prefix'te `v<N>` taşıyor, env değişkeniyle
> kontrol ediliyor. Cache value shape'ini etkileyen deploy'larda bumpluyorum, eski entry'ler
> orphan olarak TTL ile evict oluyor. Auto-FLUSHALL yapmıyorum çünkü her deploy'da cache
> stampede tetiklemenin maliyeti, schema bug'larının nadirliğine ters orantılı."

---

## 8. Bilinçli Olarak Yapmadıklarımız

- **Cache warmer yok**: Cold start'ta cache boş, ilk request'ler yavaş. Demo için OK; prod'da
  startup'ta `categories` ve top-50 product'u pre-load eden bir `@PostConstruct` job eklenebilir.
- **Distributed lock (Redisson) yok**: Tek-node Redis, race-safe sayaç DB tarafında. Lock
  gerektiren scenario yok şu an.
- **Cache stampede koruması yok**: Eğer 1000 paralel request aynı miss'e takılırsa hepsi DB
  query yapar. `synchronized=true` (Spring Cache property) bunu engeller ama performance
  trade-off var; volume bizim için bu kadar yüksek değil.
- **TTL jitter yok**: Tüm `recommendations` cache'leri 5dk = aynı anda expire olabilir →
  tüm seed'ler için Groq fırtınası. Volume düşükken kabul.

---

## İlgili Dokümanlar

- [`docs/services/product-service.md`](services/product-service.md) — Cache config detayı
- [`docs/services/cart-service.md`](services/cart-service.md) — `coupons:byCode` cache ve atomik UPDATE
- [`docs/recommendations.md`](recommendations.md) — `recommendations` cache'in oluşum süreci
