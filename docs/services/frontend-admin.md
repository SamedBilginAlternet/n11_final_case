# `frontend-admin` — Back-Office Panel

**Bu doküman:** Admin paneli ayrı SPA. Sipariş lifecycle yönetimi, ürün CRUD, kupon CRUD,
kategori CRUD, kullanıcı role yönetimi, KPI dashboard.

**Port:** 3001
**Stack:** React 18 + Vite 5 + Tailwind 3 + react-router-dom 6 + axios + recharts + framer-motion + lucide-react

---

## 1. Niye Ayrı Frontend

Mağaza ve back-office aynı SPA olabilirdi. Reddedildi:

| Konu | Tek SPA | Ayrı SPA (bizim) |
|---|---|---|
| Bundle size | Tüm code public bundle'a kompile | Public minimal, admin ayrı |
| Visual ayrım | Aynı tema → kullanıcı admin'de mağaza-mode'a girer | Net farklı palet (slate+indigo vs pink) |
| Deploy bağımsız | Birlikte | Bağımsız (admin update mağaza yayını dondurmaz) |
| Auth karışıklığı | Aynı session → admin tabı kapatınca mağaza da çıkar | LocalStorage namespace ayrımı (`n11.admin.*` vs `n11.*`) |
| Feature creep | Admin panel zaman içinde büyür, public bundle şişer | Bağımsız büyüme |

Ayrı klasör, ayrı build, ayrı container. Aynı backend.

---

## 2. Sayfalar

| Page | Path | Erişim |
|---|---|---|
| Login | `/login` | Public (ama ADMIN-only login) |
| Anasayfa (KPI dashboard) | `/` | ADMIN |
| Siparişler | `/orders` | ADMIN |
| Ürünler | `/products` | ADMIN |
| Kategoriler | `/categories` | ADMIN |
| Kuponlar | `/coupons` | ADMIN |
| Kullanıcılar | `/users` | ADMIN |

---

## 3. Auth — ADMIN-Only Client-Side Filter

```jsx
// AuthContext.jsx
const login = useCallback(async (email, password) => {
    const { data } = await api.post('/api/auth/login', { email, password });
    if (data.user?.role !== 'ADMIN') {
        toast.error('Bu hesap admin yetkisine sahip değil.');
        throw new Error('not-admin');
    }
    tokenStore.set(data);
    setUser(data.user);
}, []);
```

Niye client-side reject:
- Backend'in `@PreAuthorize` zaten her admin endpoint'i 403 ile koruyor.
- Ama USER role'lü kullanıcı admin login'i geçerse, admin paneline yüklenir, her endpoint
  403 alır → "yetkin yok" toast spam.
- Pre-emptive client-side reject = clean UX.

Trust boundary backend'de — client-side filter sadece UX optimizasyonu. Saldırgan
JWT'sini elle inject etse de backend yine reddeder.

### `AdminGuard` Route Wrapper

```jsx
export default function AdminGuard({ children }) {
    const { user } = useAuth();
    const location = useLocation();
    
    if (!user) {
        return <Navigate to="/login" state={{ from: location }} replace />;
    }
    if (user.role !== 'ADMIN') {
        return <Navigate to="/login" replace />;
    }
    return children;
}
```

App.jsx:
```jsx
<Route element={<AdminGuard><AdminLayout /></AdminGuard>}>
    <Route path="/" element={<DashboardPage />} />
    <Route path="/orders" element={<OrdersPage />} />
    {/* ... */}
</Route>
<Route path="/login" element={<LoginPage />} />
```

User loglu değilse `/login`'e redirect. Stale localStorage role=USER'ı varsa da redirect
(her render kontrol).

---

## 4. localStorage Namespace İzolasyonu

```js
// admin api/client.js
const ACCESS_KEY = 'n11.admin.token';
const REFRESH_KEY = 'n11.admin.refreshToken';
const USER_KEY = 'n11.admin.user';
```

```js
// public frontend/src/api/client.js
const ACCESS_KEY = 'n11.token';
// ...
```

Namespace farklı. İki tab açıkken (storefront + admin):
- Storefront login `n11.token`'a yazar.
- Admin login `n11.admin.token`'a yazar.
- Birbirini etkilemiyor.

Niye iki ayrı session: bir admin user mağaza tarafında "alışveriş yapıyor", admin tarafında
"sipariş yönetiyor". Aynı session = aynı user. İki tab açıkken birinde logout edince diğeri
de çıkar (cross-tab `storage` event). Bu admin için **istenmeyen** — namespace ayrımı çözüm.

---

## 5. Visual Identity — Indigo + Slate Palette

```js
// tailwind.config.js
colors: {
    brand: {
        50:  '#eef2ff',
        500: '#6366f1',
        600: '#4f46e5',
        700: '#4338ca',
        // ...
    },
}
```

vs storefront:
```js
n11: {
    pink: '#F5167E',
    pinkDark: '#C1035E',
    // ...
}
```

İndigo "tool" hissi (Linear, GitHub, Vercel admin tarzı). Slate base = nötr arka plan.

