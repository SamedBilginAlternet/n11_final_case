import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import { CheckCircle2, Loader2, XCircle } from 'lucide-react';
import { api } from '../api/client.js';

const TERMINAL_OK = new Set(['CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED']);
const TERMINAL_FAIL = new Set(['CANCELLED']);
const POLL_MS = 1000;
const TIMEOUT_MS = 30_000;

/**
 * Bridges the checkout POST and the /orders page. Order creation is sync but
 * the actual charge is async (RabbitMQ → payment-service → Iyzico → result
 * event back), so for ~1–3 s the order sits at AWAITING_PAYMENT. Polling once
 * a second here keeps the UI honest until the saga settles, then redirects to
 * /orders so the user lands on the success/failure view.
 */
export default function CheckoutProcessingPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [order, setOrder] = useState(null);
  const [phase, setPhase] = useState('processing'); // processing | success | failed | timeout
  const startRef = useRef(Date.now());

  useEffect(() => {
    let cancelled = false;
    let timer;

    async function poll() {
      try {
        const { data } = await api.get(`/api/orders/${id}`);
        if (cancelled) return;
        setOrder(data);
        if (TERMINAL_OK.has(data.status)) {
          setPhase('success');
          setTimeout(() => navigate('/orders', { replace: true }), 1200);
          return;
        }
        if (TERMINAL_FAIL.has(data.status)) {
          setPhase('failed');
          setTimeout(() => navigate('/orders', { replace: true }), 2000);
          return;
        }
      } catch {
        // network blip — keep polling, the saga is what matters
      }
      if (Date.now() - startRef.current > TIMEOUT_MS) {
        setPhase('timeout');
        setTimeout(() => navigate('/orders', { replace: true }), 1500);
        return;
      }
      timer = setTimeout(poll, POLL_MS);
    }
    poll();
    return () => { cancelled = true; if (timer) clearTimeout(timer); };
  }, [id, navigate]);

  return (
    <div className="mx-auto mt-12 max-w-md">
      <div className="card flex flex-col items-center gap-4 px-6 py-10 text-center">
        <Visual phase={phase} />
        <h1 className="text-lg font-semibold text-gray-800">{TITLES[phase]}</h1>
        <p className="text-sm text-gray-500">{SUBS[phase]}</p>
        {order && (
          <p className="text-xs text-gray-400">Sipariş #{order.id}</p>
        )}
      </div>
    </div>
  );
}

const TITLES = {
  processing: 'Ödeme işleniyor…',
  success: 'Ödeme başarılı',
  failed: 'Ödeme reddedildi',
  timeout: 'Ödeme uzun sürdü',
};

const SUBS = {
  processing: 'Iyzico ile bağlantı kuruluyor, lütfen sayfayı kapatma.',
  success: 'Siparişlerine yönlendiriliyorsun…',
  failed: 'Sipariş iptal edildi. Detaylar siparişler sayfasında.',
  timeout: 'Durumu siparişler sayfasından takip edebilirsin.',
};

function Visual({ phase }) {
  if (phase === 'success') {
    return (
      <motion.div
        initial={{ scale: 0.6, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        className="grid h-16 w-16 place-items-center rounded-full bg-emerald-100 text-emerald-600"
      >
        <CheckCircle2 className="h-9 w-9" strokeWidth={1.8} />
      </motion.div>
    );
  }
  if (phase === 'failed') {
    return (
      <div className="grid h-16 w-16 place-items-center rounded-full bg-red-100 text-red-600">
        <XCircle className="h-9 w-9" strokeWidth={1.8} />
      </div>
    );
  }
  return (
    <div className="grid h-16 w-16 place-items-center rounded-full bg-n11-pink/10 text-n11-pink">
      <Loader2 className="h-9 w-9 animate-spin" strokeWidth={1.8} />
    </div>
  );
}
