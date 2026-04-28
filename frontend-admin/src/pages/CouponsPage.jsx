import { useCallback, useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { Plus, Edit3, Trash2, Power } from 'lucide-react';
import { api } from '../api/client.js';
import CouponFormModal from '../components/CouponFormModal.jsx';
import { formatCurrency, formatDate } from '../utils/format.js';

export default function CouponsPage() {
  const [coupons, setCoupons] = useState([]);
  const [activeFilter, setActiveFilter] = useState('all'); // all | active | inactive
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(null);
  const [confirmDelete, setConfirmDelete] = useState(null);

  const refresh = useCallback(() => {
    setLoading(true);
    const params = new URLSearchParams({ size: '100' });
    if (activeFilter === 'active') params.set('activeOnly', 'true');
    if (activeFilter === 'inactive') params.set('activeOnly', 'false');
    api
      .get(`/api/coupons?${params.toString()}`)
      .then((res) => setCoupons(res.data || []))
      .catch(() => setCoupons([]))
      .finally(() => setLoading(false));
  }, [activeFilter]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  async function startEdit(id) {
    try {
      const { data } = await api.get(`/api/coupons/${id}`);
      setEditing(data);
    } catch {
      toast.error('Kupon yüklenemedi');
    }
  }

  async function toggleActive(c) {
    try {
      await api.put(`/api/coupons/${c.id}`, {
        code: c.code,
        label: c.label,
        type: c.type,
        value: c.value,
        minCartTotal: c.minCartTotal,
        maxRedemptions: c.maxRedemptions,
        validFrom: c.validFrom,
        validUntil: c.validUntil,
        active: !c.active,
      });
      toast.success(c.active ? 'Kupon pasifleştirildi.' : 'Kupon aktifleştirildi.');
      refresh();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Güncellenemedi.');
    }
  }

  async function doDelete(id) {
    try {
      await api.delete(`/api/coupons/${id}`);
      toast.success('Kupon silindi.');
      setConfirmDelete(null);
      refresh();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Silinemedi.');
    }
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Kuponlar</h1>
          <p className="text-sm text-slate-500">İndirim kuponları — yüzde veya sabit tutar.</p>
        </div>
        <div className="flex items-center gap-2">
          <FilterChip active={activeFilter === 'all'} onClick={() => setActiveFilter('all')}>Tümü</FilterChip>
          <FilterChip active={activeFilter === 'active'} onClick={() => setActiveFilter('active')}>Aktif</FilterChip>
          <FilterChip active={activeFilter === 'inactive'} onClick={() => setActiveFilter('inactive')}>Pasif</FilterChip>
          <button onClick={() => setEditing({})} className="btn-primary">
            <Plus size={14} /> Yeni Kupon
          </button>
        </div>
      </div>

      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
            <tr>
              <th className="px-4 py-3">Kod</th>
              <th className="px-4 py-3">Açıklama</th>
              <th className="px-4 py-3">İndirim</th>
              <th className="px-4 py-3">Min. Sepet</th>
              <th className="px-4 py-3">Kullanım</th>
              <th className="px-4 py-3">Geçerlilik</th>
              <th className="px-4 py-3">Durum</th>
              <th className="px-4 py-3 text-right">Aksiyon</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {loading ? (
              Array.from({ length: 4 }).map((_, i) => (
                <tr key={i} className="animate-pulse">
                  <td colSpan="8" className="px-4 py-4"><div className="h-3 w-full rounded bg-slate-100" /></td>
                </tr>
              ))
            ) : coupons.length === 0 ? (
              <tr>
                <td colSpan="8" className="px-4 py-12 text-center text-sm text-slate-400">
                  Henüz kupon yok.
                </td>
              </tr>
            ) : (
              coupons.map((c) => (
                <tr key={c.id} className="hover:bg-slate-50">
                  <td className="px-4 py-2 font-mono text-xs font-bold text-brand-700">{c.code}</td>
                  <td className="px-4 py-2 text-slate-700">{c.label}</td>
                  <td className="px-4 py-2 font-semibold">
                    {c.type === 'PERCENT' ? `%${c.value}` : formatCurrency(c.value, 'TRY')}
                  </td>
                  <td className="px-4 py-2 text-slate-700">{c.minCartTotal ? formatCurrency(c.minCartTotal, 'TRY') : '—'}</td>
                  <td className="px-4 py-2 text-slate-700">
                    {c.redemptions}{c.maxRedemptions ? ` / ${c.maxRedemptions}` : ''}
                  </td>
                  <td className="px-4 py-2 text-xs text-slate-500">
                    {c.validFrom || c.validUntil ? (
                      <>
                        <p>{c.validFrom ? formatDate(c.validFrom) : 'her zaman'}</p>
                        <p className="text-[11px] text-slate-400">{c.validUntil ? `→ ${formatDate(c.validUntil)}` : '→ süresiz'}</p>
                      </>
                    ) : '—'}
                  </td>
                  <td className="px-4 py-2">
                    {c.active ? (
                      <span className="inline-flex items-center rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-semibold text-emerald-700">Aktif</span>
                    ) : (
                      <span className="inline-flex items-center rounded-full bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-600">Pasif</span>
                    )}
                  </td>
                  <td className="px-4 py-2 text-right">
                    <div className="inline-flex gap-1">
                      <button onClick={() => toggleActive(c)} className="btn-secondary text-xs" title={c.active ? 'Pasifleştir' : 'Aktifleştir'}>
                        <Power size={12} />
                      </button>
                      <button onClick={() => startEdit(c.id)} className="btn-secondary text-xs" title="Düzenle">
                        <Edit3 size={12} />
                      </button>
                      <button onClick={() => setConfirmDelete(c)} className="btn-danger text-xs" title="Sil">
                        <Trash2 size={12} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {editing && (
        <CouponFormModal
          coupon={editing.id ? editing : null}
          onClose={() => setEditing(null)}
          onSaved={refresh}
        />
      )}

      {confirmDelete && (
        <div className="fixed inset-0 z-50 grid place-items-center bg-slate-900/40 p-4">
          <div className="card w-full max-w-md p-6">
            <h3 className="text-lg font-bold tracking-tight">Kuponu Sil</h3>
            <p className="mt-2 text-sm text-slate-600">
              <strong className="font-mono">{confirmDelete.code}</strong> silinecek.
              {confirmDelete.redemptions > 0 && (
                <span className="mt-2 block rounded bg-amber-50 p-2 text-xs text-amber-800">
                  Bu kupon {confirmDelete.redemptions} kez kullanıldı — silmeye çalışırsan reddedilecek. Bunun yerine pasifleştir.
                </span>
              )}
            </p>
            <div className="mt-5 flex items-center justify-end gap-2">
              <button onClick={() => setConfirmDelete(null)} className="btn-secondary">Vazgeç</button>
              <button onClick={() => doDelete(confirmDelete.id)} className="btn-danger">
                <Trash2 size={14} /> Evet, Sil
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function FilterChip({ active, onClick, children }) {
  return (
    <button
      onClick={onClick}
      className={`rounded-full border px-3 py-1 text-xs font-medium transition-colors ${
        active ? 'border-brand-600 bg-brand-600 text-white' : 'border-slate-300 bg-white text-slate-600 hover:bg-slate-50'
      }`}
    >
      {children}
    </button>
  );
}
