# `frontend` — Public Storefront

**Bu doküman:** Müşteri-yönlü React SPA. Sepet, sipariş, search, AI chatbot.

**Port:** 3000
**Stack:** React 18 + Vite 5 + Tailwind 3 + react-router-dom 6 + axios + framer-motion + lucide-react

---

## 1. Sorumluluklar

| Page | Path | Auth |
|---|---|---|
| Anasayfa | `/` | Public |
| Catalog (search + filter) | `/catalog?q=&filter=...` | Public |
| Ürün detay | `/products/:slug` | Public |
| Login | `/login` | Public — 3 tab: Telefon (Firebase OTP), E-posta, Google |
| Register | `/register` | Public |
| OAuth callback | `/auth/callback` | Public |
| Sepet | `/cart` | Auth |
| Sipariş listesi | `/orders` | Auth |
| Adres yönetimi | `/account/addresses` | Auth |
| Favoriler | `/favorites` | Auth |
| Profil | `/account` | Auth — email/isim edit + onboarding link |
| Checkout | `/checkout` | Auth — phone-only kullanıcı için EmailGate |
| Checkout processing | `/checkout/processing/:id` | Auth — saga durumu poll'ler |

---

## 2. State Management — React Context

Global state: **Context + useReducer/useState**, Redux yok.

```
AuthProvider (login, logout, token store)
└─ WishlistProvider (favorites set + sync)
   └─ CartProvider (cart items + quote + checkout)
      └─ ChatbotProvider (chat panel state + history)
         └─ App routes
```

### Niye Context, Redux Değil

3-4 global state slice (auth, cart, wishlist, chatbot). Bizim ölçek:
- Redux boilerplate (action creator + reducer + middleware) over-engineering.
- Context API + custom hook her slice için yeterli.
- DevTools experience eksik (Redux DevTools rich) ama development hız > production tooling.

State karmaşıklaşırsa Zustand veya Redux Toolkit eklenir; şu an gerek yok.

### `AuthContext`

```jsx
const AuthCtx = createContext(null);

export function AuthProvider({ children }) {
    const [user, setUser] = useState(() => tokenStore.getUser());
    const [loading, setLoading] = useState(false);
    
    const login = useCallback(async (email, password) => {
        const { data } = await api.post('/api/auth/login', { email, password });
        tokenStore.set(data);
        setUser(data.user);
    }, []);
    
    const logout = useCallback(async () => {
        const refreshToken = tokenStore.getRefresh();
        try { if (refreshToken) await api.post('/api/auth/logout', { refreshToken }); }
        catch { /* best-effort */ }
        tokenStore.clear();
        setUser(null);
    }, []);
    
    return <AuthCtx.Provider value={{ user, loading, login, logout }}>{children}</AuthCtx.Provider>;
}
```

### Cross-Tab Sync — `AUTH_EVENT`

```js
// api/client.js
export const AUTH_EVENT = 'n11:auth-change';

tokenStore.set = ({ accessToken, refreshToken, user }) => {
    if (accessToken) localStorage.setItem(ACCESS_KEY, accessToken);
    // ...
    window.dispatchEvent(new CustomEvent(AUTH_EVENT));
};
```

`AuthProvider` listen eder:
```jsx
useEffect(() => {
    const handler = () => setUser(tokenStore.getUser());
    window.addEventListener(AUTH_EVENT, handler);
    window.addEventListener('storage', handler);  // diğer tab'lar
    return () => { ... };
}, []);
```

Tab A'da logout olursa Tab B otomatik logged-out görünür. localStorage'ın `storage` event'i
sayesinde browser-side sync.

---

## 3. Login UI — 3 Tab + Lazy Firebase

`pages/LoginPage.jsx` üç login kanalına hizmet eder:

```
┌─ Google ile Giriş Yap (button, OAuth redirect)
│
├─ ─ veya ─
│
├─ Tab: [ Telefon ] [ E-posta ]      ← isFirebaseConfigured ise
│
├─ tab === 'phone'    → PhoneLoginForm (Firebase OTP)
└─ tab === 'email'    → EmailLoginForm (legacy)
```

