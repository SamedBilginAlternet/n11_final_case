import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { X, Truck, CheckCircle2, PackageCheck, Mail } from 'lucide-react';
import { api } from '../api/client.js';
import StatusBadge from './StatusBadge.jsx';
import { formatCurrency, formatDate } from '../utils/format.js';

/**
 * Right-side drawer that opens on order row click.
 *
 * <p>Shows the full order detail (items, shipping address, timeline) plus
 * action buttons that fire the lifecycle transitions:
 *   CONFIRMED → İşleniyor (POST /processing)
 *   PROCESSING → Kargoya Ver (POST /shipped, requires carrier + tracking #)
 *   SHIPPED → Teslim Et (POST /delivered)
 * Each successful transition emits a saga event that notification-service
 * picks up and mails the customer — that's the whole point of the demo,
 * so we surface that side effect prominently in the UI.</p>
 */
export default function OrderDetailDrawer({ orderId, onClose, onChanged }) {
  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [shipModal, setShipModal] = useState(null);

  useEffect(() => {
    setLoading(true);
    api
      .get(`/api/orders/admin/${orderId}`)
      .then((res) => setOrder(res.data))
      .catch(() => toast.error('Sipariş yüklenemedi'))
      .finally(() => setLoading(false));
  }, [orderId]);

  async function transition(path, body) {
    setBusy(true);
    try {
      const { data } = await api.post(`/api/orders/${orderId}/${path}`, body);
      setOrder(data);
      onChanged?.();
      toast.success(transitionToast(path));
    } catch (err) {
      toast.error(err.response?.data?.message || 'Geçiş başarısız');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="fixed inset-0 z-40">
      <div className="absolute inset-0 bg-slate-900/30" onClick={onClose} />
      <aside className="absolute right-0 top-0 flex h-full w-full max-w-xl flex-col overflow-y-auto bg-white shadow-xl">
        <header className="sticky top-0 z-10 flex items-center justify-between border-b border-slate-200 bg-white px-5 py-3">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">Sipariş</p>
            <h2 className="text-lg font-bold tracking-tight">#{orderId}</h2>
          </div>
          <button onClick={onClose} className="rounded-md p-1.5 text-slate-400 hover:bg-slate-100">
            <X size={18} />
          </button>
        </header>

        {loading ? (
          <div className="space-y-3 p-5">
            <div className="h-5 w-32 animate-pulse rounded bg-slate-100" />
            <div className="h-32 animate-pulse rounded-md bg-slate-100" />
            <div className="h-24 animate-pulse rounded-md bg-slate-100" />
          </div>
        ) : !order ? (
          <p className="p-5 text-sm text-slate-500">Sipariş bulunamadı.</p>
        ) : (
          <div className="space-y-5 p-5">
            <section className="flex items-center justify-between">
              <StatusBadge status={order.status} />
              <span className="text-xs text-slate-500">{formatDate(order.createdAt)}</span>
            </section>

            <ActionPanel order={order} busy={busy} onProcess={() => transition('processing')}
                         onShip={() => setShipModal({ carrier: '', trackingNumber: '' })}
                         onDeliver={() => transition('delivered')} />

            <Section title="Müşteri">
              <p className="text-sm font-semibold">{order.shipping?.recipient || '—'}</p>
              <p className="text-sm text-slate-600">{order.userEmail}</p>
              {order.shipping?.phone && <p className="text-sm text-slate-600">{order.shipping.phone}</p>}
            </Section>

            {order.shipping && (
              <Section title="Teslimat Adresi">
                <p className="text-sm text-slate-700">
                  {order.shipping.line1}
                  <br />
                  {order.shipping.district} / {order.shipping.city} {order.shipping.postalCode}
                </p>
              </Section>
            )}

            {order.tracking?.trackingNumber && (
              <Section title="Kargo">
                <p className="text-sm text-slate-700">
                  {order.tracking.carrier} ·{' '}
                  <span className="font-mono">{order.tracking.trackingNumber}</span>
                </p>
              </Section>
            )}

            <Section title="Ürünler">
              <ul className="divide-y divide-slate-100 text-sm">
                {order.items.map((it) => (
                  <li key={it.productId} className="flex items-center justify-between py-2">
                    <div>
                      <p className="font-medium text-slate-800">{it.productName}</p>
                      <p className="text-xs text-slate-500">{it.quantity} × {formatCurrency(it.unitPrice, order.currency)}</p>
                    </div>
                    <span className="font-semibold">
                      {formatCurrency(Number(it.quantity) * Number(it.unitPrice), order.currency)}
                    </span>
                  </li>
                ))}
              </ul>
              <div className="mt-3 flex items-center justify-between border-t border-slate-200 pt-3">
                <span className="text-sm text-slate-500">Toplam</span>
                <span className="text-base font-bold">{formatCurrency(order.totalAmount, order.currency)}</span>
              </div>
            </Section>

            <Section title="Zaman Çizelgesi">
              <Timeline t={order.timeline} />
            </Section>
          </div>
        )}
      </aside>

      {shipModal && (
        <ShipModal
          draft={shipModal}
          onChange={setShipModal}
          onCancel={() => setShipModal(null)}
          onSubmit={async () => {
            const body = { carrier: shipModal.carrier?.trim() || null, trackingNumber: shipModal.trackingNumber?.trim() || null };
            setShipModal(null);
            await transition('shipped', body);
          }}
        />
      )}
    </div>
  );
}

function ActionPanel({ order, busy, onProcess, onShip, onDeliver }) {
  const status = order.status;
  const buttons = [];
  if (status === 'CONFIRMED') {
    buttons.push(<ActionButton key="proc" onClick={onProcess} disabled={busy} icon={PackageCheck} label="Hazırlamaya Başla" hint="Müşteriye otomatik e-posta tetiklenmez" />);
  }
  if (status === 'PROCESSING') {
    buttons.push(<ActionButton key="ship" onClick={onShip} disabled={busy} icon={Truck} label="Kargoya Ver" hint="📧 Kargo bildirim maili gönderilir" tone="primary" />);
  }
  if (status === 'SHIPPED') {
    buttons.push(<ActionButton key="deliver" onClick={onDeliver} disabled={busy} icon={CheckCircle2} label="Teslim Edildi" hint="📧 Teslimat maili gönderilir" tone="primary" />);
  }
  if (buttons.length === 0) {
    return (
      <div className="rounded-md border border-slate-200 bg-slate-50 p-3 text-xs text-slate-500">
        Bu sipariş için aksiyon bulunmuyor.
      </div>
    );
  }
  return <div className="space-y-2">{buttons}</div>;
}

function ActionButton({ icon: Icon, label, hint, tone, ...rest }) {
  const cls = tone === 'primary' ? 'btn-primary' : 'btn-secondary';
  return (
    <div>
      <button {...rest} className={`${cls} w-full justify-start gap-2`}>
        <Icon size={16} /> {label}
      </button>
      <p className="mt-1 flex items-center gap-1 pl-1 text-[11px] text-slate-500">
        <Mail size={10} /> {hint}
      </p>
    </div>
  );
}

function Section({ title, children }) {
  return (
    <section>
      <h3 className="mb-2 text-xs font-bold uppercase tracking-wider text-slate-500">{title}</h3>
      <div>{children}</div>
    </section>
  );
}

function Timeline({ t }) {
  if (!t) return <p className="text-xs text-slate-400">Veri yok</p>;
  const rows = [
    ['Sipariş alındı', t.placedAt],
    ['Onaylandı', t.confirmedAt],
    ['Hazırlanıyor', t.processingAt],
    ['Kargoda', t.shippedAt],
    ['Teslim edildi', t.deliveredAt],
    ['İptal', t.cancelledAt],
  ].filter(([, ts]) => ts);
  return (
    <ul className="space-y-1.5 text-sm">
      {rows.map(([label, ts]) => (
        <li key={label} className="flex items-center justify-between">
          <span className="text-slate-700">{label}</span>
          <span className="text-xs text-slate-500">{formatDate(ts)}</span>
        </li>
      ))}
    </ul>
  );
}

function ShipModal({ draft, onChange, onCancel, onSubmit }) {
  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-slate-900/50 p-4">
      <div className="card w-full max-w-md p-6">
        <h3 className="text-lg font-bold tracking-tight">Kargoya Ver</h3>
        <p className="mt-1 text-xs text-slate-500">Kargo firması ve takip numarası girildiğinde müşteriye otomatik e-posta atılır.</p>
        <div className="mt-4 space-y-3">
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600">Kargo Firması</label>
            <input
              autoFocus
              className="input mt-1"
              placeholder="örn. Yurtiçi Kargo"
              value={draft.carrier}
              onChange={(e) => onChange({ ...draft, carrier: e.target.value })}
            />
          </div>
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-600">Takip Numarası</label>
            <input
              className="input mt-1 font-mono"
              placeholder="N11-..."
              value={draft.trackingNumber}
              onChange={(e) => onChange({ ...draft, trackingNumber: e.target.value })}
            />
          </div>
        </div>
        <div className="mt-5 flex items-center justify-end gap-2">
          <button onClick={onCancel} className="btn-secondary">İptal</button>
          <button onClick={onSubmit} className="btn-primary">
            <Truck size={14} /> Onayla & Mail Gönder
          </button>
        </div>
      </div>
    </div>
  );
}

function transitionToast(path) {
  switch (path) {
    case 'processing': return 'Sipariş hazırlanıyor olarak işaretlendi.';
    case 'shipped':    return '📧 Kargo maili gönderildi.';
    case 'delivered':  return '📧 Teslimat maili gönderildi.';
    default:           return 'Geçiş başarılı.';
  }
}
