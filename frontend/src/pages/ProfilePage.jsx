import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Heart, MapPin, Package, ShieldCheck, User } from 'lucide-react';
import { api } from '../api/client.js';

export default function ProfilePage() {
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    api.get('/api/users/me')
      .then((res) => { if (!cancelled) setProfile(res.data); })
      .catch(() => { if (!cancelled) setError('Profil yüklenemedi'); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

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
            <h1 className="text-xl font-semibold tracking-tight">{profile.fullName}</h1>
            <p className="text-sm text-gray-500">{profile.email}</p>
            <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-gray-500">
              {profile.role === 'ADMIN' && (
                <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 font-medium text-amber-700">
                  <ShieldCheck className="h-3 w-3" aria-hidden /> Yönetici
                </span>
              )}
              <span>Üyelik: {formatJoined(profile.createdAt)}</span>
            </div>
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
