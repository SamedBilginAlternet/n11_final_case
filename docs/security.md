# Security — JWT, Roles, Rate Limiting

**Bu doküman:** Sistemin güvenlik kararları. Ne korunuyor, niye HS256 seçildi, refresh token
rotation niye var, role-based access nasıl çalışır, hangi saldırı vektörleri düşünüldü.

---

## 1. Tehdit Modeli

Senaryo: Public e-ticaret sitesi. Olası saldırgan tipleri:

| Tip | Saldırı | Bizim koruma |
|---|---|---|
| Random bot | Brute-force login, registration spam | Token bucket rate limit (`/login`, `/register`, `/refresh`) |
| Yaramaz user | Bob, Alice'in siparişini okumaya çalışır | Server-side userId scope (404 anti-enumeration) |
| Çalınmış JWT | Saldırgan token'ı yakaladı, kullanıyor | Kısa TTL (60dk) + refresh rotation + reuse detection |
| Çalınmış refresh token | Saldırgan refresh'i yakaladı | Reuse detection — eski token kullanılırsa **tüm aile revoke** |
| OWASP: SQL injection | Param manipülasyonu | JPA/PreparedStatement (string concat yok) |
| OWASP: XSS | Storefront'ta input → HTML escape edilmiyor | React varsayılanı escape eder; yine de içerik HTML'i serverda sanitize edilmiyor (admin-trusted) |
| Cross-tenant | Admin1 başka tenant'ın user'ını manipüle eder | Tek-tenant sistem; user-level isolation yeter |

Tehdit modeli **bootcamp scope'unda gerçekçi**. Production'da WAF, CSP, CSRF token (cookie-based
auth olsaydı), DAST tarama eklenir.

---

## 2. JWT — HS256, Servis Tarafında Doğrulama

### Token Issue

`auth-service.JwtTokenProvider.issue()`:

```java
public IssuedToken issue(User user) {
    Instant now = Instant.now();
    Instant exp = now.plus(accessTtlMinutes, ChronoUnit.MINUTES);
    String token = Jwts.builder()
            .subject(user.getId().toString())
            .issuer(issuer)             // "n11-auth"
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .claim("email", user.getEmail())
            .claim("role", user.getRole().name())   // "USER" or "ADMIN"
            .signWith(key)              // HS256
            .compact();
    return new IssuedToken(token, accessTtlMinutes * 60);
}
```

**Claim'ler**:
- `sub` = userId (long)
- `iss` = "n11-auth"
- `iat` / `exp` = Unix epoch
- `email` = login için
- `role` = `USER` veya `ADMIN`

### Niye HS256 (symmetric), niye RS256 (asymmetric) değil?

Kararı yöneten faktör: **bizim case'imizde tüm servisler aynı güveni paylaşıyor**.

| Konu | HS256 (symmetric, HMAC-SHA-256) | RS256 (asymmetric, RSA) |
|---|---|---|
| Key dağıtım | Tek `JWT_SECRET` env, tüm servislere ortak | Auth-service'te private key, diğerlerinde public key + JWKS endpoint |
| Doğrulama hızı | Çok hızlı (HMAC) | Yavaş (RSA verify) |
| Multi-tenant / public verification | Uygun değil | Uygun |
| Operasyon | Bir secret rotate edilince herkes restart | JWKS endpoint key cache invalidate eder |

Niye HS256 kazandı:
- Hepsi internal Spring servisi, aynı `JWT_SECRET` tüm pod'lara compose env ile geçiyor.
- Public-facing 3rd party token verifier yok.
- HMAC ~10x daha hızlı verify, microservice'te per-request verify maliyeti düşer.
- Operasyonel basitlik.

Niye RS256 reddedildi:
- JWKS endpoint + cache invalidation + key rotation prosedürü → ekstra karmaşıklık.
- 7 servisin **public-private trust split** ihtiyacı yok; hepsi aynı tarafta.
- Real-world'de federated SSO (Auth0, Cognito) varsa RS256 zorunlu — bizde değil.

**Trade-off**: `JWT_SECRET` çalınırsa her servisi restart edip key rotate etmek gerek. Tek deploy
job'ı olduğu için bu acılı değil.

### Niye gateway'de değil servislerde verify?

Gateway sadece **routing** yapar. JWT verification her servisin kendi sorumluluğu:

