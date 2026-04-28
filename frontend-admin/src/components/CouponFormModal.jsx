import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { X, Save } from 'lucide-react';
import { api } from '../api/client.js';

const EMPTY = {
  code: '',
  label: '',
  type: 'PERCENT',
  value: '',
  minCartTotal: '',
  maxRedemptions: '',
  validFrom: '',
  validUntil: '',
  active: true,
};

export default function CouponFormModal({ coupon, onClose, onSaved }) {
  const isEdit = Boolean(coupon?.id);
  const [draft, setDraft] = useState(EMPTY);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (coupon) {
      setDraft({
        code: coupon.code || '',
        label: coupon.label || '',
        type: coupon.type || 'PERCENT',
        value: coupon.value ?? '',
        minCartTotal: coupon.minCartTotal ?? '',
        maxRedemptions: coupon.maxRedemptions ?? '',
        validFrom: toLocalInput(coupon.validFrom),
        validUntil: toLocalInput(coupon.validUntil),
        active: coupon.active !== false,
      });
    } else {
      setDraft(EMPTY);
    }
  }, [coupon]);

  function update(patch) {
    setDraft((d) => ({ ...d, ...patch }));
  }

  async function onSubmit(e) {
    e.preventDefault();
    setSubmitting(true);
    const body = {
      code: draft.code.trim().toUpperCase(),
      label: draft.label.trim(),
      type: draft.type,
      value: Number(draft.value),
      minCartTotal: draft.minCartTotal === '' ? null : Number(draft.minCartTotal),
      maxRedemptions: draft.maxRedemptions === '' ? null : Number(draft.maxRedemptions),
      validFrom: draft.validFrom ? new Date(draft.validFrom).toISOString() : null,
      validUntil: draft.validUntil ? new Date(draft.validUntil).toISOString() : null,
      active: draft.active,
    };
    try {
      if (isEdit) {
        await api.put(`/api/coupons/${coupon.id}`, body);
        toast.success('Kupon güncellendi.');
      } else {
        await api.post('/api/coupons', body);
        toast.success('Kupon eklendi.');
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
          <h2 className="text-lg font-bold tracking-tight">{isEdit ? 'Kuponu Düzenle' : 'Yeni Kupon'}</h2>
          <button type="button" onClick={onClose} className="rounded-md p-1 text-slate-400 hover:bg-slate-100">
            <X size={18} />
          </button>
        </header>

        <div className="grid gap-4 pt-4 sm:grid-cols-2">
          <Field label="Kupon Kodu" required hint="Büyük harf + rakam, sadece - ve _ özel karakter">
            <input
              className="input font-mono uppercase"
              required
              maxLength={40}
              pattern="[A-Za-z0-9_-]+"
              value={draft.code}
              onChange={(e) => update({ code: e.target.value })}
            />
          </Field>
          <Field label="Tür" required>
            <select
              className="input"
              required
              value={draft.type}
              onChange={(e) => update({ type: e.target.value })}
            >
              <option value="PERCENT">Yüzde (%)</option>
              <option value="FIXED">Sabit Tutar</option>
            </select>
          </Field>

          <Field label="Açıklama" required full>
            <input
              className="input"
              required
              maxLength={160}
              placeholder="örn. Yeni Yıl Sürprizi — %20"
              value={draft.label}
              onChange={(e) => update({ label: e.target.value })}
            />
          </Field>

          <Field label={draft.type === 'PERCENT' ? 'Yüzde Değeri (%)' : 'İndirim Tutarı'} required>
            <input
              type="number"
              step="0.01"
              min="0.01"
              max={draft.type === 'PERCENT' ? '100' : undefined}
              className="input"
              required
              value={draft.value}
              onChange={(e) => update({ value: e.target.value })}
            />
          </Field>
          <Field label="Min. Sepet Tutarı" hint="Boş bırakılırsa şart yok">
            <input
              type="number"
              step="0.01"
              min="0"
              className="input"
              value={draft.minCartTotal}
              onChange={(e) => update({ minCartTotal: e.target.value })}
            />
          </Field>

          <Field label="Maks. Kullanım" hint="Toplam kullanım limiti — boş = sınırsız">
            <input
              type="number"
              min="1"
              className="input"
              value={draft.maxRedemptions}
              onChange={(e) => update({ maxRedemptions: e.target.value })}
            />
          </Field>
          <Field label="Aktif">
            <label className="flex items-center gap-2 pt-2 text-sm">
              <input
                type="checkbox"
                className="h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
                checked={draft.active}
                onChange={(e) => update({ active: e.target.checked })}
              />
              <span className="text-slate-700">{draft.active ? 'Aktif (kullanılabilir)' : 'Pasif (kullanılamaz)'}</span>
            </label>
          </Field>

          <Field label="Geçerlilik Başlangıcı">
            <input
              type="datetime-local"
              className="input"
              value={draft.validFrom}
              onChange={(e) => update({ validFrom: e.target.value })}
            />
          </Field>
          <Field label="Geçerlilik Bitişi">
            <input
              type="datetime-local"
              className="input"
              value={draft.validUntil}
              onChange={(e) => update({ validUntil: e.target.value })}
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

/**
 * datetime-local input wants "YYYY-MM-DDTHH:mm" in browser-local time.
 * Convert from ISO at the entry, back to ISO at submit.  This intentionally
 * loses sub-minute precision — coupons aren't the kind of thing where
 * "valid from 12:34:56" matters.
 */
function toLocalInput(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
