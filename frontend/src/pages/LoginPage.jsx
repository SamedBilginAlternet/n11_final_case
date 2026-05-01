import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import toast from 'react-hot-toast';
import { RecaptchaVerifier, signInWithPhoneNumber } from 'firebase/auth';
import { useAuth } from '../state/AuthContext.jsx';
import { apiRoot } from '../api/client.js';
import { getFirebaseAuth, isFirebaseConfigured } from '../lib/firebase.js';

// Use the shared apiRoot (origin without /api) so the redirect URL is
// `<origin>/api/auth/oauth2/authorize/google`.  The earlier hand-rolled
// version did `VITE_API_BASE_URL + '/api/...'` which produced `/api/api/...`
// in production where the build arg was `VITE_API_BASE_URL=/api`.
const OAUTH_BASE = `${apiRoot}/api/auth/oauth2/authorize`;

export default function LoginPage() {
  const { login, loginWithPhone, loading } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [params, setParams] = useSearchParams();
  // Default to phone tab when Firebase is available — that's the modern TR
  // e-commerce default.  Falls back to email when Firebase isn't wired so
  // local/dev builds without firebase env vars still have a working login.
  const [tab, setTab] = useState(isFirebaseConfigured ? 'phone' : 'email');

  useEffect(() => {
    const oauthError = params.get('oauth_error');
    if (oauthError) {
      toast.error(`Sosyal giriş başarısız: ${decodeURIComponent(oauthError)}`);
      const next = new URLSearchParams(params);
      next.delete('oauth_error');
      setParams(next, { replace: true });
    }
  }, [params, setParams]);

  function onLoginSuccess() {
    const next = location.state?.from?.pathname || '/';
    navigate(next, { replace: true });
  }

  return (
    <div className="mx-auto mt-6 max-w-md p-3 sm:mt-12 sm:p-6">
      <div className="card p-4 sm:p-6">
        <h1 className="text-xl font-semibold">Giriş Yap</h1>
        <p className="mt-1 text-sm text-gray-500">Hesabına giriş yaparak alışverişe devam et.</p>

        <div className="mt-6 space-y-2">
          <a
            href={`${OAUTH_BASE}/google`}
            className="flex w-full items-center justify-center gap-2 rounded border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-50"
          >
            <GoogleMark />
            Google ile Giriş Yap
          </a>
        </div>

        <div className="my-6 flex items-center gap-3 text-xs uppercase text-gray-400">
          <span className="h-px flex-1 bg-gray-200" />
          veya
          <span className="h-px flex-1 bg-gray-200" />
        </div>

        {isFirebaseConfigured && (
          <div className="mb-4 grid grid-cols-2 gap-2">
            <TabButton active={tab === 'phone'} onClick={() => setTab('phone')}>Telefon</TabButton>
            <TabButton active={tab === 'email'} onClick={() => setTab('email')}>E-posta</TabButton>
          </div>
        )}

        {tab === 'phone' && isFirebaseConfigured ? (
          <PhoneLoginForm
            loading={loading}
            onConfirm={async (idToken) => {
              try {
                await loginWithPhone(idToken);
                onLoginSuccess();
              } catch {
                /* toast already shown */
              }
            }}
          />
        ) : (
          <EmailLoginForm
            loading={loading}
            onSubmit={async (email, password) => {
              try {
                await login(email, password);
                onLoginSuccess();
              } catch {
                /* toast already shown */
              }
            }}
          />
        )}

        <p className="mt-4 text-center text-sm text-gray-500">
          Hesabın yok mu?{' '}
          <Link className="font-medium text-n11-pink hover:text-n11-pinkDark" to="/register">
            Kayıt ol
          </Link>
        </p>
      </div>
    </div>
  );
}

function TabButton({ active, onClick, children }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`rounded-md px-3 py-2 text-sm font-medium transition-colors ${
        active ? 'bg-n11-pink text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
      }`}
    >
      {children}
    </button>
  );
}

function EmailLoginForm({ loading, onSubmit }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  return (
    <form onSubmit={(e) => { e.preventDefault(); onSubmit(email, password); }} className="space-y-4">
      <div>
        <label htmlFor="email" className="block text-sm font-medium text-gray-700">E-posta</label>
        <input id="email" type="email" required className="input mt-1" value={email} onChange={(e) => setEmail(e.target.value)} />
      </div>
      <div>
        <label htmlFor="password" className="block text-sm font-medium text-gray-700">Şifre</label>
        <input id="password" type="password" required className="input mt-1" value={password} onChange={(e) => setPassword(e.target.value)} />
      </div>
      <button type="submit" disabled={loading} className="btn-primary w-full">
        {loading ? 'Giriş yapılıyor…' : 'Giriş Yap'}
      </button>
    </form>
  );
}

/**
 * Two-step phone login.  We hold the Firebase ConfirmationResult in a ref
 * across renders so React's reconciliation never wipes it out — that object
 * is the only thing that knows how to verify the OTP.
 */
