import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import toast from 'react-hot-toast';
import { useAuth } from '../state/AuthContext.jsx';
import { apiRoot } from '../api/client.js';

// Use the shared apiRoot (origin without /api) so the redirect URL is
// `<origin>/api/auth/oauth2/authorize/google`.  The earlier hand-rolled
// version did `VITE_API_BASE_URL + '/api/...'` which produced `/api/api/...`
// in production where the build arg was `VITE_API_BASE_URL=/api`.
const OAUTH_BASE = `${apiRoot}/api/auth/oauth2/authorize`;

export default function LoginPage() {
  const { login, loading } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [params, setParams] = useSearchParams();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  useEffect(() => {
    const oauthError = params.get('oauth_error');
    if (oauthError) {
      toast.error(`Sosyal giriş başarısız: ${decodeURIComponent(oauthError)}`);
      const next = new URLSearchParams(params);
      next.delete('oauth_error');
      setParams(next, { replace: true });
    }
  }, [params, setParams]);

  async function onSubmit(e) {
    e.preventDefault();
    try {
      await login(email, password);
      const next = location.state?.from?.pathname || '/';
      navigate(next, { replace: true });
    } catch {
      // toast already shown
    }
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

        <form onSubmit={onSubmit} className="space-y-4">
          <div>
            <label htmlFor="email" className="block text-sm font-medium text-gray-700">
              E-posta
            </label>
            <input id="email" type="email" required className="input mt-1" value={email} onChange={(e) => setEmail(e.target.value)} />
          </div>
          <div>
            <label htmlFor="password" className="block text-sm font-medium text-gray-700">
              Şifre
            </label>
            <input id="password" type="password" required className="input mt-1" value={password} onChange={(e) => setPassword(e.target.value)} />
          </div>
          <button type="submit" disabled={loading} className="btn-primary w-full">
            {loading ? 'Giriş yapılıyor…' : 'Giriş Yap'}
          </button>
          <p className="text-center text-sm text-gray-500">
            Hesabın yok mu?{' '}
            <Link className="font-medium text-n11-pink hover:text-n11-pinkDark" to="/register">
              Kayıt ol
            </Link>
          </p>
        </form>
      </div>
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

