import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ResponsiveContainer,
  LineChart,
  Line,
  BarChart,
  Bar,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  Legend,
} from 'recharts';
import {
  ShoppingBag, Package, Tags, FolderTree, Users, AlertTriangle, TrendingUp, ArrowUpRight,
} from 'lucide-react';
import { api } from '../api/client.js';
import { formatCurrency, formatDate } from '../utils/format.js';

// Brand-friendly chart palette — picks from the indigo / fuchsia / amber /
// emerald families so charts read well on the white card background.
const CHART_COLORS = ['#6366f1', '#8b5cf6', '#ec4899', '#f59e0b', '#10b981', '#0ea5e9', '#f43f5e', '#94a3b8'];

const STATUS_LABEL_TR = {
  PENDING: 'Beklemede',
  AWAITING_PAYMENT: 'Ödeme Bekliyor',
  CONFIRMED: 'Onaylandı',
  PROCESSING: 'Hazırlanıyor',
  SHIPPED: 'Kargoda',
  DELIVERED: 'Teslim Edildi',
  CANCELLED: 'İptal',
};

export default function DashboardPage() {
  const [orderMetrics, setOrderMetrics] = useState(null);
  const [productMetrics, setProductMetrics] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    Promise.all([
      api.get('/api/orders/admin/metrics?days=30').then((r) => r.data).catch(() => null),
      api.get('/api/products/admin/metrics?lowStockThreshold=10').then((r) => r.data).catch(() => null),
    ])
      .then(([om, pm]) => {
        setOrderMetrics(om);
        setProductMetrics(pm);
      })
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Anasayfa</h1>
        <p className="text-sm text-slate-500">Son 30 günün özeti.</p>
      </div>

      {/* Summary cards */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <SummaryCard
          icon={ShoppingBag}
          tone="brand"
          title="Bugün Sipariş"
          value={orderMetrics?.summary?.todayOrders ?? '—'}
          subtitle="Yeni gelen"
          loading={loading}
        />
        <SummaryCard
          icon={TrendingUp}
          tone="emerald"
          title="Bugün Ciro"
          value={orderMetrics ? formatCurrency(orderMetrics.summary.todayRevenue, 'TRY') : '—'}
          subtitle="Onaylı + üzeri"
          loading={loading}
        />
        <SummaryCard
          icon={Tags}
          tone="amber"
          title="Bekleyen Sipariş"
          value={orderMetrics?.summary?.pendingOrders ?? '—'}
          subtitle="CONFIRMED durumunda"
          loading={loading}
        />
        <SummaryCard
          icon={AlertTriangle}
          tone="rose"
          title="Düşük Stok"
          value={productMetrics?.lowStockCount ?? '—'}
          subtitle={`Eşik: ${productMetrics?.lowStockThreshold ?? 10}`}
          loading={loading}
        />
      </div>

      {/* Charts row 1: orders/day + revenue */}
      <div className="grid gap-4 lg:grid-cols-2">
        <ChartCard title="Son 30 gün — Sipariş sayısı">
          <ResponsiveContainer width="100%" height={260}>
            <LineChart data={(orderMetrics?.daily || []).map((d) => ({ ...d, dateLabel: shortDate(d.date) }))}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="dateLabel" tick={{ fontSize: 11, fill: '#64748b' }} />
              <YAxis tick={{ fontSize: 11, fill: '#64748b' }} allowDecimals={false} />
              <Tooltip contentStyle={{ fontSize: 12, borderRadius: 8 }} />
              <Line type="monotone" dataKey="orderCount" stroke="#6366f1" strokeWidth={2} dot={false} name="Sipariş" />
            </LineChart>
          </ResponsiveContainer>
        </ChartCard>

        <ChartCard title="Son 30 gün — Ciro">
          <ResponsiveContainer width="100%" height={260}>
            <BarChart data={(orderMetrics?.daily || []).map((d) => ({ ...d, dateLabel: shortDate(d.date) }))}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="dateLabel" tick={{ fontSize: 11, fill: '#64748b' }} />
              <YAxis tick={{ fontSize: 11, fill: '#64748b' }} tickFormatter={(v) => v >= 1000 ? `${(v / 1000).toFixed(0)}k` : v} />
              <Tooltip contentStyle={{ fontSize: 12, borderRadius: 8 }} formatter={(v) => formatCurrency(v, 'TRY')} />
              <Bar dataKey="revenue" fill="#10b981" radius={[4, 4, 0, 0]} name="Ciro" />
            </BarChart>
          </ResponsiveContainer>
        </ChartCard>
      </div>

      {/* Charts row 2: status donut + top categories */}
      <div className="grid gap-4 lg:grid-cols-2">
        <ChartCard title="Sipariş durum dağılımı">
          <ResponsiveContainer width="100%" height={260}>
            <PieChart>
              <Pie
                data={(orderMetrics?.statusBreakdown || []).map((s) => ({
                  name: STATUS_LABEL_TR[s.status] || s.status,
                  value: s.count,
                }))}
                dataKey="value"
                nameKey="name"
                innerRadius={50}
                outerRadius={90}
                paddingAngle={2}
              >
                {(orderMetrics?.statusBreakdown || []).map((_, i) => (
                  <Cell key={i} fill={CHART_COLORS[i % CHART_COLORS.length]} />
                ))}
              </Pie>
              <Tooltip contentStyle={{ fontSize: 12, borderRadius: 8 }} />
              <Legend wrapperStyle={{ fontSize: 11 }} />
            </PieChart>
          </ResponsiveContainer>
        </ChartCard>

        <ChartCard title="Kategoriye göre ürün sayısı">
          <ResponsiveContainer width="100%" height={260}>
            <BarChart
              data={productMetrics?.topCategories || []}
              layout="vertical"
              margin={{ left: 24 }}
            >
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis type="number" tick={{ fontSize: 11, fill: '#64748b' }} allowDecimals={false} />
              <YAxis type="category" dataKey="name" tick={{ fontSize: 11, fill: '#64748b' }} width={100} />
              <Tooltip contentStyle={{ fontSize: 12, borderRadius: 8 }} />
              <Bar dataKey="productCount" fill="#8b5cf6" radius={[0, 4, 4, 0]} name="Ürün sayısı" />
            </BarChart>
          </ResponsiveContainer>
        </ChartCard>
      </div>

      {/* Low stock list */}
      <ChartCard title={`Düşük stok (≤ ${productMetrics?.lowStockThreshold ?? 10} adet)`}>
        {productMetrics?.lowStock?.length ? (
          <ul className="divide-y divide-slate-100">
            {productMetrics.lowStock.map((p) => (
              <li key={p.id} className="flex items-center justify-between py-2 text-sm">
                <div>
                  <p className="font-medium text-slate-800">{p.name}</p>
                  <p className="font-mono text-xs text-slate-500">{p.slug}</p>
                </div>
                <span className={`rounded-full px-2.5 py-0.5 text-xs font-bold ${p.stock === 0 ? 'bg-rose-100 text-rose-700' : 'bg-amber-100 text-amber-700'}`}>
                  {p.stock} adet
                </span>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm text-slate-400">Stoğu eşik altındaki ürün yok.</p>
        )}
      </ChartCard>

      {/* Quick links */}
      <div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-5">
        <ShortcutCard to="/orders" icon={ShoppingBag} title="Siparişler" />
        <ShortcutCard to="/products" icon={Package} title="Ürünler" />
        <ShortcutCard to="/categories" icon={FolderTree} title="Kategoriler" />
        <ShortcutCard to="/coupons" icon={Tags} title="Kuponlar" />
        <ShortcutCard to="/users" icon={Users} title="Kullanıcılar" />
      </div>

      {orderMetrics?.daily?.length > 0 && (
        <p className="text-[11px] text-slate-400">
          Veriler {formatDate(orderMetrics.daily[0].date)} – {formatDate(orderMetrics.daily.at(-1).date)} arasında.
        </p>
      )}
    </div>
  );
}

function SummaryCard({ icon: Icon, tone, title, value, subtitle, loading }) {
  const toneCls = {
    brand:   'bg-brand-50 text-brand-700',
    emerald: 'bg-emerald-50 text-emerald-700',
    amber:   'bg-amber-50 text-amber-700',
    rose:    'bg-rose-50 text-rose-700',
  }[tone] || 'bg-slate-50 text-slate-700';
  return (
    <div className="card p-4">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">{title}</p>
          {loading ? (
            <div className="mt-2 h-7 w-24 animate-pulse rounded bg-slate-100" />
          ) : (
            <p className="mt-1 text-2xl font-bold tracking-tight">{value}</p>
          )}
          <p className="text-[11px] text-slate-400">{subtitle}</p>
        </div>
        <div className={`grid h-9 w-9 place-items-center rounded-lg ${toneCls}`}>
          <Icon size={16} />
        </div>
      </div>
    </div>
  );
}

function ChartCard({ title, children }) {
  return (
    <div className="card p-5">
      <h3 className="mb-3 text-sm font-semibold tracking-tight text-slate-700">{title}</h3>
      {children}
    </div>
  );
}

function ShortcutCard({ to, icon: Icon, title }) {
  return (
    <Link to={to} className="card group flex items-center justify-between p-4 transition-shadow hover:shadow-md">
      <div className="flex items-center gap-2">
        <div className="grid h-8 w-8 place-items-center rounded-md bg-brand-50 text-brand-700">
          <Icon size={14} />
        </div>
        <p className="text-sm font-semibold">{title}</p>
      </div>
      <ArrowUpRight size={14} className="text-slate-400 transition-transform group-hover:translate-x-0.5 group-hover:-translate-y-0.5" />
    </Link>
  );
}

function shortDate(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}`;
}
