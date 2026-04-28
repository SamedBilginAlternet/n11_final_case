import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { X, Save } from 'lucide-react';
import { api } from '../api/client.js';

const EMPTY = {
  name: '',
  slug: '',
  description: '',
  price: '',
  currency: 'TRY',
  stock: 0,
  imageUrl: '',
  categoryId: '',
};

/**
 * Single create+edit modal — same form, same payload, only the URL/method
 * differs.  Auto-slugifies from the name on first edit (admin can still
 * override) so the common case "type the name → tab → save" works.
 */
export default function ProductFormModal({ product, categories, onClose, onSaved }) {
  const isEdit = Boolean(product?.id);
  const [draft, setDraft] = useState(EMPTY);
  const [submitting, setSubmitting] = useState(false);
  const [slugTouched, setSlugTouched] = useState(false);

  useEffect(() => {
    if (product) {
      setDraft({
        name: product.name || '',
        slug: product.slug || '',
        description: product.description || '',
        price: product.price ?? '',
        currency: product.currency || 'TRY',
        stock: product.stock ?? 0,
        imageUrl: product.imageUrl || '',
        categoryId: product.categoryId ?? '',
      });
      setSlugTouched(true);
    } else {
      setDraft(EMPTY);
      setSlugTouched(false);
    }
  }, [product]);

  function update(patch) {
    setDraft((d) => {
      const next = { ...d, ...patch };
      if ('name' in patch && !slugTouched && !isEdit) {
        next.slug = slugify(patch.name);
      }
      return next;
    });
  }

  async function onSubmit(e) {
    e.preventDefault();
    if (!draft.categoryId) {
      toast.error('Kategori seç');
      return;
    }
    setSubmitting(true);
    const body = {
      name: draft.name.trim(),
      slug: draft.slug.trim(),
      description: draft.description?.trim() || null,
      price: Number(draft.price),
      currency: (draft.currency || 'TRY').toUpperCase(),
      stock: Number(draft.stock) || 0,
      imageUrl: draft.imageUrl?.trim() || null,
      categoryId: Number(draft.categoryId),
    };
    try {
      if (isEdit) {
        await api.put(`/api/products/${product.id}`, body);
        toast.success('Ürün güncellendi.');
      } else {
        await api.post('/api/products', body);
        toast.success('Ürün eklendi.');
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
      <form onSubmit={onSubmit} className="card max-h-[92vh] w-full max-w-2xl overflow-y-auto p-6">
        <header className="flex items-center justify-between border-b border-slate-100 pb-3">
          <h2 className="text-lg font-bold tracking-tight">{isEdit ? 'Ürünü Düzenle' : 'Yeni Ürün'}</h2>
          <button type="button" onClick={onClose} className="rounded-md p-1 text-slate-400 hover:bg-slate-100">
            <X size={18} />
          </button>
        </header>

        <div className="grid gap-4 pt-4 sm:grid-cols-2">
          <Field label="Ad" required>
            <input className="input" required value={draft.name} onChange={(e) => update({ name: e.target.value })} />
          </Field>
          <Field label="Slug" required hint="URL'de görünür — boşluk yerine -">
            <input
              className="input font-mono"
              required
              value={draft.slug}
              onChange={(e) => {
                setSlugTouched(true);
                update({ slug: e.target.value });
              }}
            />
          </Field>

          <Field label="Açıklama" full>
            <textarea
              className="input min-h-[100px]"
              value={draft.description}
              onChange={(e) => update({ description: e.target.value })}
            />
          </Field>

          <Field label="Kategori" required>
            <select
              className="input"
              required
              value={draft.categoryId}
              onChange={(e) => update({ categoryId: e.target.value })}
            >
              <option value="">Seç…</option>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
          </Field>
          <Field label="Fiyat" required>
            <input
              type="number"
              step="0.01"
              min="0"
              className="input"
              required
              value={draft.price}
              onChange={(e) => update({ price: e.target.value })}
            />
          </Field>
          <Field label="Para Birimi">
            <input
              className="input uppercase"
              maxLength={3}
              value={draft.currency}
              onChange={(e) => update({ currency: e.target.value.toUpperCase() })}
            />
          </Field>
          <Field label="Stok" required>
            <input
              type="number"
              min="0"
              className="input"
              required
              value={draft.stock}
              onChange={(e) => update({ stock: e.target.value })}
            />
          </Field>
          <Field label="Görsel URL" full hint="Tam URL (https://...)">
            <input
              className="input"
              value={draft.imageUrl}
              onChange={(e) => update({ imageUrl: e.target.value })}
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

function Field({ label, hint, required, full, children }) {
  return (
    <label className={`block ${full ? 'sm:col-span-2' : ''}`}>
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
