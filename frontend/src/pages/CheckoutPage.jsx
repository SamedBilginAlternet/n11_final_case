import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import toast from 'react-hot-toast';
import { ChevronLeft, ChevronRight, CreditCard, Lock, Mail, MapPin, ShieldCheck } from 'lucide-react';
import { api, performRefresh, tokenStore } from '../api/client.js';
import { useAuth } from '../state/AuthContext.jsx';
import { useCart } from '../state/CartContext.jsx';
import { formatCurrency } from '../utils/format.js';
import CheckoutStepper from '../components/checkout/CheckoutStepper.jsx';
import AddressPicker from '../components/checkout/AddressPicker.jsx';
import CardForm, { EMPTY_CARD, isCardComplete } from '../components/checkout/CardForm.jsx';

const STEPS = ['Adres', 'Ödeme', 'Onay'];

export default function CheckoutPage() {
  const { cart, clearLocal } = useCart();
  const { user } = useAuth();
  const navigate = useNavigate();

  const [step, setStep] = useState(0);
  const [addresses, setAddresses] = useState([]);
  const [addressesLoaded, setAddressesLoaded] = useState(false);
  const [selectedAddressId, setSelectedAddressId] = useState(null);
  const [card, setCard] = useState(EMPTY_CARD);
  const [submitting, setSubmitting] = useState(false);

  // Phone-only users land here without an email on file; shipment receipts
  // and order-tracking links both require one, so we gate the rest of the
  // flow behind an inline collection step.
  const needsEmail = user && !user.email;

  useEffect(() => {
    api.get('/api/addresses')
      .then(({ data }) => {
        setAddresses(data);
        const def = data.find((a) => a.defaultAddress);
        if (def) setSelectedAddressId(def.id);
        else if (data[0]) setSelectedAddressId(data[0].id);
      })
      .finally(() => setAddressesLoaded(true));
  }, []);

  const selectedAddress = useMemo(
    () => addresses.find((a) => a.id === selectedAddressId),
    [addresses, selectedAddressId],
  );

  const cardComplete = isCardComplete(card);
  const canProceed = needsEmail
    ? false
    : step === 0 ? !!selectedAddressId : step === 1 ? cardComplete : true;

  if (!cart.items || cart.items.length === 0) {
    return (
      <div className="card flex flex-col items-center gap-3 py-16 text-center">
        <p className="text-lg font-medium">Sepetin boş</p>
        <Link to="/" className="btn-primary">Alışverişe başla</Link>
      </div>
    );
  }

  async function placeOrder() {
    if (!selectedAddressId || !cardComplete) return;
    setSubmitting(true);
    try {
      const { data } = await api.post('/api/orders/checkout', {
        addressId: selectedAddressId,
        card,
      });
      clearLocal();
      navigate(`/checkout/processing/${data.id}`, { replace: true });
    } catch (err) {
      toast.error(err.response?.data?.message || 'Ödeme başlatılamadı');
      setSubmitting(false);
    }
  }

  return (
    <div className="space-y-4 md:space-y-6">
      <header className="card p-3 sm:p-6">
        <CheckoutStepper steps={STEPS} current={step} onJump={setStep} />
      </header>

      <div className="grid gap-4 lg:grid-cols-[1fr_360px] lg:gap-6">
        <section className="space-y-4">
          {needsEmail && <EmailGate />}

          {step === 0 && (
            <StepCard
              icon={MapPin}
              title="Teslimat Adresi"
              hint="Siparişinin gönderileceği adresi seç."
            >
              {!addressesLoaded ? (
                <div className="h-24 animate-pulse rounded bg-gray-100" />
              ) : (
                <AddressPicker
                  addresses={addresses}
                  selectedId={selectedAddressId}
                  onSelect={setSelectedAddressId}
                />
              )}
              <div className="mt-3 flex items-center justify-between text-xs">
                <Link to="/account/addresses" className="text-n11-pink hover:underline">
                  Adresleri yönet
                </Link>
                {addresses.length > 0 && !selectedAddressId && (
                  <span className="text-amber-600">Bir adres seçmelisin</span>
                )}
              </div>
            </StepCard>
          )}

          {step === 1 && (
            <StepCard
              icon={CreditCard}
              title="Ödeme Bilgileri"
              hint="Iyzico sandbox üzerinden tahsil edilir, kart bilgilerin saklanmaz."
            >
              <CardForm value={card} onChange={setCard} disabled={submitting} />
              <p className="mt-4 flex items-center gap-2 rounded-md bg-emerald-50 p-2.5 text-xs text-emerald-700">
                <Lock className="h-3.5 w-3.5" strokeWidth={2.2} />
                Bu portfolyoda PAN tarayıcıdan API&apos;ye düz olarak akar; üretimde
                Iyzico Checkout-Form / 3DS dropin kullanılır.
              </p>
            </StepCard>
          )}

          {step === 2 && (
            <StepCard
              icon={ShieldCheck}
              title="Sipariş Onayı"
              hint="Bilgileri kontrol et, ardından siparişi tamamla."
            >
              <ReviewBlock
                address={selectedAddress}
                card={card}
                cart={cart}
                onEditAddress={() => setStep(0)}
                onEditCard={() => setStep(1)}
              />
            </StepCard>
          )}

          <NavButtons
            step={step}
            canProceed={canProceed}
            submitting={submitting}
            onBack={() => (step === 0 ? navigate('/cart') : setStep(step - 1))}
            onNext={() => (step === 2 ? placeOrder() : setStep(step + 1))}
          />
        </section>

        <aside>
          <SummaryCard cart={cart} />
        </aside>
      </div>
    </div>
  );
}