> Üç akışın **sequence diagram'ı + backend bağlantıları**:
> [`docs/auth-flows.md`](../auth-flows.md).

### 3.1 `isFirebaseConfigured` — Build-Time Switch

```js
// lib/firebase.js
const config = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
};
export const isFirebaseConfigured = Boolean(
  config.apiKey && config.authDomain && config.projectId
);
```

Build-time env üçü de doluysa Phone tab default olur. Yoksa tab header gizlenir,
sadece email login render edilir — dev/preview build'ler Firebase olmadan
çalışmaya devam eder.

### 3.2 Lazy Firebase — Bundle Optimizasyonu

Firebase JS SDK ~250 kB. Önceden ana bundle'a giriyordu, her sayfa bu maliyeti
ödüyordu. Şimdi dynamic import:

```js
// lib/firebase.js
let cachedAuth = null;
let initPromise = null;

export async function getFirebaseAuth() {
  if (cachedAuth) return cachedAuth;
  if (!initPromise) {
    initPromise = (async () => {
      const [{ initializeApp, getApps }, { getAuth }] = await Promise.all([
        import('firebase/app'),
        import('firebase/auth'),
      ]);
      const app = getApps().length ? getApps()[0] : initializeApp(config);
      cachedAuth = getAuth(app);
      return cachedAuth;
    })();
  }
  return initPromise;
}
```

Vite/Rollup ayrı chunk üretir: `index.esm-*.js` (~190 kB Firebase auth). Sadece
kullanıcı `/login`'e gidip telefon tab'ına dokunduğunda indirilir. Main bundle:

```
ÖNCE:  661 kB
SONRA: 499 kB main + 234 kB Firebase chunk (lazy)
```

Ek olarak `index.html`'de **preconnect** hint'leri:

```html
<link rel="preconnect" href="https://identitytoolkit.googleapis.com" crossorigin />
<link rel="preconnect" href="https://www.googleapis.com" crossorigin />
<link rel="preconnect" href="https://securetoken.googleapis.com" crossorigin />
```

TR'den us-central'a TLS handshake idle'da kurulur, "Kod Gönder" tıklayınca soğuk
başlangıç maliyeti yok.

### 3.3 Two-Step Phone Form

`PhoneLoginForm` bir state machine:

```
step === 'phone'  → numara input + reCAPTCHA + "Kod Gönder"
                    │
                    └─→ Firebase signInWithPhoneNumber
                        │
                        ├─ ConfirmationResult kaydet (useRef)
                        └─ setStep('otp')
                                │
                                ▼
step === 'otp'    → 6-kutu OtpInput + auto-submit + "Numarayı değiştir"
                    │
                    └─→ confirmation.confirm(code)
                        │
                        └─ user.getIdToken() → POST /api/auth/login/phone
```

`ConfirmationResult` **useRef**'te tutulur — render'lar arasında kaybolmaz, çünkü
Firebase confirm metodunu sadece o object'in üzerinde çağırabilirsin.

### 3.4 reCAPTCHA Yönetimi

Firebase Phone Auth bot koruması için reCAPTCHA istiyor (`size: 'invisible'`).
Subtle gotcha: reCAPTCHA verifier **bir kez render edilir**, sonra reuse
edemezsin. Cleanup şart:

```jsx
useEffect(() => () => {
  if (recaptchaRef.current) {
    try { recaptchaRef.current.clear(); } catch { /* noop */ }
    recaptchaRef.current = null;
  }
}, []);
```

Login başarısız olursa da temizleyip baştan yaratıyoruz, yoksa Firebase wedged
kalıyor.

### 3.5 6-Kutu OTP UI

Custom `OtpInput` komponenti (3rd party paket eklemedik):

```jsx
<motion.input
  inputMode="numeric"
  maxLength={1}
  autoComplete={i === 0 ? 'one-time-code' : 'off'}
  className={digit ? 'border-n11-pink text-n11-pink' : 'border-gray-200'}
  animate={digit ? { scale: [1, 1.08, 1] } : {}}
  transition={{ duration: 0.18 }}
/>
```