```java
// backend/common/src/main/java/com/n11/common/security/JwtAuthenticationFilter.java
@Override
protected void doFilterInternal(HttpServletRequest request, ...) {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
        String token = header.substring(7);
        ParsedToken parsed = jwtParser.parse(token);  // throws if invalid/expired
        AuthenticatedUser principal = new AuthenticatedUser(
                parsed.userId(), parsed.email(), parsed.role());
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + parsed.role())));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
    filterChain.doFilter(request, response);
}
```

Niye bu pattern:
- **Defense in depth**: gateway bypass olsaydı (intra-cluster doğrudan call), servis hala
  korunur.
- **Per-service authorization**: servis kendi endpoint'lerini bilir, hangi role gerektirir
  bilir. Gateway sadece "var/yok" check'i yapamaz.
- **Decoupling**: gateway logic'i ince — sadece path routing. JWT logic'i servislerde tek
  yerde (`common.JwtAuthenticationFilter`).

### Servis-tarafı JWT Filter Yerleşimi

Her servisin `SecurityConfig`'i:

```java
http
    .csrf(csrf -> csrf.disable())                          // stateless API
    .formLogin(f -> f.disable())                           // no HTML form
    .httpBasic(b -> b.disable())                           // no Basic auth
    .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))   // no session cookie
    .authorizeHttpRequests(...)
    .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);  // ← bizim filter
```

`UsernamePasswordAuthenticationFilter`'dan **önce** ekleniyor — Spring'in default form-login
filter'ından önce çalışıp authentication context'i set etmesi için.

---

## 3. Refresh Token Rotation + Reuse Detection

Access token kısa-ömürlü (60 dk) — çalınma penceresi minimum. Yenileme için **opaque refresh
token**. Niye opaque, JWT değil:

| Konu | JWT refresh | Opaque refresh (bizim) |
|---|---|---|
| Server-side state | Yok (stateless verify) | Var (`refresh_tokens` tablosu) |
| Revocation | Zor — revocation list gerek | Trivial — DB row sil/işaretle |
| Token boyutu | Büyük (claim'lerle 200+ byte) | 64 byte base64url |
| Reuse detection | İmkansız | Kolay — DB lookup |

JWT refresh **revocation imkansız** — herhangi bir saldırgan token'ı yakaladığında TTL
dolmadan iptal edemezsin. Opaque + DB ile her refresh'i takip ederiz.

### Storage Şeması

```sql
CREATE TABLE refresh_tokens (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    token_hash    CHAR(64) NOT NULL UNIQUE,       -- SHA-256 hex
    family_id     UUID NOT NULL,                  -- aynı login session
    expires_at    TIMESTAMPTZ NOT NULL,
    revoked_at    TIMESTAMPTZ,
    replaced_by_id BIGINT,                        -- rotation chain
    user_agent    TEXT,
    ip            VARCHAR(45),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_refresh_tokens_token_hash ON refresh_tokens (token_hash);
CREATE INDEX ix_refresh_tokens_family_id  ON refresh_tokens (family_id);
```

### Login → Refresh → Reuse Detection Akışı

```
LOGIN
  ├─ user/password validate
  ├─ access_token = JWT issue (60 dk)
  ├─ refresh_token_raw = secure-random 384-bit base64url
  ├─ family_id = UUID.randomUUID()
  ├─ INSERT (token_hash=SHA256(raw), family_id, user_id, ...)
  └─ return { accessToken, refreshToken: raw }

REFRESH (POST /api/auth/refresh, body: { refreshToken: raw })
  ├─ presented_hash = SHA256(raw)
  ├─ row = SELECT * FROM refresh_tokens WHERE token_hash = presented_hash
  │
  ├─ if row not found:
  │     return 401 "Invalid refresh token"
  │
  ├─ if row.revoked_at != null:
  │     ⚠️  REUSE DETECTED ⚠️
  │     UPDATE refresh_tokens SET revoked_at = now()
  │      WHERE family_id = row.family_id
  │     return 401 "Token reuse detected, family revoked"
  │
  ├─ if row.expires_at < now:
  │     return 401 "Expired"
  │
  ├─ ROTATE:
  │     new_raw = secure-random 384-bit
  │     INSERT new row (token_hash=SHA256(new_raw), family_id=row.family_id, ...)
  │     UPDATE old row SET revoked_at=now(), replaced_by_id=new_row.id
  └─ return { accessToken: new_jwt, refreshToken: new_raw }
```

### Niye reuse detection?

Senaryo:
- Saldırgan, kullanıcının **eski** refresh token'ını çaldı (örn. localStorage XSS).
- Saldırgan token'ı kullanır → success → yeni access + yeni refresh alır.
- Sonra **kullanıcı** uyandığında kendi refresh'ini kullanır (browser tekrar açıldı, axios
  interceptor 401 alır, /refresh dener).
