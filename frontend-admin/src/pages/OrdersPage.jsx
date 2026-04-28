import { useCallback, useEffect, useState } from 'react';
import { Eye, Mail } from 'lucide-react';
import { api } from '../api/client.js';
import StatusBadge, { STATUS_OPTIONS } from '../components/StatusBadge.jsx';
import OrderDetailDrawer from '../components/OrderDetailDrawer.jsx';
import { formatCurrency, formatDate, relativeTime } from '../utils/format.js';

export default function OrdersPage() {
  const [statusFilter, setStatusFilter] = useState('');
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState(null); // order id of opened drawer

  const refresh = useCallback(() => {
    setLoading(true);
    const params = new URLSearchParams({ size: '50' });
    if (statusFilter) params.set('status', statusFilter);
    api
      .get(`/api/orders/admin?${params.toString()}`)
      .then((res) => setOrders(res.data || []))
      .catch(() => setOrders([]))
      .finally(() => setLoading(false));
  }, [statusFilter]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Siparişler</h1>
          <p className="text-sm text-slate-500">
            Tüm kullanıcıların siparişleri. Durum geçişleri otomatik olarak müşteriye e-posta tetikler.
          </p>
        </div>
        <div className="flex flex-wrap gap-1.5">
          {STATUS_OPTIONS.map((o) => (
            <button
              key={o.value || 'all'}
              onClick={() => setStatusFilter(o.value)}
              className={`rounded-full border px-3 py-1 text-xs font-medium transition-colors ${
                statusFilter === o.value
                  ? 'border-brand-600 bg-brand-600 text-white'
                  : 'border-slate-300 bg-white text-slate-600 hover:bg-slate-50'
              }`}
            >
              {o.label}
            </button>
          ))}
        </div>
      </div>

      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
            <tr>
              <th className="px-4 py-3">#</th>
              <th className="px-4 py-3">Müşteri</th>
              <th className="px-4 py-3">Tutar</th>
              <th className="px-4 py-3">Durum</th>
              <th className="px-4 py-3">Tarih</th>
              <th className="px-4 py-3 text-right">Aksiyon</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {loading ? (
              Array.from({ length: 6 }).map((_, i) => (
                <tr key={i} className="animate-pulse">
                  <td className="px-4 py-3"><div className="h-3 w-10 rounded bg-slate-100" /></td>
                  <td className="px-4 py-3"><div className="h-3 w-32 rounded bg-slate-100" /></td>
                  <td className="px-4 py-3"><div className="h-3 w-20 rounded bg-slate-100" /></td>
                  <td className="px-4 py-3"><div className="h-5 w-24 rounded-full bg-slate-100" /></td>
                  <td className="px-4 py-3"><div className="h-3 w-24 rounded bg-slate-100" /></td>
                  <td className="px-4 py-3"><div className="ml-auto h-3 w-12 rounded bg-slate-100" /></td>
                </tr>
              ))
            ) : orders.length === 0 ? (
              <tr>
                <td colSpan="6" className="px-4 py-12 text-center text-sm text-slate-400">
                  Bu filtreye uygun sipariş yok.
                </td>
              </tr>
            ) : (
              orders.map((o) => (
                <tr key={o.id} className="hover:bg-slate-50">
                  <td className="px-4 py-3 font-mono text-xs text-slate-600">#{o.id}</td>
                  <td className="px-4 py-3">
                    <p className="font-medium text-slate-800">{o.shipping?.recipient || o.userEmail}</p>
                    <p className="text-xs text-slate-500">{o.userEmail}</p>
                  </td>
                  <td className="px-4 py-3 font-semibold">{formatCurrency(o.totalAmount, o.currency)}</td>
                  <td className="px-4 py-3"><StatusBadge status={o.status} /></td>
                  <td className="px-4 py-3 text-slate-600">
                    <p>{formatDate(o.createdAt)}</p>
                    <p className="text-xs text-slate-400">{relativeTime(o.createdAt)}</p>
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button onClick={() => setSelected(o.id)} className="btn-secondary text-xs">
                      <Eye size={14} /> Detay
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <p className="flex items-center gap-2 text-xs text-slate-400">
        <Mail size={14} /> Hazırlanıyor / Kargoya Ver / Teslim Et durum geçişleri RabbitMQ üzerinden notification-service'e event gönderir; müşteriye otomatik e-posta atılır.
      </p>

      {selected && (
        <OrderDetailDrawer
          orderId={selected}
          onClose={() => setSelected(null)}
          onChanged={refresh}
        />
      )}
    </div>
  );
}