Admin'in UI'sı **bilinçli olarak farklı**: bir admin iki tab'ı açıkken hangi tab'da olduğunu
periferal görüşte anlamalı. Yanlış tab'da "Sil" butonuna basmamak için.

---

## 6. Dashboard — recharts

`/` (anasayfa) recharts grafikleri:

```jsx
<ResponsiveContainer width="100%" height={260}>
    <LineChart data={(orderMetrics?.daily || []).map((d) => ({ ...d, dateLabel: shortDate(d.date) }))}>
        <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
        <XAxis dataKey="dateLabel" tick={{ fontSize: 11, fill: '#64748b' }} />
        <YAxis tick={{ fontSize: 11, fill: '#64748b' }} allowDecimals={false} />
        <Tooltip contentStyle={{ fontSize: 12, borderRadius: 8 }} />
        <Line type="monotone" dataKey="orderCount" stroke="#6366f1" strokeWidth={2} dot={false} name="Sipariş" />
    </LineChart>
</ResponsiveContainer>
```

4 chart: line (orders/day), bar (revenue), donut (status), horizontal bar (top categories).
+ 4 KPI kartı: Bugün sipariş, Bugün ciro, Bekleyen, Düşük stok.
+ Düşük stok listesi.

Veri kaynağı: backend'in iki metric endpoint'i:
- `/api/orders/admin/metrics?days=30`
- `/api/products/admin/metrics?lowStockThreshold=10`