function EmailGate() {
  const [email, setEmail] = useState('');
  const [busy, setBusy] = useState(false);

  async function save(e) {
    e.preventDefault();
    setBusy(true);
    try {
      // 1. Persist email on the user record.
      await api.patch('/api/users/me', { email });
      // 2. Trade the refresh cookie for a fresh JWT — the new token carries
      //    the email claim, which order-service trusts when it derives
      //    user_email for the order row.  Without this rotation the next
      //    POST /api/orders/checkout still presents a stale email-less JWT.
      await performRefresh();
      // performRefresh updates tokenStore + AuthContext via AUTH_EVENT,
      // so the parent re-reads user.email and EmailGate unmounts itself.
      toast.success('E-posta kaydedildi');
      // Defensive: if for some reason the AUTH_EVENT didn't fire, force a
      // re-read so the gate definitely closes.
      tokenStore.getUser();
    } catch (err) {
      const message = err.response?.data?.message || 'E-posta kaydedilemedi';
      toast.error(message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <article className="card border-amber-200 bg-amber-50/40 p-5">
      <header className="mb-3 flex items-center gap-3">
        <span className="grid h-9 w-9 place-items-center rounded-full bg-amber-100 text-amber-700">
          <Mail className="h-4 w-4" strokeWidth={1.8} aria-hidden />
        </span>
        <div>
          <h2 className="text-base font-semibold text-gray-800">E-posta Adresin</h2>
          <p className="text-xs text-gray-500">
            Sipariş onayı, fatura ve kargo takibi için gerekli.
          </p>
        </div>
      </header>
      <form onSubmit={save} className="flex flex-col gap-2 sm:flex-row">
        <input
          type="email"
          required
          autoComplete="email"
          placeholder="ornek@mail.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="input flex-1"
        />
        <button type="submit" disabled={busy || !email} className="btn-primary whitespace-nowrap">
          {busy ? 'Kaydediliyor…' : 'Kaydet ve Devam Et'}
        </button>
      </form>
    </article>
  );
}

function StepCard({ icon: Icon, title, hint, children }) {
  return (
    <article className="card p-5">
      <header className="mb-4 flex items-center gap-3">
        <span className="grid h-9 w-9 place-items-center rounded-full bg-n11-pink/10 text-n11-pink">
          <Icon className="h-4.5 w-4.5" strokeWidth={1.8} aria-hidden />
        </span>
        <div>
          <h2 className="text-base font-semibold text-gray-800">{title}</h2>
          {hint && <p className="text-xs text-gray-500">{hint}</p>}
        </div>
      </header>
      {children}
    </article>
  );
}

function NavButtons({ step, canProceed, submitting, onBack, onNext }) {
  const lastStep = step === 2;
  return (
    <div className="flex items-center justify-between gap-3">
      <button
        type="button"
        onClick={onBack}
        disabled={submitting}
        className="inline-flex items-center gap-1 rounded-md border border-gray-200 bg-white px-4 py-2 text-sm font-medium text-gray-700 transition hover:bg-gray-50 disabled:opacity-50"
      >
        <ChevronLeft className="h-4 w-4" />
        {step === 0 ? 'Sepete dön' : 'Geri'}
      </button>
      <button
        type="button"
        onClick={onNext}
        disabled={!canProceed || submitting}
        className="btn-primary inline-flex items-center gap-1"
      >
        {submitting ? 'Gönderiliyor…' : lastStep ? 'Siparişi Tamamla' : 'Devam Et'}
        {!lastStep && <ChevronRight className="h-4 w-4" />}
      </button>
    </div>
  );
}

function SummaryCard({ cart }) {
  return (
    <div className="card space-y-4 p-4 lg:sticky lg:top-4">
      <h3 className="text-sm font-semibold text-gray-700">Sipariş Özeti</h3>
      <ul className="space-y-2 text-sm">
        {cart.items.map((item) => (
          <li key={item.id} className="flex items-center gap-2">
            <div className="h-10 w-10 shrink-0 overflow-hidden rounded bg-gray-100">
              {item.imageUrl && <img src={item.imageUrl} alt={item.productName} className="h-full w-full object-cover" />}
            </div>
            <div className="flex-1 truncate">
              <p className="truncate text-xs text-gray-700">{item.productName}</p>
              <p className="text-[11px] text-gray-400">× {item.quantity}</p>
            </div>
            <span className="text-xs font-medium text-gray-700">
              {formatCurrency(item.lineTotal, item.currency)}
            </span>
          </li>
        ))}
      </ul>

      <dl className="space-y-1.5 border-t border-gray-100 pt-3 text-sm text-gray-600">
        <div className="flex justify-between">
          <dt>Ara toplam</dt>
          <dd>{formatCurrency(cart.subtotal ?? cart.totalAmount, cart.currency)}</dd>
        </div>
        {cart.discounts?.map((d) => (
          <div key={`${d.kind}:${d.code}`} className="flex justify-between text-emerald-600">
            <dt>{d.label}</dt>
            <dd>−{formatCurrency(d.amount, cart.currency)}</dd>
          </div>
        ))}
        <div className="flex justify-between">
          <dt>Kargo</dt>
          <dd className="text-emerald-600">Ücretsiz</dd>
        </div>
        <div className="flex justify-between border-t border-gray-100 pt-2 text-base font-semibold text-gray-900">
          <dt>Toplam</dt>
          <dd>{formatCurrency(cart.totalAmount, cart.currency)}</dd>
        </div>
      </dl>
    </div>
  );
}

function ReviewBlock({ address, card, cart, onEditAddress, onEditCard }) {
  return (
    <div className="space-y-4">
      <ReviewRow
        title="Teslimat Adresi"
        onEdit={onEditAddress}
        body={
          address ? (
            <>
              <p className="font-medium text-gray-800">{address.title} · {address.recipientName}</p>
              <p className="text-gray-600">{address.phone}</p>
              <p className="text-gray-600">
                {address.line1}, {[address.district, address.city, address.postalCode].filter(Boolean).join(' / ')}
              </p>
            </>
          ) : (
            <p className="text-gray-500">Adres seçilmedi.</p>
          )
        }
      />
      <ReviewRow
        title="Ödeme Yöntemi"
        onEdit={onEditCard}
        body={
          <>
            <p className="font-medium text-gray-800">
              {card.holderName || '—'}
            </p>
            <p className="text-gray-600">
              **** **** **** {card.number.slice(-4) || '••••'} · son kullanma {card.expireMonth || '--'}/{card.expireYear.slice(-2) || '--'}
            </p>
          </>
        }
      />
      <ReviewRow
        title={`Ürünler (${cart.totalQuantity})`}
        body={
          <ul className="space-y-1">
            {cart.items.map((item) => (
              <li key={item.id} className="flex justify-between text-gray-700">
                <span className="truncate pr-2">{item.productName} × {item.quantity}</span>
                <span className="shrink-0 font-medium">{formatCurrency(item.lineTotal, item.currency)}</span>
              </li>
            ))}
          </ul>
        }
      />
    </div>
  );
}

function ReviewRow({ title, body, onEdit }) {
  return (
    <div className="rounded-md border border-gray-200 p-3">
      <div className="mb-1.5 flex items-center justify-between">
        <h4 className="text-xs font-semibold uppercase tracking-wide text-gray-500">{title}</h4>
        {onEdit && (
          <button onClick={onEdit} className="text-xs font-medium text-n11-pink hover:underline">
            Düzenle
          </button>
        )}
      </div>
      <div className="space-y-0.5 text-sm">{body}</div>
    </div>
  );
}