- Kullanıcının token'ı **eski** — saldırgan zaten rotate ettiği için. Sistem "aynı eski
  token iki kez kullanıldı" tespit eder → **family**'i revoke eder, **saldırgan da kullanıcı da**
  başa döner (re-login zorunlu).

Bu **iyi** bir trade-off: legitimate user da etkilenir ama saldırgan da çıktığı için zorunlu
güvenlik. UX trade-off: paralel tabs, kötü network ortamlarında **false positive** ihtimali var.
Bunu hafifletmek için:

### Single-flight Refresh — Frontend Tarafı

```js
// frontend/src/api/client.js
let refreshPromise = null;

async function performRefresh() {
    if (refreshPromise) return refreshPromise;  // single-flight
    refreshPromise = refreshClient.post('/api/auth/refresh', { ... })
        .finally(() => { refreshPromise = null; });
    return refreshPromise;
}

api.interceptors.response.use(null, async (err) => {
    if (err.response?.status === 401 && !err.config._retry) {
        err.config._retry = true;
        await performRefresh();  // shared
        return api(err.config);  // retry
    }
    throw err;
});
```

5 paralel API çağrısı 401 alırsa **tek bir** /refresh isteği gider. Yoksa 5 paralel refresh
denemesi → ilki başarılı, sonraki 4'ü "eski token" diye reuse-detect tetikler → kendi
ailesini revoke eder. Single-flight bu false positive'i engeller.

### Token Hash, Plain Değil

`token_hash` SHA-256 hex (deterministic) — **plaintext token DB'de yok**. DB dump'ı çalınsa
saldırgan token'ları kullanamaz (reverse SHA imkansız). Hash deterministic çünkü lookup
yapacağız (`WHERE token_hash = ?`); bcrypt (random salt) işe yaramaz çünkü her insert farklı
hash üretir.

Hash trade-off: pre-image attack? Token'lar 384-bit secure-random; rainbow table veya brute
force gerçekçi değil.

### Family ID

Her login **yeni family_id** üretir (UUID). Bir kullanıcının iki cihazı varsa:
- Telefon login → family_id = X
- Laptop login → family_id = Y

Telefonun token'ı çalınırsa **family X revoke** olur, laptop (family Y) etkilenmez. Granular
revocation = per-device.

---

## 4. Role-Based Access — `@PreAuthorize` + Spring Security

Endpoint koruma:

```java
@RestController
@RequestMapping("/api/coupons")
@PreAuthorize("hasRole('ADMIN')")   // class-level, tüm methodlar
public class CouponAdminController {
    @PostMapping public CouponDto create(...) { ... }
    @PutMapping("/{id}") public CouponDto update(...) { ... }
    @DeleteMapping("/{id}") public void delete(...) { ... }
}
```

Veya method-level:

```java
@PreAuthorize("hasRole('ADMIN')")
@PostMapping("/{id}/processing")
public OrderDto markProcessing(@PathVariable Long id) { ... }
```

### `@EnableMethodSecurity` Şart

