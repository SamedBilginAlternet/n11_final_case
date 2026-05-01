import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import toast from 'react-hot-toast';
import { Heart, MapPin, Package, Pencil, ShieldCheck, User } from 'lucide-react';
import { api, performRefresh } from '../api/client.js';

export default function ProfilePage() {
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [editing, setEditing] = useState(false);

  useEffect(() => {
    let cancelled = false;
    api.get('/api/users/me')
      .then((res) => { if (!cancelled) setProfile(res.data); })
      .catch(() => { if (!cancelled) setError('Profil yüklenemedi'); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  async function saveProfile({ email, fullName }) {
    const payload = {};
    if (email && email !== profile.email) payload.email = email;
    if (fullName && fullName !== profile.fullName) payload.fullName = fullName;
    if (Object.keys(payload).length === 0) {
      setEditing(false);
      return;
    }
    try {
      const { data } = await api.patch('/api/users/me', payload);
      setProfile(data);
      // Refresh JWT so order-service / other downstream services see the
      // updated email claim immediately.  Without this, the existing token
      // keeps the stale value until its 60-min expiry.
      await performRefresh().catch(() => { /* non-fatal — UI already updated */ });
      toast.success('Profil güncellendi');
      setEditing(false);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Güncelleme başarısız');
    }
  }

  if (loading) return <div className="card h-64 animate-pulse bg-gray-100" />;
  if (error) return <p className="rounded bg-red-50 p-3 text-sm text-red-600">{error}</p>;
  if (!profile) return null;

  return (
    <div className="space-y-6">
      <div className="card p-6">
        <div className="flex items-start gap-4">
          <div className="grid h-14 w-14 place-items-center rounded-full bg-n11-pink/10 text-n11-pink">
            <User className="h-7 w-7" strokeWidth={1.7} aria-hidden />
          </div>
          <div className="flex-1">
            {editing ? (
              <ProfileEditForm profile={profile} onCancel={() => setEditing(false)} onSave={saveProfile} />
            ) : (
              <>
                <div className="flex items-center justify-between gap-2">
                  <h1 className="text-xl font-semibold tracking-tight">
                    {profile.fullName || profile.phoneNumber || profile.email || 'Hesabım'}
                  </h1>
                  <button
                    onClick={() => setEditing(true)}
                    className="inline-flex items-center gap-1 rounded border border-gray-200 px-2 py-1 text-xs font-medium text-gray-600 hover:border-n11-pink hover:text-n11-pink"
                  >
                    <Pencil className="h-3 w-3" /> Düzenle
                  </button>
                </div>
                <p className="text-sm text-gray-500">{profile.email || 'E-posta eklenmemiş'}</p>
                {profile.phoneNumber && <p className="text-xs text-gray-500">{profile.phoneNumber}</p>}
                <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-gray-500">
                  {profile.role === 'ADMIN' && (
                    <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 font-medium text-amber-700">
                      <ShieldCheck className="h-3 w-3" aria-hidden /> Yönetici
                    </span>
                  )}
                  <span>Üyelik: {formatJoined(profile.createdAt)}</span>
                </div>
              </>
            )}
          </div>
        </div>
      </div>

      <div className="grid gap-3 sm:grid-cols-3">
        <ProfileLink to="/orders" icon={Package} title="Siparişlerim" subtitle="Geçmiş ve aktif siparişler" />
        <ProfileLink to="/favorites" icon={Heart} title="Favorilerim" subtitle="Beğendiğin ürünler" />
        <ProfileLink to="/account/addresses" icon={MapPin} title="Adreslerim" subtitle="Teslimat adreslerin" />
      </div>
    </div>
  );
}

function ProfileEditForm({ profile, onCancel, onSave }) {
  const [email, setEmail] = useState(profile.email || '');
  const [fullName, setFullName] = useState(profile.fullName || '');
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    try {
      await onSave({ email: email.trim(), fullName: fullName.trim() });
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="space-y-3">
      <div>
        <label className="block text-xs font-medium text-gray-600">İsim Soyisim</label>
        <input
          type="text"
          value={fullName}
          onChange={(e) => setFullName(e.target.value)}
          className="input mt-1"
          autoComplete="name"
        />
      </div>
      <div>
        <label className="block text-xs font-medium text-gray-600">E-posta</label>
        <input
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          className="input mt-1"
          autoComplete="email"
        />
      </div>
      <div className="flex items-center gap-2">
        <button type="submit" disabled={busy} className="btn-primary text-sm">
          {busy ? 'Kaydediliyor…' : 'Kaydet'}
        </button>
        <button
          type="button"
          onClick={onCancel}
          disabled={busy}
          className="rounded border border-gray-200 px-3 py-1.5 text-sm text-gray-600 hover:bg-gray-50"
        >
          Vazgeç
        </button>
      </div>
    </form>
  );
}

function ProfileLink({ to, icon: Icon, title, subtitle }) {
  return (
    <Link
      to={to}
      className="card flex items-center gap-3 p-4 transition hover:border-n11-pink hover:shadow-sm"
    >
      <Icon className="h-6 w-6 text-n11-pink" strokeWidth={1.7} aria-hidden />
      <div>
        <p className="text-sm font-semibold text-gray-800">{title}</p>
        <p className="text-xs text-gray-500">{subtitle}</p>
      </div>
    </Link>
  );
}

function formatJoined(iso) {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleDateString('tr-TR', { year: 'numeric', month: 'long' });
  } catch {
    return '—';
  }
}
