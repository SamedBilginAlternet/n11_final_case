import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { X, Save } from 'lucide-react';
import { api } from '../api/client.js';

export default function CategoryFormModal({ category, onClose, onSaved }) {
  const isEdit = Boolean(category?.id);
  const [name, setName] = useState('');
  const [slug, setSlug] = useState('');
  const [slugTouched, setSlugTouched] = useState(false);
  const [description, setDescription] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (category) {
      setName(category.name || '');
      setSlug(category.slug || '');
      setDescription(category.description || '');
      setSlugTouched(true);
    } else {
      setName(''); setSlug(''); setDescription('');
      setSlugTouched(false);
    }
  }, [category]);

  function onName(e) {
    const v = e.target.value;
    setName(v);
    if (!slugTouched && !isEdit) setSlug(slugify(v));
  }

  async function onSubmit(e) {
    e.preventDefault();
    setSubmitting(true);
    const body = { name: name.trim(), slug: slug.trim(), description: description?.trim() || null };
    try {
      if (isEdit) {
        await api.put(`/api/categories/${category.id}`, body);
        toast.success('Kategori güncellendi.');
      } else {
        await api.post('/api/categories', body);
        toast.success('Kategori eklendi.');
      }
      onSaved?.();
      onClose?.();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Kaydedilemedi.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-slate-900/40 p-4">
      <form onSubmit={onSubmit} className="card w-full max-w-md p-6">
        <header className="flex items-center justify-between border-b border-slate-100 pb-3">
          <h2 className="text-lg font-bold tracking-tight">{isEdit ? 'Kategoriyi Düzenle' : 'Yeni Kategori'}</h2>
          <button type="button" onClick={onClose} className="rounded-md p-1 text-slate-400 hover:bg-slate-100">
            <X size={18} />
          </button>
        </header>

        <div className="space-y-4 pt-4">
          <Field label="Ad" required>
            <input className="input" required value={name} onChange={onName} />
          </Field>
          <Field label="Slug" required hint="URL'de görünür">
            <input
              className="input font-mono"
              required
              value={slug}
              onChange={(e) => { setSlugTouched(true); setSlug(e.target.value); }}
            />
          </Field>
          <Field label="Açıklama">
            <textarea
              className="input min-h-[80px]"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </Field>
        </div>

        <footer className="mt-5 flex items-center justify-end gap-2 border-t border-slate-100 pt-4">
          <button type="button" onClick={onClose} className="btn-secondary">İptal</button>
          <button type="submit" disabled={submitting} className="btn-primary">
            <Save size={14} /> {submitting ? 'Kaydediliyor…' : 'Kaydet'}
          </button>
        </footer>
      </form>
    </div>
  );
}

function Field({ label, hint, required, children }) {
  return (
    <label className="block">
      <span className="block text-xs font-semibold uppercase tracking-wider text-slate-600">
        {label} {required && <span className="text-rose-500">*</span>}
      </span>
      <div className="mt-1">{children}</div>
      {hint && <p className="mt-1 text-[11px] text-slate-400">{hint}</p>}
    </label>
  );
}

function slugify(s) {
  return (s || '')
    .toLowerCase()
    .replace(/[üÜ]/g, 'u').replace(/[öÖ]/g, 'o').replace(/[şŞ]/g, 's')
    .replace(/[ıİ]/g, 'i').replace(/[çÇ]/g, 'c').replace(/[ğĞ]/g, 'g')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}
