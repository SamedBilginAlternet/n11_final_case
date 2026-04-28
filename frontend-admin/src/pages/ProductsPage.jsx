import { useCallback, useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { Plus, Edit3, Trash2, Search } from 'lucide-react';
import { api } from '../api/client.js';
import ProductFormModal from '../components/ProductFormModal.jsx';
import { formatCurrency } from '../utils/format.js';

export default function ProductsPage() {
  const [items, setItems] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [q, setQ] = useState('');
  const [editing, setEditing] = useState(null); // null | {} (new) | productDetail (edit)
  const [confirmDelete, setConfirmDelete] = useState(null);

  const refresh = useCallback(() => {
    setLoading(true);
    const params = new URLSearchParams({ size: '50' });
    if (q.trim()) params.set('q', q.trim());
    api
      .get(`/api/products?${params.toString()}`)
      .then((res) => setItems(res.data?.content || []))
      .catch(() => setItems([]))
      .finally(() => setLoading(false));
  }, [q]);

  useEffect(() => {
    api.get('/api/categories').then((r) => setCategories(r.data || [])).catch(() => setCategories([]));
  }, []);

  useEffect(() => {
    const t = setTimeout(refresh, 250); // debounced search
    return () => clearTimeout(t);
  }, [refresh]);

  async function startEdit(id) {
    try {
      const { data } = await api.get(`/api/products/${id}`);
      setEditing(data);
    } catch {
      toast.error('Ürün yüklenemedi');
    }
  }

  async function doDelete(id) {
    try {
      await api.delete(`/api/products/${id}`);
      toast.success('Ürün silindi.');
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
          <h1 className="text-2xl font-bold tracking-tight">Ürünler</h1>
          <p className="text-sm text-slate-500">Katalogdaki ürünleri ekle, düzenle, sil.</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="relative">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              className="input w-64 pl-8"
              placeholder="Ürün adı veya açıklama…"
              value={q}
              onChange={(e) => setQ(e.target.value)}
            />
          </div>
          <button onClick={() => setEditing({})} className="btn-primary">
            <Plus size={14} /> Yeni Ürün
          </button>
        </div>
      </div>

      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
            <tr>
              <th className="px-4 py-3">Görsel</th>
              <th className="px-4 py-3">Ad / Slug</th>
              <th className="px-4 py-3">Kategori</th>
              <th className="px-4 py-3">Fiyat</th>
              <th className="px-4 py-3">Stok</th>
              <th className="px-4 py-3">Puan</th>
              <th className="px-4 py-3 text-right">Aksiyon</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {loading ? (
              Array.from({ length: 6 }).map((_, i) => (
                <tr key={i} className="animate-pulse">
                  <td className="px-4 py-3"><div className="h-10 w-10 rounded bg-slate-100" /></td>
                  <td className="px-4 py-3"><div className="h-3 w-40 rounded bg-slate-100" /></td>
                  <td className="px-4 py-3"><div className="h-3 w-24 rounded bg-slate-100" /></td>
                  <td className="px-4 py-3"><div className="h-3 w-16 rounded bg-slate-100" /></td>
                  <td className="px-4 py-3"><div className="h-3 w-8 rounded bg-slate-100" /></td>
                  <td className="px-4 py-3"><div className="h-3 w-12 rounded bg-slate-100" /></td>
                  <td className="px-4 py-3"><div className="ml-auto h-3 w-16 rounded bg-slate-100" /></td>
                </tr>
              ))
            ) : items.length === 0 ? (
              <tr>
                <td colSpan="7" className="px-4 py-12 text-center text-sm text-slate-400">
                  Bu aramaya uygun ürün yok.
                </td>
              </tr>
            ) : (
              items.map((p) => (
                <tr key={p.id} className="hover:bg-slate-50">
                  <td className="px-4 py-2">
                    {p.imageUrl ? (
                      <img src={p.imageUrl} alt="" className="h-12 w-12 rounded object-cover" />
                    ) : (
                      <div className="h-12 w-12 rounded bg-slate-100" />
                    )}
                  </td>
                  <td className="px-4 py-2">
                    <p className="font-medium text-slate-800">{p.name}</p>
                    <p className="font-mono text-xs text-slate-500">{p.slug}</p>
                  </td>
                  <td className="px-4 py-2 text-slate-700">{p.categoryName}</td>
                  <td className="px-4 py-2 font-semibold">{formatCurrency(p.price, p.currency)}</td>
                  <td className="px-4 py-2">
                    <span className={p.stock > 0 ? 'text-slate-700' : 'text-rose-600'}>
                      {p.stock}
                    </span>
                  </td>
                  <td className="px-4 py-2 text-slate-700">
                    {Number(p.ratingAverage).toFixed(1)} <span className="text-xs text-slate-400">({p.ratingCount})</span>
                  </td>
                  <td className="px-4 py-2 text-right">
                    <div className="inline-flex gap-1">
                      <button onClick={() => startEdit(p.id)} className="btn-secondary text-xs" title="Düzenle">
                        <Edit3 size={12} />
                      </button>
                      <button onClick={() => setConfirmDelete(p)} className="btn-danger text-xs" title="Sil">
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
        <ProductFormModal
          product={editing.id ? editing : null}
          categories={categories}
          onClose={() => setEditing(null)}
          onSaved={refresh}
        />
      )}

      {confirmDelete && (
        <div className="fixed inset-0 z-50 grid place-items-center bg-slate-900/40 p-4">
          <div className="card w-full max-w-md p-6">
            <h3 className="text-lg font-bold tracking-tight">Ürünü Sil</h3>
            <p className="mt-2 text-sm text-slate-600">
              <strong>{confirmDelete.name}</strong> kalıcı olarak silinecek. Devam etmek istiyor musun?
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
