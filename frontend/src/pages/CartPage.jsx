import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { api } from '../api/client.js';
import { useCart } from '../state/CartContext.jsx';
import { formatCurrency } from '../utils/format.js';

export default function CartPage() {
  const { cart, refresh, updateQuantity, removeItem, applyCoupon, clearCoupon, isGuest } = useCart();
  const [checkoutLoading, setCheckoutLoading] = useState(false);
  const [couponInput, setCouponInput] = useState('');
  const [couponLoading, setCouponLoading] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();

  async function onCheckout() {
    if (isGuest) {
      navigate('/login', { state: { from: location } });
      return;
    }
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

  async function onApplyCoupon(e) {
    e.preventDefault();
    if (!couponInput.trim()) return;
    setCouponLoading(true);
    try {
      await applyCoupon(couponInput.trim().toUpperCase());
      setCouponInput('');
    } catch {
      // toast already shown
    } finally {
      setCouponLoading(false);
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
        {isGuest && <GuestBanner />}
        {cart.items.map((item) => (
          <article key={item.id} className="card flex gap-4 p-3">
            <div className="h-24 w-24 overflow-hidden rounded bg-gray-100">
              {item.imageUrl && <img src={item.imageUrl} alt={item.productName} className="h-full w-full object-cover" />}
            </div>
            <div className="flex flex-1 flex-col justify-between">
              <div className="flex items-start justify-between gap-3">
                <h3 className="text-sm font-medium text-gray-800">{item.productName}</h3>
                <button onClick={() => removeItem(item.id)} className="text-xs text-gray-400 hover:text-red-500">
                  Kaldır
                </button>
              </div>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2 text-sm text-gray-500">
                  <button
                    className="rounded border border-gray-200 px-2 hover:bg-gray-50"
                    onClick={() => updateQuantity(item.id, Math.max(1, item.quantity - 1))}
                  >
                    −
                  </button>
                  <span className="w-8 text-center font-medium">{item.quantity}</span>
                  <button
                    className="rounded border border-gray-200 px-2 hover:bg-gray-50"
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

        {isGuest ? (
          <CouponLoginLock />
        ) : (
          <CouponBlock
            cart={cart}
            couponInput={couponInput}
            setCouponInput={setCouponInput}
            couponLoading={couponLoading}
            onApply={onApplyCoupon}
            onClear={clearCoupon}
          />
        )}

        <dl className="space-y-2 text-sm text-gray-600">
          <div className="flex justify-between">
            <dt>Ürün adedi</dt>
            <dd>{cart.totalQuantity}</dd>
          </div>
          <div className="flex justify-between">
            <dt>Ara toplam</dt>
            <dd>{formatCurrency(cart.subtotal ?? cart.totalAmount, cart.currency)}</dd>
          </div>

          {cart.discounts?.map((d) => (
            <div key={`${d.kind}:${d.code}`} className="flex justify-between text-emerald-600">
              <dt className="flex items-center gap-1.5">
                <DiscountBadge kind={d.kind} />
                <span>{d.label}</span>
              </dt>
              <dd>-{formatCurrency(d.amount, cart.currency)}</dd>
            </div>
          ))}

          <div className="flex justify-between">
            <dt>Kargo</dt>
            <dd>Ücretsiz</dd>
          </div>
          <div className="flex justify-between border-t border-gray-200 pt-2 text-base font-semibold text-gray-900">
            <dt>Toplam</dt>
            <dd>{formatCurrency(cart.totalAmount, cart.currency)}</dd>
          </div>
        </dl>
        <button onClick={onCheckout} disabled={checkoutLoading} className="btn-primary w-full">
          {checkoutLoading
            ? 'Sipariş oluşturuluyor…'
            : isGuest
            ? 'Devam etmek için giriş yap'
            : 'Siparişi Tamamla'}
        </button>
      </aside>
    </div>
  );
}

function GuestBanner() {
  return (
    <div className="rounded-md border border-n11-pink/30 bg-n11-pinkBg/40 p-3 text-sm">
      <p className="font-medium text-n11-pinkDark">Misafir olarak alışveriş yapıyorsun</p>
      <p className="mt-0.5 text-xs text-gray-600">
        Sepetin tarayıcında saklanıyor.{' '}
        <Link to="/login" className="font-medium text-n11-pink hover:underline">
          Giriş yap
        </Link>{' '}
        ya da{' '}
        <Link to="/register" className="font-medium text-n11-pink hover:underline">
          üye ol
        </Link>{' '}
        — sepetin otomatik aktarılır, kupon ve kampanyalar uygulanır.
      </p>
    </div>
  );
}

function CouponLoginLock() {
  return (
    <div className="rounded-md border border-gray-200 bg-gray-50 p-3 text-xs text-gray-500">
      <p className="font-medium text-gray-700">Kupon kodu için giriş yap</p>
      <p className="mt-0.5">Kuponlar ve kampanya indirimleri sipariş özetine giriş sonrası eklenir.</p>
    </div>
  );
}

function CouponBlock({ cart, couponInput, setCouponInput, couponLoading, onApply, onClear }) {
  if (cart.couponCode) {
    return (
      <div className="rounded-md border border-emerald-200 bg-emerald-50 p-3 text-sm">
        <div className="flex items-center justify-between">
          <span className="font-medium text-emerald-700">Kupon: {cart.couponCode}</span>
          <button onClick={onClear} className="text-xs text-emerald-700 hover:underline">
            Kaldır
          </button>
        </div>
      </div>
    );
  }
  return (
    <form onSubmit={onApply} className="space-y-1">
      <label htmlFor="coupon" className="block text-xs font-medium text-gray-500">
        Kupon kodun var mı?
      </label>
      <div className="flex gap-2">
        <input
          id="coupon"
          type="text"
          placeholder="KUPON100"
          value={couponInput}
          onChange={(e) => setCouponInput(e.target.value)}
          className="input flex-1 uppercase"
          maxLength={40}
        />
        <button
          type="submit"
          disabled={couponLoading || !couponInput.trim()}
          className="rounded bg-n11-pink px-3 text-sm font-medium text-white disabled:opacity-50"
        >
          {couponLoading ? '…' : 'Uygula'}
        </button>
      </div>
    </form>
  );
}

function DiscountBadge({ kind }) {
  const isCoupon = kind === 'COUPON';
  return (
    <span
      className={`rounded px-1.5 py-0.5 text-[10px] font-bold uppercase ${
        isCoupon ? 'bg-emerald-100 text-emerald-700' : 'bg-amber-100 text-amber-700'
      }`}
    >
      {isCoupon ? 'Kupon' : 'Kampanya'}
    </span>
  );
}