Detay: [`docs/services/order-service.md`](order-service.md#7-dashboard-metrics) ve
[`docs/services/product-service.md`](product-service.md#7-admin-metrics).

---

## 7. Orders Page — Lifecycle Drawer

```jsx
{selected && (
    <OrderDetailDrawer
        orderId={selected}
        onClose={() => setSelected(null)}
        onChanged={refresh}
    />
)}
```

Sağ-yan drawer:
- Order detail (customer, shipping, items, timeline).
- Status-aware action panel:
  - CONFIRMED → "Hazırlamaya Başla" button (no email triggered)
  - PROCESSING → "Kargoya Ver" button → modal (carrier + tracking) → POST `/shipped`
  - SHIPPED → "Teslim Edildi" → POST `/delivered`
- Her butonun yanında **email side-effect hint**: "📧 Kargo bildirim maili gönderilir"

User-visible hint demonstrate eder: admin click → backend transition → RabbitMQ → notification-service
→ email. Demo'da bu chain'i göstermek bootcamp grader için açık.

---

## 8. CRUD Pages — Pattern

Products, Categories, Coupons hepsi aynı pattern:

```jsx
function ProductsPage() {
    const [items, setItems] = useState([]);
    const [editing, setEditing] = useState(null);     // null | {} (new) | item (edit)
    const [confirmDelete, setConfirmDelete] = useState(null);
    
    const refresh = useCallback(() => {
        api.get('/api/products?...').then((res) => setItems(res.data?.content));
    }, [filters]);
    
    return (
        <>
            <Toolbar onNew={() => setEditing({})} onSearch={...} />
            <Table items={items} onEdit={setEditing} onDelete={setConfirmDelete} />
            
            {editing && (
                <ProductFormModal
                    product={editing.id ? editing : null}
                    onClose={() => setEditing(null)}
                    onSaved={refresh}
                />
            )}
            
            {confirmDelete && <DeleteConfirmModal item={confirmDelete} ... />}
        </>
    );
}
```

### `ProductFormModal` — Single Form for Create + Edit

```jsx
const isEdit = Boolean(product?.id);
// ...
async function onSubmit(e) {
    if (isEdit) await api.put(`/api/products/${product.id}`, body);
    else        await api.post('/api/products', body);
    onSaved();
    onClose();
}
```

Same form, same payload — POST vs PUT distinguishes intent.

### Auto-Slugify (TR Diakritik)

```js
function slugify(s) {
    return (s || '')
        .toLowerCase()
        .replace(/[üÜ]/g, 'u').replace(/[öÖ]/g, 'o').replace(/[şŞ]/g, 's')
        .replace(/[ıİ]/g, 'i').replace(/[çÇ]/g, 'c').replace(/[ğĞ]/g, 'g')
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/^-+|-+$/g, '');
}

function update(patch) {
    setDraft((d) => {
        const next = { ...d, ...patch };
        if ('name' in patch && !slugTouched && !isEdit) {
            next.slug = slugify(patch.name);
        }
        return next;
    });
}
```

User name yazar, slug otomatik üretilir. Slug field'ı manuel düzenlerse `slugTouched=true`
toggle olur, name değişiminde slug otomatik update olmaz (saygı).

Edit mode'da otomatik slugify devre dışı — admin'in mevcut slug'ı yanlışlıkla bozulmasın.

---

## 9. Users Page — Self-Demote Korumalı

```jsx
{users.map((u) => {
    const isSelf = me?.id === u.id;
    const isAdmin = u.role === 'ADMIN';
    return (
        <tr key={u.id} className={clsx('hover:bg-slate-50', isSelf && 'bg-brand-50/30')}>
            <td>
                <p>{u.fullName}
                    {isSelf && <span>Sen</span>}
                </p>
                <p>{u.email}</p>
            </td>
            <td>
                {isAdmin ? (
                    <button
                        onClick={() => setRole(u, 'demote')}
                        disabled={isSelf || busyId === u.id}
                        title={isSelf ? 'Kendi rolünü düşüremezsin' : 'USER yap'}>
                        Yetkiyi Kaldır
                    </button>
                ) : (
                    <button onClick={() => setRole(u, 'promote')}>ADMIN Yap</button>
                )}
            </td>
        </tr>
    );
})}
```

`isSelf` row highlight + "Sen" badge + demote button **disabled**. Backend zaten 409 atar
(self-demote guard) ama client-side disable = visual confirmation.

---

## 10. axios Interceptors — 401 Hard-Reset

```js
api.interceptors.response.use(
    (res) => res,
    (err) => {
        const status = err.response?.status;
        if (status === 401 && err.config?.url && !err.config.url.includes('/api/auth/login')) {
            tokenStore.clear();
            window.location.assign('/login');     // hard nav, route guards re-evaluate
        } else if (status === 403) {
            toast.error('Bu işlem için yetkin yok.');
        } else if (status >= 500) {
            toast.error('Sunucu hatası, biraz sonra tekrar dene.');
        }
        return Promise.reject(err);
    },
);
```

Niye hard-nav (`window.location.assign`) vs `navigate('/login')`:
- `navigate` SPA route change, AdminGuard yine çalışır ama auth context cache'i temizlenmiş olabilir.
- Hard nav full reload → providers fresh state'le başlar → garantili clean state.

Niye admin panelde refresh-token rotation yok (storefront'taki gibi):
- Bootcamp scope'unda admin session daha kısa (admin login → iş → çıkış).
- 401 → hard-logout. Admin reload login eder.
- Kompleksite vs benefit dengesinde simplicity kazanıyor.

Ileride admin için de single-flight refresh eklenebilir; pattern aynı (storefront'tan
kopyala).

---

## 11. Build & Deploy

`Dockerfile`:
```dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json ./
COPY package-lock.json* ./
RUN npm ci --no-audit --no-fund || npm install --no-audit --no-fund
COPY . .
ARG VITE_API_BASE_URL=
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

`nginx.conf` storefront'la aynı pattern — `/api/*` proxy + SPA fallback.

`docker-compose.yml`:
```yaml
frontend-admin:
  build:
    context: ./frontend-admin
    args:
      VITE_API_BASE_URL: ""
  ports: ["3001:80"]
```

Aynı backend, ayrı container, port 3001.

CORS_ALLOWED_ORIGINS: gateway env'inde `http://localhost:3001` listede — Vite dev mode (5174)
direkt cross-origin gerekirse de.

---

## 12. Bilinçli Olarak Yapmadıklarımız

- **Refresh token rotation**: Storefront'taki single-flight pattern admin'de yok. 401 →
  hard logout. Daha basit; admin session'ları kısa.
- **Bulk action**: "Birden fazla ürün seç + sil" gibi multi-row action yok. Tek-tek.
- **Export (CSV, Excel)**: Sipariş/ürün liste export'u yok.
- **Audit log UI**: Backend log'unda var ama UI'da gösterilmiyor. Future feature.
- **i18n**: Sadece Türkçe.
- **Dark mode**: Hayır.
- **Error boundary fancy**: Generic toast'lar.

---

## 13. Klasör Yapısı

```
frontend-admin/
├── package.json, Dockerfile, nginx.conf, vite.config.js, tailwind.config.js, postcss.config.js
├── index.html
└── src/
    ├── main.jsx, App.jsx
    ├── api/client.js                 # n11.admin.* localStorage namespace
    ├── state/
    │   └── AuthContext.jsx           # ADMIN-only login filter
    ├── components/
    │   ├── AdminGuard.jsx
    │   ├── AdminLayout.jsx           # sidebar + lucide icons
    │   ├── StatusBadge.jsx
    │   ├── OrderDetailDrawer.jsx
    │   ├── ProductFormModal.jsx, CategoryFormModal.jsx, CouponFormModal.jsx
    ├── pages/
    │   ├── DashboardPage.jsx         # recharts KPI dashboard
    │   ├── LoginPage.jsx
    │   ├── OrdersPage.jsx
    │   ├── ProductsPage.jsx
    │   ├── CategoriesPage.jsx
    │   ├── CouponsPage.jsx
    │   └── UsersPage.jsx
    ├── utils/format.js
    └── styles/index.css              # @layer components: .input/.btn-primary/.btn-secondary/.btn-danger/.card
```

---

## İlgili Dokümanlar

- [`docs/security.md`](../security.md) — Auth + role-based access
- [`docs/services/order-service.md`](order-service.md) — Lifecycle endpoint backend
- [`docs/services/product-service.md`](product-service.md) — CRUD + metrics backend
- [`docs/services/cart-service.md`](cart-service.md) — Coupon CRUD backend
- [`docs/services/auth-service.md`](auth-service.md) — Users promote/demote backend
- [`docs/services/frontend.md`](frontend.md) — Sister storefront SPA
