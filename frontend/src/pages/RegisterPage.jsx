import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../state/AuthContext.jsx';

export default function RegisterPage() {
  const { register, login, loading } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', password: '' });

  function onChange(e) {
    setForm((f) => ({ ...f, [e.target.name]: e.target.value }));
  }

  async function onSubmit(e) {
    e.preventDefault();
    try {
      await register(form);
      await login(form.email, form.password);
      navigate('/');
    } catch {
      // toast already shown
    }
  }

  return (
    <div className="mx-auto mt-12 max-w-md p-6">
      <div className="card p-6">
        <h1 className="text-xl font-semibold">Kayıt Ol</h1>
        <form onSubmit={onSubmit} className="mt-6 space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700" htmlFor="fullName">
              İsim Soyisim
            </label>
            <input id="fullName" name="fullName" required minLength={2} className="input mt-1" value={form.fullName} onChange={onChange} />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700" htmlFor="email">
              E-posta
            </label>
            <input id="email" name="email" type="email" required className="input mt-1" value={form.email} onChange={onChange} />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700" htmlFor="password">
              Şifre
            </label>
            <input id="password" name="password" type="password" minLength={8} required className="input mt-1" value={form.password} onChange={onChange} />
            <p className="mt-1 text-xs text-gray-400">En az 8 karakter</p>
          </div>
          <button type="submit" disabled={loading} className="btn-primary w-full">
            {loading ? 'Kayıt olunuyor…' : 'Hesap oluştur'}
          </button>
          <p className="text-center text-sm text-gray-500">
            Zaten hesabın var mı?{' '}
            <Link className="font-medium text-n11-pink hover:text-n11-pinkDark" to="/login">
              Giriş yap
            </Link>
          </p>
        </form>
      </div>
    </div>
  );
}
