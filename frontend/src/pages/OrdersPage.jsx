import { useEffect, useRef, useState } from 'react';
import { motion } from 'framer-motion';
import {
  Check,
  Clock,
  CreditCard,
  Package,
  Truck,
  Home,
  XCircle,
  MapPin,
} from 'lucide-react';
import { api } from '../api/client.js';
import { formatCurrency } from '../utils/format.js';

const STATUS_LABELS = {
  PENDING: 'İşleniyor',
  AWAITING_PAYMENT: 'Ödeme bekleniyor',
  CONFIRMED: 'Onaylandı',
  PROCESSING: 'Hazırlanıyor',
  SHIPPED: 'Kargoda',
  DELIVERED: 'Teslim edildi',
  CANCELLED: 'İptal edildi',
};

const STATUS_TONES = {
  PENDING: 'bg-gray-100 text-gray-700',
  AWAITING_PAYMENT: 'bg-amber-100 text-amber-800',
  CONFIRMED: 'bg-blue-100 text-blue-800',
  PROCESSING: 'bg-purple-100 text-purple-800',
  SHIPPED: 'bg-indigo-100 text-indigo-800',
  DELIVERED: 'bg-emerald-100 text-emerald-800',
  CANCELLED: 'bg-red-100 text-red-700',
};

// Active state (still moving) keeps polling — terminal states stop.
const ACTIVE_STATUSES = new Set(['PENDING', 'AWAITING_PAYMENT', 'CONFIRMED', 'PROCESSING', 'SHIPPED']);

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
      const stillMoving = ordersRef.current.some((o) => ACTIVE_STATUSES.has(o.status));
      if (stillMoving) load();
    }, 4000);

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
        <OrderCard key={order.id} order={order} />
      ))}
    </div>
  );
}

function OrderCard({ order }) {
  return (
    <motion.article
      layout
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      className="card space-y-4 p-4"
    >
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-sm text-gray-500">Sipariş #{order.id}</p>
          <p className="text-xs text-gray-400">{new Date(order.createdAt).toLocaleString('tr-TR')}</p>
        </div>
        <span className={`rounded-full px-3 py-1 text-xs font-semibold ${STATUS_TONES[order.status] || ''}`}>
          {STATUS_LABELS[order.status] || order.status}
        </span>
      </header>

      {order.status !== 'CANCELLED' && <Timeline timeline={order.timeline} status={order.status} />}

      {order.shipping && <ShippingBlock shipping={order.shipping} tracking={order.tracking} />}

      <ul className="divide-y divide-gray-100 text-sm">
        {order.items.map((item) => (
          <li key={item.productId + ':' + item.unitPrice} className="flex justify-between py-2">
            <span>{item.productName} × {item.quantity}</span>
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
    </motion.article>
  );
}

function ShippingBlock({ shipping, tracking }) {
  return (
    <div className="rounded-md border border-gray-100 bg-gray-50 p-3 text-xs">
      <div className="flex items-center gap-2 font-medium text-gray-700">
        <MapPin className="h-3.5 w-3.5 text-n11-pink" /> Teslimat
      </div>
      <p className="mt-1 text-gray-600">{shipping.recipient} · {shipping.phone}</p>
      <p className="text-gray-600">
        {shipping.line1}, {[shipping.district, shipping.city, shipping.postalCode].filter(Boolean).join(' / ')}
      </p>
      {tracking?.trackingNumber && (
        <p className="mt-1 flex items-center gap-1 font-medium text-indigo-700">
          <Truck className="h-3.5 w-3.5" />
          {tracking.carrier ? `${tracking.carrier} · ` : ''}#{tracking.trackingNumber}
        </p>
      )}
    </div>
  );
}

function Timeline({ timeline, status }) {
  if (!timeline) return null;
  const steps = [
    { key: 'placed',     label: 'Oluşturuldu', icon: Clock,      at: timeline.placedAt },
    { key: 'confirmed',  label: 'Ödeme alındı', icon: CreditCard, at: timeline.confirmedAt },
    { key: 'processing', label: 'Hazırlanıyor', icon: Package,    at: timeline.processingAt },
    { key: 'shipped',    label: 'Kargoda',      icon: Truck,      at: timeline.shippedAt },
    { key: 'delivered',  label: 'Teslim',       icon: Home,       at: timeline.deliveredAt },
  ];

  // A step is "done" if it has a timestamp; "current" if it's the latest done one
  // and the order isn't fully delivered/cancelled yet.
  const lastDoneIdx = steps.reduce((acc, s, i) => (s.at ? i : acc), -1);
  const cancelled = status === 'CANCELLED';

  return (
    <div className="overflow-x-auto">
      <ol className="flex min-w-max items-start gap-2">
        {steps.map((step, i) => {
          const done = !!step.at;
          const current = i === lastDoneIdx && status !== 'DELIVERED' && !cancelled;
          const Icon = cancelled && i > 0 && !done ? XCircle : step.icon;
          return (
            <li key={step.key} className="flex flex-1 items-start gap-2">
              <div className="flex flex-col items-center">
                <div
                  className={`relative grid h-8 w-8 place-items-center rounded-full border-2 transition ${
                    done
                      ? 'border-emerald-500 bg-emerald-500 text-white'
                      : current
                      ? 'border-n11-pink bg-white text-n11-pink'
                      : 'border-gray-200 bg-white text-gray-300'
                  }`}
                >
                  {done ? <Check className="h-4 w-4" strokeWidth={3} /> : <Icon className="h-4 w-4" />}
                  {current && (
                    <motion.span
                      className="absolute inset-0 rounded-full border-2 border-n11-pink"
                      animate={{ scale: [1, 1.4], opacity: [0.7, 0] }}
                      transition={{ duration: 1.4, repeat: Infinity, ease: 'easeOut' }}
                    />
                  )}
                </div>
                <p className={`mt-1 text-[10px] font-medium ${done || current ? 'text-gray-700' : 'text-gray-400'}`}>
                  {step.label}
                </p>
                {step.at && (
                  <p className="text-[9px] text-gray-400">{new Date(step.at).toLocaleString('tr-TR', {
                    day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit',
                  })}</p>
                )}
              </div>
              {i < steps.length - 1 && (
                <div
                  className={`mt-3 h-0.5 flex-1 ${
                    steps[i + 1].at ? 'bg-emerald-500' : 'bg-gray-200'
                  }`}
                />
              )}
            </li>
          );
        })}
      </ol>
    </div>
  );
}
