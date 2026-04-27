import { useEffect, useRef, useState } from 'react';
import { api } from '../api/client.js';
import { formatCurrency } from '../utils/format.js';

const STATUS_LABELS = {
  PENDING: 'İşleniyor',
  AWAITING_PAYMENT: 'Ödeme bekleniyor',
  CONFIRMED: 'Onaylandı',
  CANCELLED: 'İptal edildi',
};

const STATUS_TONES = {
  PENDING: 'bg-gray-100 text-gray-700',
  AWAITING_PAYMENT: 'bg-amber-100 text-amber-800',
  CONFIRMED: 'bg-emerald-100 text-emerald-800',
  CANCELLED: 'bg-red-100 text-red-700',
};

export default function OrdersPage() {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const ordersRef = useRef(orders);
  ordersRef.current = orders;

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        const { data } = await api.get('/api/orders');
        if (!cancelled) setOrders(data);
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    load();

    const interval = setInterval(() => {
      const stillPending = ordersRef.current.some(
        (o) => o.status === 'AWAITING_PAYMENT' || o.status === 'PENDING',
      );
      if (stillPending) load();
    }, 2500);

    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, []);

  if (loading) return <div className="card h-32 animate-pulse bg-gray-100" />;
  if (orders.length === 0) return <p className="text-gray-500">Henüz sipariş vermedin.</p>;

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-semibold tracking-tight">Siparişlerim</h1>
      {orders.map((order) => (
        <article key={order.id} className="card space-y-3 p-4">
          <header className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="text-sm text-gray-500">Sipariş #{order.id}</p>
              <p className="text-xs text-gray-400">{new Date(order.createdAt).toLocaleString('tr-TR')}</p>
            </div>
            <span className={`rounded-full px-3 py-1 text-xs font-semibold ${STATUS_TONES[order.status]}`}>
              {STATUS_LABELS[order.status] || order.status}
            </span>
          </header>

          <ul className="divide-y divide-gray-100 text-sm">
            {order.items.map((item) => (
              <li key={item.productId + ':' + item.unitPrice} className="flex justify-between py-2">
                <span>
                  {item.productName} × {item.quantity}
                </span>
                <span className="font-medium">{formatCurrency(item.lineTotal, order.currency)}</span>
              </li>
            ))}
          </ul>

          <footer className="flex items-center justify-between border-t border-gray-100 pt-2 text-sm font-medium">
            <span className="text-gray-500">Toplam</span>
            <span className="text-base">{formatCurrency(order.totalAmount, order.currency)}</span>
          </footer>

          {order.status === 'CANCELLED' && order.failureReason && (
            <p className="rounded bg-red-50 p-2 text-xs text-red-600">İptal nedeni: {order.failureReason}</p>
          )}
        </article>
      ))}
    </div>
  );
}
