import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Lock } from 'lucide-react';
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
      // toast already shown in AuthContext
    }
  }

  return (
    <div className="grid min-h-screen place-items-center bg-gradient-to-br from-slate-100 via-white to-brand-50 p-6">
      <div className="card w-full max-w-md p-8">
        <div className="mb-6 flex items-center gap-3">
          <div className="grid h-10 w-10 place-items-center rounded-lg bg-gradient-to-br from-brand-500 to-brand-700 text-white">
            <Lock size={18} />
          </div>
          <div>
            <h1 className="text-lg font-bold tracking-tight">n11 Admin Paneli</h1>
            <p className="text-xs text-slate-500">Yalnızca ADMIN rolündeki kullanıcılar erişebilir</p>
          </div>
        </div>

        <form onSubmit={onSubmit} className="space-y-4">
          <div>
            <label htmlFor="email" className="block text-xs font-semibold uppercase tracking-wider text-slate-600">
              E-posta
            </label>
            <input
              id="email"
              type="email"
              required
              autoComplete="email"
              className="input mt-1"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>
          <div>
            <label htmlFor="password" className="block text-xs font-semibold uppercase tracking-wider text-slate-600">
              Şifre
            </label>
            <input
              id="password"
              type="password"
              required
              autoComplete="current-password"
              className="input mt-1"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>
          <button type="submit" disabled={loading} className="btn-primary w-full">
            {loading ? 'Giriş yapılıyor…' : 'Giriş Yap'}
          </button>
        </form>
      </div>
    </div>
  );
}
