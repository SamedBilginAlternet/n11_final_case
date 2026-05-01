# `auth-service`

**Bu doküman:** Identity + access management. Register, login, refresh, OAuth2, address book,
user listing/promote.

**Port:** 8081
**DB:** `authdb`
**Stack:** Spring Boot 3 + Spring Security + JPA + Flyway + Redis (cache yok ama RabbitMQ yok)

---

## 1. Sorumluluklar

| Concern | Endpoint(ler) |
|---|---|
| Self-registration (email + şifre) | `POST /api/auth/register` |
| Email login | `POST /api/auth/login` |
| **Phone login (Firebase OTP)** | **`POST /api/auth/login/phone`** |
| Refresh access token | `POST /api/auth/refresh` |
| Logout | `POST /api/auth/logout` |
| OAuth2 (Google) | `GET /api/auth/oauth2/authorize/google` → callback flow |
| Self profile | `GET /api/users/me` |
| **Self profile update** | **`PATCH /api/users/me`** (email/fullName) |
| Address book | `GET/POST/PUT/DELETE /api/addresses` |
| Admin user listing | `GET /api/users`, `POST /api/users/{id}/promote\|demote` |

Sorumluluk **dışı**:
- Password reset email — yok (şimdilik); notification-service eklenebilir.
- 2FA (şifre + SMS ikisi birden) — yok; biz **passwordless phone login** yaptık.
- Audit log tablosu — yok; structured log yeterli (`Admin <email> promoted userId=X`).

> **3 login kanalı, tek JWT akışı**. Detaylı sequence diagram'lar + UI ref'leri:
> [`docs/auth-flows.md`](../auth-flows.md).

---

## 2. JWT Issue & Refresh Rotation

### Login Akışı

```
POST /api/auth/login { email, password }
  ↓
auth-service:
  1. UserRepository.findByEmailIgnoreCase(email)
  2. passwordEncoder.matches(password, user.passwordHash)
  3. JwtTokenProvider.issue(user) → access JWT (60dk)
  4. RefreshTokenService.issueNewFamily(user, ua, ip) → opaque refresh (30 gün)
  5. Response: { accessToken, refreshToken, user }
```

### `JwtTokenProvider`

```java
public IssuedToken issue(User user) {
    Instant now = Instant.now();
    Instant exp = now.plus(accessTtlMinutes, ChronoUnit.MINUTES);
    String token = Jwts.builder()
            .subject(user.getId().toString())
            .issuer(issuer)
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .claim("email", user.getEmail())
            .claim("role", user.getRole().name())
            .signWith(key)
            .compact();
    return new IssuedToken(token, accessTtlMinutes * 60);
}
```

