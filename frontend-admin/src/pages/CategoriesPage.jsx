import { useCallback, useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { Plus, Edit3, Trash2 } from 'lucide-react';
import { api } from '../api/client.js';
import CategoryFormModal from '../components/CategoryFormModal.jsx';

export default function CategoriesPage() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(null);
  const [confirmDelete, setConfirmDelete] = useState(null);

  const refresh = useCallback(() => {
    setLoading(true);
    api
      .get('/api/categories')
      .then((res) => setItems(res.data || []))
      .catch(() => setItems([]))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { refresh(); }, [refresh]);

  async function doDelete(id) {
    try {
      await api.delete(`/api/categories/${id}`);
      toast.success('Kategori silindi.');
      setConfirmDelete(null);
      refresh();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Silinemedi.');
    }
  }

  return (
    <div className="space-y-5">
      <div className="flex items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Kategoriler</h1>
          <p className="text-sm text-slate-500">Ürün kategorileri — slug URL'lerde görünür.</p>
        </div>
        <button onClick={() => setEditing({})} className="btn-primary">
          <Plus size={14} /> Yeni Kategori
        </button>
      </div>

      <div className="card overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
            <tr>
              <th className="px-4 py-3">Ad</th>
              <th className="px-4 py-3">Slug</th>
              <th className="px-4 py-3">Açıklama</th>
              <th className="px-4 py-3 text-right">Aksiyon</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {loading ? (
              Array.from({ length: 4 }).map((_, i) => (
                <tr key={i} className="animate-pulse">
                  <td colSpan="4" className="px-4 py-4"><div className="h-3 w-full rounded bg-slate-100" /></td>
                </tr>
              ))
            ) : items.length === 0 ? (
              <tr>
                <td colSpan="4" className="px-4 py-12 text-center text-sm text-slate-400">
                  Henüz kategori yok.
                </td>
              </tr>
            ) : (
              items.map((c) => (
                <tr key={c.id} className="hover:bg-slate-50">
                  <td className="px-4 py-2 font-medium text-slate-800">{c.name}</td>
                  <td className="px-4 py-2 font-mono text-xs text-slate-500">{c.slug}</td>
                  <td className="px-4 py-2 text-slate-700">
                    {c.description || <span className="text-slate-400">—</span>}
                  </td>
                  <td className="px-4 py-2 text-right">
                    <div className="inline-flex gap-1">
                      <button onClick={() => setEditing(c)} className="btn-secondary text-xs"><Edit3 size={12} /></button>
                      <button onClick={() => setConfirmDelete(c)} className="btn-danger text-xs"><Trash2 size={12} /></button>
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {editing && (
        <CategoryFormModal
          category={editing.id ? editing : null}
          onClose={() => setEditing(null)}
          onSaved={refresh}
        />
      )}

      {confirmDelete && (
        <div className="fixed inset-0 z-50 grid place-items-center bg-slate-900/40 p-4">
          <div className="card w-full max-w-md p-6">
            <h3 className="text-lg font-bold tracking-tight">Kategoriyi Sil</h3>
            <p className="mt-2 text-sm text-slate-600">
              <strong>{confirmDelete.name}</strong> silinecek. Bu kategoride ürün varsa backend reddeder.
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
