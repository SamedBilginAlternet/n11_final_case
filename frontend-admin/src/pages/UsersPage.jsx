import { useCallback, useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { ShieldCheck, Shield } from 'lucide-react';
import clsx from 'clsx';
import { api } from '../api/client.js';
import { useAuth } from '../state/AuthContext.jsx';
import { formatDate } from '../utils/format.js';

export default function UsersPage() {
  const { user: me } = useAuth();
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState(null);

  const refresh = useCallback(() => {
    setLoading(true);
    api
      .get('/api/users?size=200')
      .then((res) => setUsers(res.data || []))
      .catch(() => setUsers([]))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { refresh(); }, [refresh]);

  async function setRole(u, action) {
    setBusyId(u.id);
    try {
      await api.post(`/api/users/${u.id}/${action}`);
      toast.success(action === 'promote' ? 'Kullanıcı ADMIN yapıldı.' : 'Yetki kaldırıldı.');
      refresh();
    } catch (err) {
      toast.error(err.response?.data?.message || 'İşlem başarısız.');
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Kullanıcılar</h1>
        <p className="text-sm text-slate-500">Tüm kayıtlı kullanıcılar. Rol değişikliği auth-service log'una düşer.</p>
      </div>

      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
            <tr>
              <th className="px-4 py-3">Kullanıcı</th>
              <th className="px-4 py-3">Rol</th>
              <th className="px-4 py-3">Kayıt</th>
              <th className="px-4 py-3 text-right">Aksiyon</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {loading ? (
              Array.from({ length: 5 }).map((_, i) => (
                <tr key={i} className="animate-pulse">
                  <td colSpan="4" className="px-4 py-4"><div className="h-3 w-full rounded bg-slate-100" /></td>
                </tr>
              ))
            ) : users.length === 0 ? (
              <tr>
                <td colSpan="4" className="px-4 py-12 text-center text-sm text-slate-400">
                  Kullanıcı yok.
                </td>
              </tr>
            ) : (
              users.map((u) => {
                const isSelf = me?.id === u.id;
                const isAdmin = u.role === 'ADMIN';
                return (
                  <tr key={u.id} className={clsx('hover:bg-slate-50', isSelf && 'bg-brand-50/30')}>
                    <td className="px-4 py-2">
                      <p className="font-medium text-slate-800">
                        {u.fullName || <span className="text-slate-400">(isimsiz)</span>}
                        {isSelf && <span className="ml-2 text-[10px] font-semibold uppercase tracking-wider text-brand-600">Sen</span>}
                      </p>
                      <p className="text-xs text-slate-500">{u.email}</p>
                    </td>
                    <td className="px-4 py-2">
                      <span className={clsx(
                        'inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-semibold',
                        isAdmin ? 'bg-brand-100 text-brand-700' : 'bg-slate-100 text-slate-600'
                      )}>
                        {isAdmin ? <ShieldCheck size={11} /> : <Shield size={11} />}
                        {u.role}
                      </span>
                    </td>
                    <td className="px-4 py-2 text-xs text-slate-500">{formatDate(u.createdAt)}</td>
                    <td className="px-4 py-2 text-right">
                      {isAdmin ? (
                        <button
                          onClick={() => setRole(u, 'demote')}
                          disabled={isSelf || busyId === u.id}
                          className="btn-secondary text-xs"
                          title={isSelf ? 'Kendi rolünü düşüremezsin' : 'USER yap'}
                        >
                          Yetkiyi Kaldır
                        </button>
                      ) : (
                        <button
                          onClick={() => setRole(u, 'promote')}
                          disabled={busyId === u.id}
                          className="btn-primary text-xs"
                        >
                          <ShieldCheck size={12} /> ADMIN Yap
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
