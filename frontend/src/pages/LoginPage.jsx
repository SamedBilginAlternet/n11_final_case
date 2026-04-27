import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../state/AuthContext.jsx';

export default function LoginPage() {
  const { login, loading } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

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
    <div className="mx-auto mt-12 max-w-md p-6">
      <div className="card p-6">
        <h1 className="text-xl font-semibold">Giriş Yap</h1>
        <p className="mt-1 text-sm text-slate-500">Hesabına giriş yaparak alışverişe devam et.</p>
        <form onSubmit={onSubmit} className="mt-6 space-y-4">
          <div>
            <label htmlFor="email" className="block text-sm font-medium text-slate-700">
              E-posta
            </label>
            <input id="email" type="email" required className="input mt-1" value={email} onChange={(e) => setEmail(e.target.value)} />
          </div>
          <div>
            <label htmlFor="password" className="block text-sm font-medium text-slate-700">
              Şifre
            </label>
            <input id="password" type="password" required className="input mt-1" value={password} onChange={(e) => setPassword(e.target.value)} />
          </div>
          <button type="submit" disabled={loading} className="btn-primary w-full">
            {loading ? 'Giriş yapılıyor…' : 'Giriş Yap'}
          </button>
          <p className="text-center text-sm text-slate-500">
            Hesabın yok mu?{' '}
            <Link className="text-n11-orange" to="/register">
              Kayıt ol
            </Link>
          </p>
        </form>
      </div>
    </div>
  );
}
