import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { api } from '../api/client.js';
import { useCart } from '../state/CartContext.jsx';
import { formatCurrency } from '../utils/format.js';

export default function CartPage() {
  const { cart, refresh, updateQuantity, removeItem } = useCart();
  const [checkoutLoading, setCheckoutLoading] = useState(false);
  const navigate = useNavigate();

  async function onCheckout() {
    setCheckoutLoading(true);
    try {
      const { data } = await api.post('/api/orders/checkout');
      toast.success(`Siparişin oluşturuldu (#${data.id})`);
      await refresh();
      navigate('/orders');
    } catch (err) {
      toast.error(err.response?.data?.message || 'Ödeme başarısız');
    } finally {
      setCheckoutLoading(false);
    }
  }

  if (!cart.items || cart.items.length === 0) {
    return (
      <div className="card flex flex-col items-center gap-3 py-16 text-center">
        <p className="text-lg font-medium">Sepetin boş</p>
        <Link to="/" className="btn-primary">
          Alışverişe başla
        </Link>
      </div>
    );
  }

  return (
    <div className="grid gap-6 lg:grid-cols-3">
      <div className="space-y-3 lg:col-span-2">
        {cart.items.map((item) => (
          <article key={item.id} className="card flex gap-4 p-3">
            <div className="h-24 w-24 overflow-hidden rounded bg-slate-100">
              {item.imageUrl && <img src={item.imageUrl} alt={item.productName} className="h-full w-full object-cover" />}
            </div>
            <div className="flex flex-1 flex-col justify-between">
              <div className="flex items-start justify-between gap-3">
                <h3 className="text-sm font-medium text-slate-800">{item.productName}</h3>
                <button onClick={() => removeItem(item.id)} className="text-xs text-slate-400 hover:text-red-500">
                  Kaldır
                </button>
              </div>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2 text-sm text-slate-500">
                  <button
                    className="rounded border border-slate-200 px-2 hover:bg-slate-50"
                    onClick={() => updateQuantity(item.id, Math.max(1, item.quantity - 1))}
                  >
                    −
                  </button>
                  <span className="w-8 text-center font-medium">{item.quantity}</span>
                  <button
                    className="rounded border border-slate-200 px-2 hover:bg-slate-50"
                    onClick={() => updateQuantity(item.id, item.quantity + 1)}
                  >
                    +
                  </button>
                </div>
                <span className="text-sm font-semibold">{formatCurrency(item.lineTotal, item.currency)}</span>
              </div>
            </div>
          </article>
        ))}
      </div>

      <aside className="card sticky top-4 h-fit space-y-4 p-4">
        <h2 className="text-lg font-semibold">Sipariş Özeti</h2>
        <dl className="space-y-2 text-sm text-slate-600">
          <div className="flex justify-between">
            <dt>Ürün adedi</dt>
            <dd>{cart.totalQuantity}</dd>
          </div>
          <div className="flex justify-between">
            <dt>Ara toplam</dt>
            <dd>{formatCurrency(cart.totalAmount, cart.currency)}</dd>
          </div>
          <div className="flex justify-between">
            <dt>Kargo</dt>
            <dd>Ücretsiz</dd>
          </div>
          <div className="flex justify-between border-t border-slate-200 pt-2 text-base font-semibold text-slate-900">
            <dt>Toplam</dt>
            <dd>{formatCurrency(cart.totalAmount, cart.currency)}</dd>
          </div>
        </dl>
        <button onClick={onCheckout} disabled={checkoutLoading} className="btn-primary w-full">
          {checkoutLoading ? 'Sipariş oluşturuluyor…' : 'Siparişi Tamamla'}
        </button>
      </aside>
    </div>
  );
}