**Davranış:**
- Auto-advance: digit girince `focus()` next cell
- Backspace: boş cell'de bir önceki cell'e atla
- Paste: 6 hane yapıştırılırsa otomatik dağıtır
- Auto-submit: 6. hane girilince `verifyCode` tetiklenir (kullanıcı butona basmaz)
- iOS one-time-code: ilk cell'deki `autoComplete="one-time-code"` SMS notification
  banner ile native autofill'i çalıştırır

framer-motion `scale` pulse — tamamlanan cell hafif zıplar, "kabul edildi"
feedback'i verir.

### 3.6 Onboarding Name Modal

Phone-only signup'ta `fullName` null. Login sonrası ilk kez load'da
`OnboardingNameDialog` çıkar (skipable + localStorage):

```jsx
// components/OnboardingNameDialog.jsx
useEffect(() => {
  if (!user) return;
  if (user.fullName?.trim()) return;
  if (window.localStorage?.getItem('n11.onboarding.nameSkipped')) return;
  setOpen(true);
}, [user]);
```

Save → `PATCH /api/users/me { fullName }` → `performRefresh()` (yeni JWT
fullName claim'iyle) → modal kapanır + toast `Hoş geldin, <ad>`.

App.jsx Routes ile sibling olarak mount:

```jsx
<Routes>...</Routes>
<Footer />
<ChatBubbleButton />
<ChatPanel />
<OnboardingNameDialog />   {/* her sayfada available */}
```

### 3.7 Checkout Email Gate

`CheckoutPage`'te `user.email === null` ise `EmailGate` üstte render edilir,
`canProceed` false olur:

```jsx
const needsEmail = user && !user.email;
const canProceed = needsEmail ? false : (...existing logic);

{needsEmail && <EmailGate />}
```

Kaydet butonu → `PATCH /api/users/me { email }` → `performRefresh()` → user.email
dolar → gate unmount → kullanıcı normal akışa devam eder.

Server-side de aynı koruma: order-service `user.email() == null` ise 422 döner.

> Detaylı sequence diagram: [`docs/auth-flows.md` § 6](../auth-flows.md#6-checkout-email-gate--telefon-only-kullanıcı-için).

---

## 4. Axios Client + Interceptors

`api/client.js`:

```js
const baseURL = import.meta.env.VITE_API_BASE_URL || '/api';
const root = baseURL.endsWith('/api') ? baseURL.slice(0, -4) : baseURL;
export const api = axios.create({ baseURL: root });

// Ayrı instance refresh için — interceptor recursion önler
const refreshClient = axios.create({ baseURL: root });

api.interceptors.request.use((config) => {
    const token = tokenStore.getAccess();
    if (token) config.headers.Authorization = `Bearer ${token}`;
    return config;
});
```

### 401 Interceptor — Single-Flight Refresh

```js
let refreshPromise = null;

async function performRefresh() {
    if (refreshPromise) return refreshPromise;   // single-flight
    refreshPromise = refreshClient
        .post('/api/auth/refresh', { refreshToken: tokenStore.getRefresh() })
        .then(({ data }) => {
            tokenStore.set(data);
            return data;
        })
        .finally(() => { refreshPromise = null; });
    return refreshPromise;
}

api.interceptors.response.use(
    (res) => res,
    async (err) => {
        const original = err.config;
        if (err.response?.status === 401
                && !original._retry
                && !original.url?.includes('/api/auth/')) {
            original._retry = true;
            try {
                await performRefresh();
                return api(original);
            } catch {
                tokenStore.clear();
                window.location.assign('/login');
            }
        }
        throw err;
    },
);
```

Niye single-flight: page-load anında 5 paralel API çağrısı 401 alırsa:
- Naive: 5 paralel /refresh → ilki başarılı + rotation, sonraki 4 "stale token" reuse-detect
  → family revoke → kullanıcı atılır.
- Single-flight: 5 çağrı **tek** /refresh promise'ini paylaşır → bir rotation, hepsi yeni
  token ile retry.

Detay: [`docs/security.md`](../security.md#single-flight-refresh--frontend-tarafı).

### `original._retry` Guard

Sonsuz loop önler. Refresh başarılı olduktan sonra original request retry edilir; bu retry
de 401 alırsa (refresh çalıştı ama yeni token da geçersiz?), `_retry=true` olduğu için
ikinci refresh denemeye gitmez → kullanıcı çıkar.

`original.url?.includes('/api/auth/')` exclusion: `/login` veya `/refresh` 401 dönerse
recursive infinite loop'u engeller.

---

## 5. Search Bar + Debounced Autocomplete

```jsx
const [value, setValue] = useState('');
const [suggestions, setSuggestions] = useState([]);
const debounceRef = useRef(null);

useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);
    if (!value || value.trim().length < 2) {
        setSuggestions([]);
        return;
    }
    debounceRef.current = setTimeout(() => {
        api.get(`/api/products/autocomplete?q=${encodeURIComponent(value.trim())}&limit=6`)
            .then((res) => setSuggestions(res.data));
    }, 220);
    return () => clearTimeout(debounceRef.current);
}, [value]);
```

220ms debounce — typing duruşunu tahmin et. Daha kısa = gereksiz API call, daha uzun = lag
hissi.

Min length 2 — tek harf çok generic.

Submit Enter'a:
```jsx
function onSubmit(e) {
    e.preventDefault();
    if (!value.trim()) return;
    navigate(`/catalog?q=${encodeURIComponent(value.trim())}`);
}
```

→ catalog page mount eder, FTS search çalışır, full result page.

---

## 6. Catalog Page — URL-Driven State

```
/catalog?q=iphone&categoryIds=1,2&minPrice=10000&maxPrice=50000&minRating=4&inStockOnly=true&sort=price_asc&page=2
```

`ProductListPage`:
```jsx
const [params, setParams] = useSearchParams();

const apiQuery = useMemo(() => buildApiQuery(params, PAGE_SIZE), [params]);

useEffect(() => {
    Promise.all([
        api.get(`/api/products?${apiQuery.list}`).then((res) => res.data),
        api.get(`/api/products/facets?${apiQuery.facets}`).then((res) => res.data),
    ])
    .then(([page, fac]) => { setData(page); setFacets(fac); });
}, [apiQuery.list, apiQuery.facets]);

function patchParams(patch) {
    const next = new URLSearchParams(params);
    Object.entries(patch).forEach(([k, v]) => {
        if (v == null || v === '') next.delete(k);
        else next.set(k, String(v));
    });
    next.set('page', '0');                 // herhangi filter değişimi page reset
    setParams(next);
}
```

URL = single source of truth. Detay: [`docs/search.md`](../search.md#7-url-driven-state-frontend).

---

## 7. Cart Hybrid (Guest + Authenticated)

Login etmemiş user da sepete ürün ekleyebilir. `CartContext`:

```jsx
function ensureGuestToken() {
    let t = localStorage.getItem('n11.guest.cartToken');
    if (!t) {
        t = crypto.randomUUID();
        localStorage.setItem('n11.guest.cartToken', t);
    }
    return t;
}

async function addItem(product, quantity) {
    const url = user
        ? '/api/cart/items'
        : `/api/cart/items?guestToken=${ensureGuestToken()}`;
    await api.post(url, { productId: product.id, quantity });
    await refresh();
}
```

Login olduğunda merge:
```jsx
useEffect(() => {
    if (!user) return;
    const guestToken = localStorage.getItem('n11.guest.cartToken');
    if (!guestToken) { refresh(); return; }
    api.post('/api/cart/merge', { guestToken })
        .finally(() => {
            localStorage.removeItem('n11.guest.cartToken');
            refresh();
        });
}, [user]);
```

---

## 8. AI Chatbot

`ChatBubbleButton.jsx`:
```jsx
<motion.div
    animate={{ scale: [1, 1.05, 1] }}
    transition={{ duration: 2.5, repeat: Infinity }}
    className="grid place-items-center rounded-full ...">
    <Bot className="h-6 w-6 text-white" />
    <Sparkles className="absolute -top-1 -right-1 h-3 w-3 ..." />
</motion.div>
```

Breathing pulse + sparkle twinkle. framer-motion ile sürekli animasyon. User'ın dikkatini
çekiyor ama abartılı değil.

`ChatPanel.jsx`:
- Spring scale + slide-in mount animasyonu.
- 3-dot typing indicator (LLM yanıt beklerken).
- Mesaj listesi virtualize edilmiyor (max ~50 mesaj/session, performant).

---

## 9. Build & Deploy

```dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
ARG VITE_API_BASE_URL=/api
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

`nginx.conf`:
```nginx
location /api/ {
    proxy_pass http://gateway;     # api-gateway:8080
}
location / {
    try_files $uri /index.html;    # SPA fallback
}
```

Container'da axios baseURL '/api' (same-origin) → nginx `/api/*`'ı gateway'e proxy → gateway
service'lere distribute.

Niye `try_files`: SPA route'ları (`/orders`, `/products/iphone-15`) browser direct-load'da
nginx'in kendi 404'ünden önce React Router'a düşmesi için. `index.html` her zaman serve →
React boot → router mount → doğru sayfa.

---

## 10. Testing — Vitest

`vitest.config.js`:
```js
test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.js'],
}
```

Component testleri minimum — bootcamp scope'unda backend coverage öncelikli. Eklenen
test'ler kritik invariant'ları korur (cart toplam hesabı, login flow).

---

## 11. Bilinçli Olarak Yapmadıklarımız

- **SSR / Next.js**: SPA + nginx tek-sayfa-uygulaması. SEO için SSR mantıklı ama scope dışı.
- **Service worker / offline**: PWA convert edilebilir. Şu an sadece online-mode.
- **Image optimization (next/image equivalent)**: Stock URL'leri olduğu gibi kullanılır.
  Cloudinary / imgix transform'u eklenebilir.
- **i18n**: Sadece Türkçe. `react-i18next` ile EN eklenebilir.
- **Error boundary fallback UI**: Generic toast + retry; fancy "yandık özür dileriz" sayfası
  yok.
- **Web Vitals tracking**: Lighthouse manuel; otomatik telemetry yok.

---

## 12. Klasör Yapısı

```
frontend/
├── package.json
├── Dockerfile, nginx.conf, vite.config.js, tailwind.config.js, postcss.config.js
├── index.html
└── src/
    ├── main.jsx                       # provider tree
    ├── App.jsx                        # routes
    ├── api/
    │   └── client.js                  # axios + interceptors + tokenStore
    ├── state/                         # React Context providers
    │   ├── AuthContext.jsx
    │   ├── CartContext.jsx
    │   ├── WishlistContext.jsx
    │   └── ChatbotContext.jsx
    ├── pages/
    │   ├── HomePage.jsx
    │   ├── ProductListPage.jsx        # /catalog
    │   ├── ProductDetailPage.jsx
    │   ├── CartPage.jsx
    │   ├── OrdersPage.jsx
    │   ├── AddressBookPage.jsx
    │   ├── WishlistPage.jsx
    │   ├── LoginPage.jsx, RegisterPage.jsx
    │   └── OAuthCallbackPage.jsx
    ├── components/
    │   ├── ProductCard.jsx
    │   ├── Pagination.jsx
    │   ├── ProtectedRoute.jsx
    │   ├── header/                    # SearchBar, TopBar, ...
    │   ├── product/                   # RatingStars, HeartButton, RecommendationStrip,
    │   │                              #   ReviewsSection, ProductRail
    │   ├── catalog/
    │   │   └── FilterSidebar.jsx
    │   └── chatbot/
    │       ├── ChatBubbleButton.jsx
    │       └── ChatPanel.jsx
    ├── utils/
    │   ├── format.js                  # formatCurrency, formatDate
    │   └── guestWishlist.js
    └── styles/
        └── index.css                  # Tailwind directives + .input/.btn-* @layer
```

---

## İlgili Dokümanlar

- [`docs/security.md`](../security.md) — Token store + single-flight refresh detayı
- [`docs/search.md`](../search.md) — Catalog page URL state
- [`docs/services/auth-service.md`](auth-service.md) — Backend auth flow
- [`docs/services/frontend-admin.md`](frontend-admin.md) — Sister admin panel