Detay: [`docs/security.md`](../security.md#2-jwt--hs256-servis-tarafında-doğrulama).

### Refresh Token Rotation + Reuse Detection

`RefreshTokenService.rotate()`:

```java
@Transactional
public RotateResult rotate(String presented, String userAgent, String ip) {
    String hash = sha256(presented);
    RefreshToken token = repository.findByTokenHash(hash)
            .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Invalid refresh token"));

    if (token.getRevokedAt() != null) {
        // ⚠️ Reuse detected
        repository.revokeFamily(token.getFamilyId(), Instant.now());
        throw new ResponseStatusException(UNAUTHORIZED, "Token reuse detected — family revoked");
    }
    if (token.getExpiresAt().isBefore(Instant.now())) {
        throw new ResponseStatusException(UNAUTHORIZED, "Expired");
    }

    // ROTATE: yeni token üret, eskiyi revoke et
    String newRaw = generateOpaqueToken();
    RefreshToken newRow = RefreshToken.builder()
            .userId(token.getUserId())
            .familyId(token.getFamilyId())   // aynı family
            .tokenHash(sha256(newRaw))
            .expiresAt(Instant.now().plus(refreshTtlDays, DAYS))
            .userAgent(userAgent)
            .ip(ip)
            .build();
    RefreshToken saved = repository.save(newRow);
    token.setRevokedAt(Instant.now());
    token.setReplacedById(saved.getId());

    return new RotateResult(newRaw, saved.getExpiresAt(), token.getUserId());
}
```

Detaylı flowchart + niye reuse detection: [`docs/security.md`](../security.md#3-refresh-token-rotation--reuse-detection).

### Migration

```sql
-- V3__refresh_tokens.sql
CREATE TABLE refresh_tokens (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    token_hash    CHAR(64) NOT NULL UNIQUE,
    family_id     UUID NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    revoked_at    TIMESTAMPTZ,
    replaced_by_id BIGINT,
    user_agent    TEXT,
    ip            VARCHAR(45),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_family_id ON refresh_tokens (family_id);
CREATE INDEX ix_refresh_tokens_expires_at ON refresh_tokens (expires_at);
```

`token_hash` UNIQUE — duplicate insert imkansız (her token unique random'dan üretildiği için
zaten çakışmaz, UNIQUE belt-and-suspenders).

`family_id` index — reuse detection sırasında `revokeFamily(familyId)` query'sinin O(log n)
olması için.

---

## 3. OAuth2 — Google Login

### Konfig

```yaml
n11:
  social-login:
    frontend-base-url: ${FRONTEND_BASE_URL:http://localhost:3000}
    success-path: /auth/callback
    failure-path: /login
    google:
      client-id: ${GOOGLE_CLIENT_ID:}
      client-secret: ${GOOGLE_CLIENT_SECRET:}
```

`OAuth2ClientConfig` `client-id` boşsa Google registration'ı **kayıt etmez**.
`SocialLoginProperties.googleEnabled()` `false` döner. SecurityConfig:

```java
if (socialLoginProperties.anyEnabled()) {
    http.oauth2Login(oauth -> oauth
            .authorizationEndpoint(a -> a.baseUri("/api/auth/oauth2/authorize"))
            .redirectionEndpoint(r -> r.baseUri("/api/auth/oauth2/callback/*"))
            .successHandler(oAuth2LoginSuccessHandler)
            .failureHandler(oAuth2LoginFailureHandler)
    );
}
```

`anyEnabled() == false` → `oauth2Login` chain hiç eklenmez. Endpoint 404 olur. Login butonu
frontend'de yine var ama tıklanırsa boş sayfa. Bilinçli bir trade-off — backend tarafında
silmek için frontend'e de gitmek gerek (yapıldı: GitHub kaldırıldı, Google opsiyonel kaldı).

### Akış

1. User `/login` sayfasında "Google ile Giriş" butonuna tıklar.
2. Tarayıcı `/api/auth/oauth2/authorize/google`'a navigate olur.
3. Spring Security PKCE+state ile Google'a redirect — kullanıcı Google'da login + onay.
4. Google → `/api/auth/oauth2/callback/google` (Spring filter handles).
5. `OAuth2LoginSuccessHandler.onAuthenticationSuccess`:
   - `subject = principal.getAttribute("sub")` (OIDC standardı)
   - `email = principal.getAttribute("email")`
   - `SocialLoginService.upsert(provider, subject, email, name)` — yeni user ya da existing
     match (provider+subject ile, yoksa email ile link)
   - Standard JWT issue
   - URL fragment ile redirect: `${FRONTEND_BASE_URL}/auth/callback#token=...&refreshToken=...`

### Niye URL Fragment

`#fragment` browser-side, **server log'una gitmez**. Server access log'unda token görünmez.
`?query` parametresi olsa nginx access log'unda kalırdı + Referer header'ı ile sonraki sayfaya
sızabilirdi.

Frontend `/auth/callback` sayfası fragment'ı parse eder, localStorage'a koyar, ana sayfaya
yönlendirir.

### `SocialLoginService.upsert`

```java
@Transactional
public User upsert(String provider, String subject, String email, String fullName) {
    // 1. (provider, subject) ile direkt match
    Optional<User> direct = userRepository.findByOauthProviderAndOauthSubject(provider, subject);
    if (direct.isPresent()) return direct.get();
    
    // 2. Email ile match — local kullanıcının üzerine OAuth bağla
    Optional<User> byEmail = userRepository.findByEmailIgnoreCase(email);
    if (byEmail.isPresent()) {
        User u = byEmail.get();
        u.setOauthProvider(provider);
        u.setOauthSubject(subject);
        return u;  // dirty: JPA flush'ta save
    }
    
    // 3. Yeni kullanıcı — password_hash null
    User newUser = User.builder()
            .email(email)
            .fullName(fullName)
            .role(Role.USER)
            .oauthProvider(provider)
            .oauthSubject(subject)
            // .passwordHash(null) — OAuth-only, password yok
            .build();
    return userRepository.save(newUser);
}
```

Email ile link önemli: Bob `bob@x.com` ile **password ile** kayıt olduysa, sonra Google ile
login etse → aynı `bob@x.com` → aynı user, OAuth field'ları doldurulur. Hesap fragmenta-
syonu yok.

---

## 4. Phone Login — Firebase OTP

Eski email-only modeli yetmedi: TR e-ticaretin default'u **telefon + SMS OTP**
(Trendyol, Getir, Hepsiburada). Mevcut kullanıcılara dokunmadan üçüncü bir login
kanalı eklendi.

> **Tam sequence diagram + UI tarafı**: [`docs/auth-flows.md` § 3](../auth-flows.md#3-telefon--sms-otp-firebase).

### 4.1 Akış (Backend Perspektifi)

```
POST /api/auth/login/phone { idToken: "<firebase-jwt>" }
  ↓
auth-service:
  1. FirebaseTokenVerifier.verify(idToken)
       → FirebaseAuth.verifyIdToken (offline JWKS)
       → return VerifiedPhoneIdentity(uid, "+905551234567")
  2. PhoneLoginService.upsertByPhone("+905551234567")
       → findByPhoneNumber OR INSERT yeni user (email=null, fullName=null)
  3. AuthenticationService.issueTokens(user)
       → Aynı flow olarak email login (JWT 60dk + refresh 30 gün)
  4. Response: { accessToken } + Set-Cookie n11_refresh
```

### 4.2 `FirebaseTokenVerifier`

```java
@Service
@ConditionalOnBean(FirebaseAuth.class)
@RequiredArgsConstructor
public class FirebaseTokenVerifier {
    private final FirebaseAuth firebaseAuth;

    public VerifiedPhoneIdentity verify(String idToken) {
        FirebaseToken token = firebaseAuth.verifyIdToken(idToken, true);
        Object phoneClaim = token.getClaims().get("phone_number");
        if (!(phoneClaim instanceof String phone) || phone.isBlank()) {
            throw new BadCredentialsException("Token has no phone_number claim");
        }
        return new VerifiedPhoneIdentity(token.getUid(), phone);
    }
}
```

İkinci parametre `true` — **token revocation check** açık. Firebase admin tarafında
revoke edilen session'lar reddedilir. Doğrulama **offline** (JWKS cache 1 saat).

### 4.3 `FirebaseConfig` — Conditional Wiring

```java
@Configuration
@ConditionalOnProperty(prefix = "n11.firebase", name = "service-account-json")
public class FirebaseConfig {
    @Bean
    public FirebaseApp firebaseApp(@Value("${n11.firebase.service-account-json}") String json) throws Exception {
        if (!FirebaseApp.getApps().isEmpty()) return FirebaseApp.getInstance();
        return FirebaseApp.initializeApp(FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(
                        new ByteArrayInputStream(json.getBytes(UTF_8))))
                .build());
    }
}
```

`FIREBASE_SERVICE_ACCOUNT_JSON` env var'ı boşsa bean üretilmez, `FirebaseTokenVerifier`
da `@ConditionalOnBean(FirebaseAuth.class)` ile gated. Sonuç:
- Dev/CI Firebase config'i olmadan auth-service ayağa kalkar
- `/login/phone` endpoint'i 401 döner (`Phone login is not configured`)
- Email/Google login'i etkilenmez

### 4.4 `PhoneLoginService` — Identity Modeli

```java
@Transactional
public User upsertByPhone(String phoneNumber) {
    String normalised = phoneNumber.trim();
    return userRepository.findByPhoneNumber(normalised)
            .orElseGet(() -> userRepository.save(User.builder()
                    .phoneNumber(normalised)
                    .role(Role.USER)
                    .enabled(true)
                    .build()));
}
```

Email/fullName kasıtlı **null** — phone-only signup'lar için bu alanlar
sonra toplanır:
- **Email** — checkout sırasında gate (zorunlu)
- **fullName** — login sonrası onboarding modal (opsiyonel)

### 4.5 Schema (V5)

```sql
-- V5__phone_auth.sql
ALTER TABLE users
    ALTER COLUMN email     DROP NOT NULL,
    ALTER COLUMN full_name DROP NOT NULL,
    ADD COLUMN phone_number VARCHAR(20);

CREATE UNIQUE INDEX ux_users_phone_number ON users (phone_number)
    WHERE phone_number IS NOT NULL;
```

`partial unique index` deseni — null kayıtlar (email-only legacy users) ile çakışmaz.

### 4.6 Profile Update — `PATCH /api/users/me`

Telefon-only kullanıcı sonradan email/isim ekleyebilsin diye:

```java
// UserController.updateMe
if (body.email() != null) {
    String normalised = body.email().trim().toLowerCase();
    if (!normalised.equalsIgnoreCase(user.getEmail())
            && userRepository.existsByEmailIgnoreCase(normalised)) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu e-posta başka bir hesapta kayıtlı");
    }
    user.setEmail(normalised);
}
if (body.fullName() != null && !body.fullName().isBlank()) {
    user.setFullName(body.fullName().trim());
}
```

PATCH semantiği: gönderilmemiş alanlar el sürülmez. Frontend gerektiğinde **JWT
refresh** çağrısı atar — yeni email/fullName claim'leri downstream servislere
hemen yansır (order-service'de `customer_email` snapshot'ı doğru gelsin diye).

### 4.7 Konfigürasyon

```yaml
# application.yml
n11:
  firebase:
    service-account-json: ${FIREBASE_SERVICE_ACCOUNT_JSON:}
```

```yaml
# docker-compose.prod.yml
auth-service:
  environment:
    FIREBASE_SERVICE_ACCOUNT_JSON: ${FIREBASE_SERVICE_ACCOUNT_JSON:-}
```

JSON içerik **Infisical'da** tutulur — geliştirici sadece UI'dan rotate eder,
deploy çalışınca yeni değer otomatik iner. Detay:
[`docs/secrets-management.md`](../secrets-management.md).

---

## 5. Address Book

User'ların kayıtlı teslimat adresleri. `addresses` tablosu user_id FK ile.

### Default Address

```sql
-- V4__addresses.sql
CREATE TABLE addresses (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(60) NOT NULL,
    recipient_name VARCHAR(120) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    line1 VARCHAR(255) NOT NULL,
    city VARCHAR(80) NOT NULL,
    district VARCHAR(80) NOT NULL,
    postal_code VARCHAR(10) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Partial unique index: bir kullanıcının max 1 default address'i
CREATE UNIQUE INDEX ux_addresses_user_default
    ON addresses (user_id) WHERE is_default = TRUE;
```

### Niye Partial Unique Index

Trivial alternatif: app-side check. Race koşullarında iki request paralel "set as default"
yapar → iki default oluşur. Index DB-side garantor.

### Promote-to-Default Pattern

```java
@Transactional
public AddressDto setDefault(Long userId, Long addressId) {
    Address target = repository.findByIdAndUserId(addressId, userId)
            .orElseThrow(() -> new EntityNotFoundException(...));   // 404, anti-enumeration
    
    if (target.isDefault()) return mapper.toDto(target);  // already default, no-op
    
    // Eski default'u temizle (eğer varsa)
    repository.clearDefaultsFor(userId);
    
    target.setDefaultAddress(true);
    return mapper.toDto(target);  // dirty save
}
```

`clearDefaultsFor` ve `setDefaultAddress(true)` **aynı transaction'da**. Index violation
imkansız (TX commit'inden önce eski default false'a çekilmiş, yeni default true).

### İlk Address Otomatik Default

```java
boolean firstAddress = repository.findFirstByUserIdAndDefaultAddressTrue(userId).isEmpty()
        && repository.findByUserIdOrderByDefaultAddressDescIdAsc(userId).isEmpty();
if (firstAddress) entity.setDefaultAddress(true);
```

`&&` short-circuit: ilk call true (no current default) ise ikinci call'ı yap (no addresses
at all). Eğer mevcut bir default varsa zaten ilk address değildir; second call yapma.

Test'te dikkat: Mockito strict-stub mode'da kullanılmayan stub fail eder. Test'in mevcut
default scenario'sunda **second call için stub ekleme**, çünkü çağrılmıyor.

### Order-Service'den Address Fetch

Order checkout'ta order-service `AddressClient` ile auth-service'in `/api/addresses/{id}`
endpoint'ini çağırır:

```java
// order-service AddressClient
public AddressDto get(Long addressId, String bearerToken) {
    return restClient.get()
            .uri("/api/addresses/" + addressId)
            .header("Authorization", "Bearer " + bearerToken)  // user'ın JWT'si
            .retrieve()
            .body(AddressDto.class);
}
```

User'ın **kendi token'ını** forward eder. Auth-service `findByIdAndUserId` ile owner check
yapar — Bob, Alice'in adresini gönderemez. 404 anti-enumeration.

---

## 6. Role Management

### Schema

```sql
-- V1
CREATE TABLE users (
    ...
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    ...
);
```

```java
public enum Role { USER, ADMIN }
```

### Promote/Demote

```java
@PreAuthorize("hasRole('ADMIN')")
@PostMapping("/{id}/promote")
public UserDto promote(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
    User u = userRepository.findById(id).orElseThrow(...);
    u.setRole(Role.ADMIN);
    log.info("Admin {} promoted userId={} to ADMIN", actor.email(), id);
    return userMapper.toDto(userRepository.save(u));
}

@PreAuthorize("hasRole('ADMIN')")
@PostMapping("/{id}/demote")
public UserDto demote(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser actor) {
    if (actor.userId() != null && actor.userId().equals(id)) {
        throw new ResponseStatusException(CONFLICT,
                "Kendi rolünü düşüremezsin — başka bir admin'e yaptır.");
    }
    // ... demote
}
```

Self-demote 409 — son admin'in kendini demote edip sistemi locked-out etmesini engeller.

İlk admin'i seed et:
```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'samed@example.com';
```

İlk kullanıcılar UI'dan kayıt olur, sonra DB'de manuel ADMIN yapılır. Production'da
"first user becomes admin" pattern'i de mantıklı ama basit migration'a göre daha karmaşık.

---

## 7. SecurityConfig — Filter Chain

```java
http
    .csrf(csrf -> csrf.disable())                          // stateless API
    .formLogin(f -> f.disable())                           // SPA, login JSON ile
    .httpBasic(b -> b.disable())                           // Basic auth no-go
    .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))   // no cookie session
    .authorizeHttpRequests(auth -> auth
        .requestMatchers(POST, "/api/auth/register", "/api/auth/login",
                         "/api/auth/refresh", "/api/auth/logout").permitAll()
        .requestMatchers("/api/auth/oauth2/**").permitAll()
        .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
        .anyRequest().authenticated()
    )
    .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
    .addFilterBefore(loginRateLimitFilter, JwtAuthenticationFilter.class);

if (socialLoginProperties.anyEnabled()) {
    http.oauth2Login(...);
}
```

### Filter Ordering

```
TokenBucketRateLimitFilter
  ↓
JwtAuthenticationFilter
  ↓
UsernamePasswordAuthenticationFilter (Spring's, no-op çünkü disabled formLogin)
  ↓
ExceptionTranslationFilter
  ↓
FilterSecurityInterceptor (authorize check)
```

Rate limit JWT'den **önce** çalışır — saldırgan bir saatte 100K JWT denemesi yapamaz.
JWT olmadan da rate limit `/login` endpoint'ini koruyor.

### Method Security

```java
@EnableMethodSecurity
```

`@PreAuthorize` no-op olmasın diye. Bu projede tüm 4 servisin SecurityConfig'i `@EnableMethodSecurity`
ile açılır (auth, product, cart, order). Diğerleri (chatbot, notification) admin endpoint
yok, gerekmez.

---

## 8. Logout

```java
@PostMapping("/logout")
public ResponseEntity<Void> logout(@RequestBody LogoutRequest req) {
    refreshTokenService.revoke(req.refreshToken());
    return ResponseEntity.noContent().build();
}
```

Sadece **refresh token'ı revoke eder**. Access token zaten kısa-ömürlü, ek işlem gerek yok
(50 dk içinde dolacak). Saldırgan elindeki access'i 50dk daha kullanabilir, refresh'i edemez.

Hard logout istenirse — gateway-side blocklist (Redis) eklenir, her request access token'ı
blocklist'e karşı kontrol eder. Bu projede yok — JWT'nin stateless avantajını kaybetmek değer
değil.

---

## 9. Bilinçli Olarak Yapmadıklarımız

- **Email verification**: User registers, email'i hemen aktif. Production'da double-opt-in
  + verification token + email link gerekir.
- **Password reset**: "Forgot password" akışı yok. notification-service ile eklenebilir
  (reset token + email + form).
- **2FA / TOTP**: Authenticator app, SMS, vb.
- **OAuth provider çoklama**: GitHub kaldırıldı (e-ticaret için saçma), Apple/Twitter eklenmedi.
- **User profile editing**: Email değiştirme, password change UI'da yok. Backend `PUT /api/users/me`
  eklenebilir.
- **Account deletion / GDPR right-to-erasure**: Yok. Production-grade GDPR-compliance için
  cascade delete + audit trail gerekir.

---

## 10. Klasör Yapısı

```
backend/auth-service/
├── pom.xml
└── src/main/java/com/n11/auth/
    ├── AuthApplication.java
    ├── api/
    │   ├── AuthController.java        # /api/auth/* endpoints
    │   ├── UserController.java        # /api/users/{me,id/promote,id/demote}
    │   ├── AddressController.java     # /api/addresses
    │   └── dto/                       # LoginRequest, AuthTokenResponse, etc.
    ├── config/
    │   ├── SecurityConfig.java        # filter chain + @EnableMethodSecurity
    │   ├── OAuth2ClientConfig.java    # ClientRegistration bean (Google)
    │   ├── SocialLoginProperties.java
    │   └── JwtProperties.java
    ├── domain/
    │   ├── User.java                  # entity (id, email, passwordHash, role, oauth*)
    │   ├── Role.java                  # USER, ADMIN
    │   ├── RefreshToken.java          # entity
    │   └── Address.java
    ├── repository/
    │   ├── UserRepository.java
    │   ├── RefreshTokenRepository.java
    │   └── AddressRepository.java
    ├── security/
    │   ├── JwtTokenProvider.java      # issue + parse (HS256)
    │   ├── OAuth2LoginSuccessHandler.java
    │   └── OAuth2LoginFailureHandler.java
    └── service/
        ├── RegistrationService.java
        ├── RefreshTokenService.java   # rotate + reuse detection
        ├── SocialLoginService.java    # OAuth user upsert
        └── AddressService.java
```

---

## İlgili Dokümanlar

- [`docs/security.md`](../security.md) — JWT + refresh + rate limit detayı
- [`docs/services/order-service.md`](order-service.md) — Address forward to order-service
- [`docs/services/frontend.md`](frontend.md) — Single-flight refresh client-side
- [`docs/services/frontend-admin.md`](frontend-admin.md) — Admin user management UI
