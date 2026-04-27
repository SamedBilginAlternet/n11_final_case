import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { Plus, Star, Trash2, Pencil, X } from 'lucide-react';
import { api } from '../api/client.js';

const EMPTY = {
  id: null,
  title: '',
  recipientName: '',
  phone: '',
  line1: '',
  city: '',
  district: '',
  postalCode: '',
  defaultAddress: false,
};

export default function AddressBookPage() {
  const [addresses, setAddresses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(null); // null | EMPTY | existing

  async function load() {
    try {
      const { data } = await api.get('/api/addresses');
      setAddresses(data);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  async function onSave(e) {
    e.preventDefault();
    const body = { ...editing };
    delete body.id;
    try {
      if (editing.id) {
        await api.put(`/api/addresses/${editing.id}`, body);
        toast.success('Adres güncellendi');
      } else {
        await api.post('/api/addresses', body);
        toast.success('Adres eklendi');
      }
      setEditing(null);
      await load();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Kayıt başarısız');
    }
  }

  async function onDelete(id) {
    if (!window.confirm('Bu adresi silmek istediğine emin misin?')) return;
    try {
      await api.delete(`/api/addresses/${id}`);
      toast('Adres silindi');
      await load();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Silinemedi');
    }
  }

  async function onMakeDefault(addr) {
    try {
      await api.put(`/api/addresses/${addr.id}`, { ...addr, defaultAddress: true });
      await load();
    } catch (err) {
      toast.error(err.response?.data?.message || 'İşlem başarısız');
    }
  }

  if (loading) return <div className="card h-32 animate-pulse bg-gray-100" />;

  return (
    <div className="space-y-4">
      <header className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold tracking-tight">Adres Defterim</h1>
        <button onClick={() => setEditing({ ...EMPTY })} className="btn-primary flex items-center gap-1.5">
          <Plus className="h-4 w-4" /> Yeni Adres
        </button>
      </header>

      {addresses.length === 0 && !editing && (
        <div className="card flex flex-col items-center gap-3 py-12 text-center">
          <p className="text-gray-500">Henüz kayıtlı adresin yok.</p>
          <button onClick={() => setEditing({ ...EMPTY })} className="btn-primary">
            İlk adresi ekle
          </button>
        </div>
      )}

      {editing && (
        <AddressForm
          value={editing}
          onChange={setEditing}
          onSubmit={onSave}
          onCancel={() => setEditing(null)}
        />
      )}

      <div className="grid gap-3 md:grid-cols-2">
        {addresses.map((a) => (
          <AddressCard
            key={a.id}
            address={a}
            onEdit={() => setEditing({ ...a })}
            onDelete={() => onDelete(a.id)}
            onMakeDefault={() => onMakeDefault(a)}
          />
        ))}
      </div>
    </div>
  );
}

function AddressCard({ address, onEdit, onDelete, onMakeDefault }) {
  return (
    <article className="card relative space-y-2 p-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold text-gray-800">{address.title}</h3>
          <p className="text-xs text-gray-500">{address.recipientName} · {address.phone}</p>
        </div>
        {address.defaultAddress && (
          <span className="flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-[11px] font-semibold text-amber-700">
            <Star className="h-3 w-3" fill="currentColor" /> Varsayılan
          </span>
        )}
      </div>
      <p className="text-sm text-gray-700">{address.line1}</p>
      <p className="text-xs text-gray-500">
        {[address.district, address.city, address.postalCode].filter(Boolean).join(' · ')}
      </p>
      <div className="flex items-center gap-2 pt-2 text-xs">
        {!address.defaultAddress && (
          <button onClick={onMakeDefault} className="text-n11-pink hover:underline">
            Varsayılan yap
          </button>
        )}
        <button onClick={onEdit} className="ml-auto flex items-center gap-1 text-gray-500 hover:text-gray-700">
          <Pencil className="h-3.5 w-3.5" /> Düzenle
        </button>
        <button onClick={onDelete} className="flex items-center gap-1 text-red-500 hover:text-red-600">
          <Trash2 className="h-3.5 w-3.5" /> Sil
        </button>
      </div>
    </article>
  );
}

function AddressForm({ value, onChange, onSubmit, onCancel }) {
  const set = (k) => (e) => onChange({ ...value, [k]: e.target.value });
  return (
    <form onSubmit={onSubmit} className="card space-y-3 p-4">
      <header className="flex items-center justify-between">
        <h2 className="text-sm font-semibold">{value.id ? 'Adresi düzenle' : 'Yeni adres'}</h2>
        <button type="button" onClick={onCancel} className="text-gray-400 hover:text-gray-600">
          <X className="h-4 w-4" />
        </button>
      </header>
      <div className="grid gap-3 md:grid-cols-2">
        <Field label="Başlık (Ev, Ofis...)" required maxLength={60} value={value.title} onChange={set('title')} />
        <Field label="Alıcı Adı" required maxLength={120} value={value.recipientName} onChange={set('recipientName')} />
        <Field label="Telefon" required maxLength={32} value={value.phone} onChange={set('phone')} placeholder="+905551234567" />
        <Field label="Posta Kodu" maxLength={16} value={value.postalCode} onChange={set('postalCode')} />
        <Field className="md:col-span-2" label="Adres" required maxLength={255} value={value.line1} onChange={set('line1')} />
        <Field label="İl" required maxLength={80} value={value.city} onChange={set('city')} />
        <Field label="İlçe" maxLength={80} value={value.district} onChange={set('district')} />
      </div>
      <label className="flex items-center gap-2 text-sm text-gray-600">
        <input
          type="checkbox"
          checked={value.defaultAddress}
          onChange={(e) => onChange({ ...value, defaultAddress: e.target.checked })}
        />
        Varsayılan adres olarak ayarla
      </label>
      <div className="flex justify-end gap-2">
        <button type="button" onClick={onCancel} className="btn-outline">İptal</button>
        <button type="submit" className="btn-primary">Kaydet</button>
      </div>
    </form>
  );
}

function Field({ label, className = '', ...props }) {
  return (
    <label className={`block ${className}`}>
      <span className="mb-1 block text-xs font-medium text-gray-500">{label}</span>
      <input className="input w-full" {...props} />
    </label>
  );
}