Önemli detay: `@PreAuthorize` **sessizce no-op** olur eğer `@EnableMethodSecurity` yoksa.
Dev'de fark etmezsin (admin user testi geçer), prod'a çıkar — herkes admin endpoint'leri
çağırır.

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // ← şart
public class SecurityConfig { ... }
```

Bu projedeki kural: **her servis SecurityConfig'i `@EnableMethodSecurity` ile** açılır.
Bug avı: bu session'da auth-service ve cart-service'te eksikti, fark edip eklendi.

### `hasRole('ADMIN')` vs `hasAuthority('ROLE_ADMIN')`

`JwtAuthenticationFilter` rol'ü `ROLE_<role>` prefix'i ile authority olarak kaydeder:

```java
new SimpleGrantedAuthority("ROLE_" + parsed.role())  // "ROLE_ADMIN"
```

Spring Security'nin `hasRole('ADMIN')` ifadesi otomatik `ROLE_` prefix'ini ekler — yani
yukarıdaki authority ile eşleşir. `hasAuthority('ROLE_ADMIN')` da aynı şey.

### URL Pattern Matchers — Defense in Depth

`SecurityConfig.authorizeHttpRequests`:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers(HttpMethod.GET, "/api/products/admin/**").authenticated()
    .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**").permitAll()
    .requestMatchers(HttpMethod.POST,   "/api/products").authenticated()
    .requestMatchers(HttpMethod.PUT,    "/api/products/*").authenticated()
    .requestMatchers(HttpMethod.DELETE, "/api/products/*").authenticated()
    ...
)
```

Niye HTTP-method bazlı authenticated() ve `@PreAuthorize` ikisi birden:

- **`.authenticated()`**: JWT yoksa **401**.
- **`@PreAuthorize("hasRole('ADMIN')")`**: JWT var ama role USER ise **403**.

Eğer sadece `@PreAuthorize` koysam, anonim user 403 alır (yanlış status). Sadece
`.authenticated()` koysam, USER role'ü admin endpoint'i çağırabilir. **İkisi birden** doğru
davranır:
- Anonim → 401 (interceptor wipe + redirect to /login)
- Authenticated USER → 403 (toast: "yetkin yok")
- Authenticated ADMIN → 200

---

## 5. Rate Limiting — Token Bucket

`/api/auth/login`, `/register`, `/refresh` brute-force hedef. Per-IP token bucket:

```java
// backend/common/src/main/java/com/n11/common/security/TokenBucketRateLimitFilter.java
public class TokenBucketRateLimitFilter extends OncePerRequestFilter {
    private final int capacity;        // 10
    private final int refillSeconds;   // 60
    // Cache<IP, Bucket> with 10-min eviction
    
    @Override
    protected void doFilterInternal(...) {
        if (!shouldApply(request)) { chain.doFilter(); return; }
        Bucket b = buckets.computeIfAbsent(clientIp(request), k -> new Bucket(capacity));
        if (!b.tryConsume(refillRate)) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            return;
        }
        chain.doFilter();
    }
}
```

Auth-service `SecurityConfig`:
```java
@Bean
public TokenBucketRateLimitFilter loginRateLimitFilter() {
    return new TokenBucketRateLimitFilter(10, 60, request ->
            "POST".equals(request.getMethod())
                    && ("/api/auth/login".equals(request.getRequestURI())
                            || "/api/auth/register".equals(request.getRequestURI())
                            || "/api/auth/refresh".equals(request.getRequestURI())));
}
```

10 attempt / dakika / IP. 1M-word dictionary attack 190 yıl sürer.

### Niye token-bucket, niye fixed-window değil?

Fixed-window: "1 dakikada 10 istek". Sınır anında 19 istek geçer (bir dakika sonu 10 + sonraki başı 9).

Token-bucket: anlık 10, sürekli 1 saniyede ~0.16 yeniden yeşerir. **Burst engellenir**, sürekli
trafik korunur.

### IP Tespiti — `X-Forwarded-For`

Reverse-proxy arkasında çalışıyoruz (Caddy → gateway → service). Doğrudan
`request.getRemoteAddr()` proxy IP'sini verir, **client IP**'sini değil. Helper:

```java
private String clientIp(HttpServletRequest r) {
    String xff = r.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
        return xff.split(",")[0].trim();   // ilk hop = original client
    }
    return r.getRemoteAddr();
}
```

XFF header'ı saldırgan tarafından **forge edilebilir** (HTTP request'te elle eklenir).
Caddy + nginx XFF'i append eder, override etmez. Bu yüzden **sadece** Caddy/nginx'in
arkasındaysak güvenli. Direct exposure varsa XFF'e güvenmek tehlikeli.

---

## 6. Anti-Enumeration: 404, 403 Değil

Bob, Alice'in siparişini istemek için `GET /api/orders/123` çağırırsa:

```java
@GetMapping("/{id}")
public OrderDto get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
    return repository.findByIdAndUserId(id, user.userId())  // ← scope filter
            .map(mapper::toDto)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Order not found"));
}
```

