# Recommendations — Co-Purchase + Groq Re-Rank

**Bu doküman:** Ürün detay sayfasındaki "Sana özel öneriler" şeridi nasıl çalışıyor.
Hibrit yaklaşım: **SQL aday seti** + **LLM re-rank + açıklama**.

---

## 1. Niye Hibrit?

İki uç yaklaşımın trade-off'ı:

| Yaklaşım | Avantaj | Dezavantaj |
|---|---|---|
| **Saf SQL** (co-purchase / category) | Bedava, hızlı, deterministic | "Neden önerildi" cümlesi yok, generic |
| **Saf LLM** (her ürünü prompt'a sıkıştır) | Kişiselleştirme zengin | Token maliyeti yüksek, scale'lemez |
| **Hibrit** (bizim) | LLM sadece re-rank + açıklama | Bir LLM call + bir SQL aday |

Bizim hibrit:
1. **SQL aday seti**: 12 ürün — co-purchase + category fallback
2. **LLM re-rank**: 12 → 5 + her biri için Türkçe "neden ilgini çekebilir" cümlesi
3. **Cache 5dk**: Aynı seed için tek LLM call / 5dk

Free tier rate limit (Groq llama-3.1-8b-instant: 30 req/dk, 6000 TPM) bizim için fazlasıyla
yeterli — popüler bir ürün sayfasında bile cache miss/yapı dakikada bir.

---

## 2. Aday Seti — `RecommendationService.collectCandidates`

### Sinyal #1: Co-Purchase

`order_items` tablosunda **bu ürünle** aynı sepette satılan diğer ürünleri bul:

```java
// OrderRepository
@Query("""
    select co.productId, co.productName, count(co) as cnt
    from OrderItem self
    join self.order o
    join o.items co
    where self.productId = :productId
      and co.productId <> :productId
      and o.status <> com.n11.order.domain.OrderStatus.CANCELLED
      and o.createdAt >= :since
    group by co.productId, co.productName
    order by cnt desc
    """)
List<Object[]> findCoPurchaseCandidates(...);
```

Filter detayları:
- `co.productId <> :productId`: kendisi-kendine match'i çıkar.
- `status <> CANCELLED`: iptal edilmiş siparişler co-purchase sinyali değil.
- `createdAt >= :since` (90 gün): trend bayatlamasın. Yıllık satışlar bugünün önerilerini
  bozmasın.

### Niye order-service'te endpoint, niye direkt DB query değil

product-service'in productdb'si var, order-service'in orderdb'si. **Cross-DB JOIN yok**
(per-service-DB kuralı). Çözüm: order-service `/internal/co-purchases?productId=X&limit=10`
endpoint'i sunar:

```java
@RestController
@RequestMapping("/internal/co-purchases")
public class InternalController {
    @GetMapping
    public List<CoPurchaseDto> coPurchases(@RequestParam Long productId,
                                           @RequestParam(defaultValue = "10") int limit) {
        Instant since = Instant.now().minus(90, DAYS);
        return repository.findCoPurchaseCandidates(productId, since, PageRequest.of(0, limit))
                .stream().map(...).toList();
    }
}
```

product-service `CoPurchaseClient` ile çağırır:
```java
client.get()
    .uri(uri -> uri.path("/internal/co-purchases").queryParam("productId", id).build())
    .retrieve()
    .body(new ParameterizedTypeReference<List<CoPurchase>>() {});
```

### Niye `/internal/`?

API gateway `/api/orders/**`'i public route'lar. `/internal/**` route etmez → cluster içinde
sadece (`http://order-service:8084/internal/...`) erişilebilir. Network izolasyonu = trust
boundary.

SecurityConfig:
```java
.requestMatchers("/internal/**").permitAll()
```

Permitall mantıklı çünkü dış dünya zaten ulaşamaz, içerideki servisler güvenilir. Daha sıkı
istersen `X-Internal-Api-Key` shared secret eklenebilir; bootcamp scope'unda overkill.

### Sinyal #2: Category Fallback

Co-purchase yoksa veya 6'dan az adayda kalınmışsa (yeni ürün, az satış), aynı kategorinin
top-rated ürünlerini ekle:

```java
if (byId.size() < MIN_CANDIDATES) {
    for (Product p : productRepository.topRatedInCategory(
            seed.getCategory().getId(), seed.getId(), PageRequest.of(0, CANDIDATE_POOL))) {
        byId.putIfAbsent(p.getId(), p);  // co-purchase önceliği korunur
        if (byId.size() >= CANDIDATE_POOL) break;
    }
}
```

`putIfAbsent`: co-purchase'da varsa override etme (co-purchase = daha güçlü sinyal).
`LinkedHashMap`: insertion order korunur → co-purchase önce, category sonra.

### Sinyal #3 (yok): User history

Bu projede **session-aware** öneri yok. "Bu kullanıcı X kategorisini sıkça gezer" verisi
toplanmıyor. Catalog-side recommendation. User history eklemek isterse:
- Cart-service / order-service'ten kullanıcının geçmiş satın alımları çekilir.
- LLM prompt'una "kullanıcı genelde X türü ürünler alıyor" eklenir.
- GDPR concerns + UI explicit consent gerekir.

Scope dışı.

---

## 3. Groq Re-Rank — `GroqRecommendationClient`

### Prompt Yapısı

```text
Müşteri şu ürüne baktı:
- ID 5 | iPhone 15 | kategori: Telefon | fiyat: 49999.00 TRY

Aday ürünler:
- ID 12 | AirPods Pro 2 | kategori: Kulaklık | fiyat: 7999.00 TRY | rating: 4.6
- ID 8  | iPhone Şarj Cihazı | kategori: Aksesuar | fiyat: 599.00 TRY | rating: 4.4
- ID 23 | Anker Powerbank | kategori: Aksesuar | fiyat: 1299.00 TRY | rating: 4.7
- ...

Görev: Müşterinin baktığı ürünle en alakalı en fazla 5 adet adayı seç ve her biri için
TEK CÜMLELİK Türkçe "neden ilgini çekebilir" açıklaması yaz. Açıklamada ürün adını tekrar
etme, alakanın sebebini söyle (örn. "telefonunla uyumlu", "aynı stilde tamamlayıcı",
"daha güçlü versiyonu"). Yanıtı şu JSON şemasıyla ver:
{"items":[{"productId":<long>,"reason":"<cümle>"}]}
```

### Niye JSON Mode

```java
"response_format", Map.of("type", "json_object")
```

Groq (OpenAI-compatible) `response_format` parametresi destekler. JSON mode'da model
**garantili JSON output**'u verir — açıklama metni veya markdown çıkarmaz. Parsing safe.

JSON mode'sız: model bazen "İşte 5 öneri:" gibi natural language önek/sonek ekler. Parser
hata verir.

### System Prompt

```text
Türkçe konuşan ve Türk e-ticaret katalogunu iyi bilen bir öneri asistanısın. Yanıtın
MUTLAKA tek bir JSON object olmalı, başka açıklama yok.
```

Türkçe + role definition + format constraint. Kısa ve net — token tasarrufu, cevap
deterministic'e yaklaşır.

### Niye llama-3.1-8b-instant

Üç Groq model alternatifi:

| Model | Latency | Kalite | Maliyet |
|---|---|---|---|
| llama-3.3-70b-versatile | ~600ms | En yüksek | Free tier düşük rate limit |
| llama-3.1-8b-instant | ~100ms | İyi yeterli | Free tier yüksek rate limit (30/dk) |
| mixtral-8x7b | ~300ms | İyi | Geçiş aşamasında, deprecated yön |

Şeritte 5 öneri için 8b yeterli — basit pattern matching görevi. Latency düşük → user
deneyim akıcı.

### Temperature 0.4

```java
"temperature", 0.4
```

- 0.0 = deterministic, aynı input → aynı output her zaman.
- 1.0 = yaratıcı, çeşitli output.
- 0.4 = "neden" cümleleri biraz çeşitli olsun ama saçmalamasın.

Cache 5 dk, aynı seed → aynı output istiyoruz aslında. 0.0 daha mantıklı olabilir; 0.4
"neden ilgini çekebilir" cümlelerinin biraz farklı olmasına izin verir → user iki kez aynı
şeride bakarsa "aynı kelimeleri tekrarlanıyor" hissini azaltır.

---

## 4. Defensive Pipeline

```java
public List<RecommendedItemDto> recommendFor(Long seedId) {
    Product seed = productRepository.findById(seedId)
            .orElseThrow(() -> new EntityNotFoundException(...));
    
    Map<Long, Product> candidates = collectCandidates(seed);
    if (candidates.isEmpty()) return List.of();   // <-- early exit
    
    List<Product> ordered = new ArrayList<>(candidates.values());
    Map<Long, String> reasons = askGroqForReasons(seed, ordered);  // <-- best-effort
    
    // Order: Groq id'leri varsa o sırada, yoksa SQL sırasında
    Iterable<Long> orderedIds = reasons.isEmpty()
            ? ordered.stream().map(Product::getId).toList()
            : reasons.keySet();
    
    List<RecommendedItemDto> result = new ArrayList<>();
    for (Long id : orderedIds) {
        Product p = candidates.get(id);  // <-- hallucinated id varsa null → skip
        if (p == null) continue;
        result.add(new RecommendedItemDto(productMapper.toSummary(p), reasons.get(id)));
        if (result.size() >= RESULT_SIZE) break;
    }
    
    // Top up if Groq returned fewer than 5
    if (result.size() < RESULT_SIZE) {
        for (Product p : ordered) {
            if (result.stream().anyMatch(r -> r.product().id().equals(p.getId()))) continue;
            result.add(new RecommendedItemDto(productMapper.toSummary(p), null));
            if (result.size() >= RESULT_SIZE) break;
        }
    }
    return result;
}
```

### Defensive Layer'lar

1. **`candidates.isEmpty()` early exit**: SQL hiç bir aday üretmediyse (yeni catalog, yeni
   ürün, izole kategori) — boş liste dön. UI tarafı boş listede strip'i hide eder.

2. **Groq başarısız → reasons empty**: `GroqRecommendationClient.rerank` hata yutar (try/catch),
   boş liste döner. SQL sırası kullanılır, "reason" field null gider, UI onu "neden" pill'siz
   gösterir.

3. **Hallucinated ID protection**: Groq bazen prompt'ta olmayan bir ID uydurur ("ID 99 |
   iPhone..."). `candidates.get(id)` null döner → o öneri **drop**. UI sadece valid ürünleri
   görür, kullanıcı tıklayınca 404 olmaz.

4. **Top-up**: Groq sadece 3 ürün döndüyse (örn. düşünce limit'e takıldı), kalan 2 slot SQL
   sırasından doldurulur — şerit hep 5 kart gösterir.

### Conditional Bean — `@ConditionalOnProperty`

```java
@Component
@ConditionalOnProperty(prefix = "n11.recommendations.groq", name = "api-key", matchIfMissing = false)
public class GroqRecommendationClient { ... }
```

`GROQ_API_KEY` boşsa → bean inşa edilmez → `RecommendationService.askGroqForReasons` içindeki
`groqProvider.getIfAvailable()` null döner → reasons empty Map → SQL sırası ile şerit yine
çalışır, sadece "neden" cümleleri olmaz.

Bu pattern her optional integration için (Iyzico, OAuth providers, OpenAI vb.) tutarlı.

---

## 5. Cache — `recommendations` Namespace

```java
@Cacheable(value = "recommendations", key = "#seedId")
public List<RecommendedItemDto> recommendFor(Long seedId) { ... }
```

Redis cache, 5 dk TTL. Popular ürün:
- 1. visit: cache miss → SQL + Groq → 100-300ms total → cache populate.
- 2-1000. visit (5dk içinde): cache hit → ~1ms.
- 5dk sonra: ilk visit cache miss yenilenir.

Free tier 30 req/dk Groq budget'ında, 100 popular ürünün hepsinin cache hit ratio'su yüksek
olduğunda ~10 req/dk Groq trafiği. Çok rahat sınırların altı.

### Cache Invalidation

`ProductAdminService` her write'da:
```java
@CacheEvict(cacheNames = "recommendations", allEntries = true)
public ProductDetailDto update(...) { ... }
```

Niye `allEntries`: hangi seed'in cache'inde bu ürün var bilemeyiz. Granular invalidation reverse-index
gerektirir → karmaşık, getirisi düşük.

5dk TTL ile zaten sürekli refresh oluyor; admin write'da `allEntries` ek temizlik.

---

## 6. Frontend Tarafı — `RecommendationStrip`

```jsx
<motion.article
    key={product.id}
    initial={{ opacity: 0, y: 12 }}
    animate={{ opacity: 1, y: 0 }}
    transition={{ duration: 0.35, delay: idx * 0.06 }}>
    <Link to={`/products/${product.slug}`}>
        ...image, name, rating...
        {reason && (
            <motion.p
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: 'auto' }}
                transition={{ duration: 0.3, delay: 0.2 + idx * 0.06 }}>
                <Sparkles size={12} className="text-fuchsia-500" />
                {reason}
            </motion.p>
        )}
    </Link>
</motion.article>
```

- Stagger delay (60ms) → kartlar sırayla "kayar gelir".
- AI badge kart yüklendikten 200ms sonra height-animation ile açılır → bilinçli "ek bilgi"
  hissi.
- `reason` null ise badge yok, kart sadece ürün cardını gösterir.

Defensive: API hata verirse boş liste, strip kaybolur. Page asla recommendation yüzünden
break olmaz.

---

## 7. Bilinçli Olarak Yapmadıklarımız

- **Embeddings + pgvector**: Semantic similarity (text-embedding ile cosine distance)
  daha "akıllı" bir sinyal verir ama: (1) Groq'ta embedding model yok, ayrı sağlayıcı + maliyet,
  (2) bizim catalog ölçeği için co-purchase + category yeterli sinyal.
- **A/B testing**: Hangi öneri stratejisi click-through rate'i artırır — telemetry yok.
- **User-personalized**: Önce login user için "bu kullanıcı X tipini sevmiş" sinyali — geçmiş
  satın alımdan çıkar. Privacy + scope dışı.
- **Real-time signal**: Şu anki cart içindeki ürünlere göre "bunlarla uyumlu" — checkout
  öncesi up-sell. Future feature.
- **Scoring transparency**: User "neden bu öneri?" diye sorabilir. Şu an "neden ilgini
  çekebilir" cümlesi var ama LLM'in halüsinasyonu olabilir, deterministic değil. Production-grade
  öneri sistemi explainable scoring ister.

---

## İlgili Dokümanlar

- [`docs/services/product-service.md`](services/product-service.md) — RecommendationService implementasyonu
- [`docs/services/order-service.md`](services/order-service.md) — Co-purchase repository sorgusu
- [`docs/services/chatbot-service.md`](services/chatbot-service.md) — Aynı Groq pattern'i farklı use-case
- [`docs/caching.md`](caching.md) — Cache invalidation stratejisi