function PhoneLoginForm({ loading, onConfirm }) {
  const [step, setStep] = useState('phone');
  const [phone, setPhone] = useState('+90');
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const recaptchaRef = useRef(null);
  const recaptchaContainerRef = useRef(null);
  const confirmationRef = useRef(null);

  // Tear down the reCAPTCHA on unmount; otherwise re-mounting LoginPage
  // (e.g. after navigating away and back) leaves a stale widget that
  // Firebase refuses to re-initialise.
  useEffect(() => () => {
    if (recaptchaRef.current) {
      try { recaptchaRef.current.clear(); } catch { /* noop */ }
      recaptchaRef.current = null;
    }
  }, []);

  async function ensureRecaptcha() {
    if (recaptchaRef.current) return recaptchaRef.current;
    const auth = getFirebaseAuth();
    const verifier = new RecaptchaVerifier(auth, recaptchaContainerRef.current, { size: 'invisible' });
    await verifier.render();
    recaptchaRef.current = verifier;
    return verifier;
  }

  async function sendCode(e) {
    e.preventDefault();
    if (!/^\+\d{10,15}$/.test(phone)) {
      toast.error('Numarayı +90 ile başlayan formatta gir (örn. +905551234567)');
      return;
    }
    setBusy(true);
    try {
      const auth = getFirebaseAuth();
      const verifier = await ensureRecaptcha();
      const confirmation = await signInWithPhoneNumber(auth, phone, verifier);
      confirmationRef.current = confirmation;
      setStep('otp');
      toast.success('Kod gönderildi');
    } catch (err) {
      // Firebase throws fairly opaque errors; surface code so user can debug
      const code = err?.code || 'unknown';
      toast.error(`Kod gönderilemedi (${code})`);
      // Reset reCAPTCHA so the next attempt gets a fresh widget; reuse after
      // a failure leaves Firebase wedged.
      if (recaptchaRef.current) {
        try { recaptchaRef.current.clear(); } catch { /* noop */ }
        recaptchaRef.current = null;
      }
    } finally {
      setBusy(false);
    }
  }

  async function verifyCode(e) {
    e.preventDefault();
    if (!confirmationRef.current) {
      toast.error('Önce kod iste');
      return;
    }
    setBusy(true);
    try {
      const result = await confirmationRef.current.confirm(code);
      const idToken = await result.user.getIdToken();
      await onConfirm(idToken);
    } catch (err) {
      const code = err?.code || 'unknown';
      toast.error(`Kod doğrulanamadı (${code})`);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="space-y-4">
      {step === 'phone' && (
        <form onSubmit={sendCode} className="space-y-4">
          <div>
            <label htmlFor="phone" className="block text-sm font-medium text-gray-700">Telefon Numarası</label>
            <input
              id="phone"
              type="tel"
              required
              autoComplete="tel"
              placeholder="+905551234567"
              className="input mt-1"
              value={phone}
              onChange={(e) => setPhone(e.target.value.replace(/[^\d+]/g, ''))}
            />
            <p className="mt-1 text-xs text-gray-400">
              Ülke kodu zorunlu. Türkiye numaraları için <code className="rounded bg-gray-100 px-1">+90</code> ile başlat.
            </p>
          </div>
          <button type="submit" disabled={busy || loading} className="btn-primary w-full">
            {busy ? 'Kod gönderiliyor…' : 'Kod Gönder'}
          </button>
        </form>
      )}

      {step === 'otp' && (
        <form onSubmit={verifyCode} className="space-y-4">
          <div>
            <label htmlFor="otp" className="block text-sm font-medium text-gray-700">
              Doğrulama Kodu
            </label>
            <input
              id="otp"
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              required
              className="input mt-1 tracking-[0.5em] text-center text-lg"
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
            />
            <p className="mt-1 text-xs text-gray-400">
              {phone} numarasına 6 haneli kod gönderildi.
            </p>
          </div>
          <button type="submit" disabled={busy || loading || code.length !== 6} className="btn-primary w-full">
            {busy || loading ? 'Doğrulanıyor…' : 'Doğrula ve Giriş Yap'}
          </button>
          <button
            type="button"
            onClick={() => { setStep('phone'); setCode(''); confirmationRef.current = null; }}
            className="block w-full text-center text-xs text-gray-500 hover:text-n11-pink"
          >
            Numarayı değiştir
          </button>
        </form>
      )}

      {/* Invisible reCAPTCHA target — Firebase mounts the challenge here. */}
      <div ref={recaptchaContainerRef} />
    </div>
  );
}

function GoogleMark() {
  return (
    <svg width="16" height="16" viewBox="0 0 48 48" aria-hidden="true">
      <path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3c-1.6 4.7-6.1 8-11.3 8a12 12 0 1 1 0-24c3 0 5.7 1.1 7.7 2.9l5.7-5.7A20 20 0 1 0 24 44c11 0 20-7.6 20-20 0-1.2-.1-2.3-.4-3.5z" />
      <path fill="#FF3D00" d="M6.3 14.7l6.6 4.8C14.7 16 19 13 24 13c3 0 5.7 1.1 7.7 2.9l5.7-5.7A20 20 0 0 0 6.3 14.7z" />
      <path fill="#4CAF50" d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2c-2 1.4-4.5 2.4-7.2 2.4-5.2 0-9.6-3.3-11.2-8l-6.5 5A20 20 0 0 0 24 44z" />
      <path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.3-2.3 4.4-4.1 5.6l6.2 5.2C41.2 35.5 44 30.2 44 24c0-1.2-.1-2.3-.4-3.5z" />
    </svg>
  );
}