Order **var** ama Bob'un değil → 404 dönüyor.

Niye 403 değil:
- 403 "Forbidden" → "var ama erişimin yok" implies kaynağın varlığını teyit eder.
- 404 "Not Found" → "ya yok ya senin değil" — saldırgan **enumerate edip** Alice'in sipariş
  ID'lerini öğrenemez.

Spring Data'nın `findByIdAndUserId` derived query'i tek SQL. Filter DB seviyesinde, app-level
post-filter değil → race-safe.

Aynı pattern her user-scoped endpoint'te (cart, addresses, orders, user). Admin endpoint'leri
(`/api/orders/admin/{id}`) farklı — admin'in görme hakkı var, scope filter yok.

---

## 7. CORS — Gateway Seviyesinde Tek Yer

```yaml
# backend/api-gateway/src/main/resources/application.yml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:5173}
            allowedMethods: GET,POST,PUT,PATCH,DELETE,OPTIONS
            allowedHeaders: Authorization,Content-Type,X-Correlation-Id,X-Guest-Token
            allowCredentials: true
            maxAge: 3600
```

### Niye `allowedOrigins`, niye `allowedOriginPatterns: '*'` değil?

Spring'in pattern variant'ı **wildcard ile credentials birleştirir** — saldırgan kendi
sitesinden cookie'li request gönderebilir, browser kabul eder. CVE-eşdeğeri klasik bir hole.

`allowedOrigins` listesi **explicit origin enumeration** — yalnızca listedeki origin'lere
credentialed CORS. Production'da `CORS_ALLOWED_ORIGINS=https://yourdomain.com,https://admin.yourdomain.com`.

### Niye Gateway'de, Servislerde Değil

Tek bir CORS noktası — "her servis kendi CORS yazsın" demek 7 yerde değişiklik. Gateway her
yerden geçen tek hop, oraya koy.

---

## 8. Password Hashing — BCrypt

```java
@Bean public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

Default work-factor = 10. Production'da 12 yapılabilir — saldırgan GPU brute-force maliyeti
artar, login latency'si küçük artış (10ms → 40ms).

### Niye BCrypt, Argon2 değil?

Argon2 modern, memory-hard, GPU saldırısına daha dirençli. Reddedildi:
- Spring Security ekosistemi default BCrypt — daha az custom kod.
- Bootcamp scope'unda BCrypt yeter — Argon2 e-banking için daha mantıklı.
- Memory-hard fonksiyonlar verifier-side memory tüketir, microservice scaling'i etkiler.

### Niye plain bcrypt, salt manuel değil?

`BCryptPasswordEncoder` her hash başına **random salt** üretir, salt hash'in içine encode
edilir (`$2a$10$<salt><hash>`). Manuel salt yönetimi yok. Authoritative — kendi `MessageDigest`
ile bcrypt clone yazma.

---

## 9. Bilinçli Olarak Yapmadıklarımız

- **Cookie-based session yok**: API client'i React SPA, mobile gelirse JWT zaten doğru çalışır.
  CSRF concern'i de yok (CSRF cookie auth'da var).
- **CSP, HSTS, security headers**: Caddy Caddyfile'ında prod'da var (`security_headers` directive).
  Dev'de yok.
- **Per-tenant isolation**: Tek-tenant. Multi-tenant olsa `tenant_id` her tabloda + WHERE clause
  her query'de gerekir.
- **2FA**: Yok. SaaS portal değil, e-ticaret. Düşünüldü, scope dışı.
- **Audit log tablosu**: `Notification` audit'i var ama generic "her admin aksiyonu logla" yok.
  Application log'u (level INFO) yeterli — admin aksiyonları log'larda görünür.
- **Secret rotation otomasyonu**: `JWT_SECRET` rotation manuel. Vault/SealedSecrets gerekirse
  yapılır.

---

## İlgili Dokümanlar

- [`docs/services/auth-service.md`](services/auth-service.md) — JWT issue + refresh detayı
- [`docs/services/api-gateway.md`](services/api-gateway.md) — Gateway CORS + routing
- [`docs/services/common.md`](services/common.md) — JwtAuthenticationFilter + RateLimitFilter
- [`docs/services/frontend-admin.md`](services/frontend-admin.md) — Admin guard + 401 interceptor
