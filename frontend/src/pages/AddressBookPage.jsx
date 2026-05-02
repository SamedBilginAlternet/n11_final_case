import { useEffect, useMemo, useState } from 'react';
import toast from 'react-hot-toast';
import { Plus, Star, Trash2, Pencil, X, Home, Briefcase, MapPin } from 'lucide-react';
import { api } from '../api/client.js';
import trLocations from '../data/tr-locations.json';

const ADDRESS_TYPES = [
  { value: 'HOME',   label: 'Ev',    Icon: Home },
  { value: 'OFFICE', label: 'Ofis',  Icon: Briefcase },
  { value: 'OTHER',  label: 'Diğer', Icon: MapPin },
];

// Backend (TrPhoneValidator) accepts the same shape: strip everything that
// isn't a digit, then require optional `90`, optional `0`, leading `5`, and
// 9 trailing digits.  Mirroring it client-side gives instant feedback and
// keeps the validator and the UI in sync.
const TR_PHONE_RE = /^(90)?0?5\d{9}$/;
function isValidTrPhone(input) {
  if (!input) return false;
  return TR_PHONE_RE.test(input.replace(/\D/g, ''));
}

const EMPTY = {
  id: null,
  addressType: 'HOME',
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
    if (!isValidTrPhone(editing.phone)) {
      toast.error('Geçerli bir TR cep numarası gir (örn. 0555 123 45 67)');
      return;
    }
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
  const typeMeta = ADDRESS_TYPES.find((t) => t.value === address.addressType) || ADDRESS_TYPES[2];
  const TypeIcon = typeMeta.Icon;
  return (
    <article className="card relative space-y-2 p-4">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-start gap-2">
          <span
            className="mt-0.5 inline-flex h-7 w-7 items-center justify-center rounded-full bg-n11-pinkBg text-n11-pink"
            title={typeMeta.label}
          >
            <TypeIcon className="h-4 w-4" />
          </span>
          <div>
            <h3 className="text-sm font-semibold text-gray-800">{address.title}</h3>
            <p className="text-xs text-gray-500">{address.recipientName} · {address.phone}</p>
          </div>
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

  // Districts cascade off the picked city; the district list is reset whenever
  // the city changes so a stale "Kadıköy" can't survive a switch to Ankara.
  const districts = useMemo(() => {
    const row = trLocations.find((c) => c.city === value.city);
    return row ? row.districts : [];
  }, [value.city]);

  function onCityChange(e) {
    const next = e.target.value;
    onChange({ ...value, city: next, district: '' });
  }

  // Picking a type also seeds the title so the most common case (Ev/Ofis)
  // skips an extra keystroke.  If the user has already typed a custom title
  // we don't clobber it — only the empty/default-matching cases get
  // overwritten.
  function onTypeChange(nextType) {
    const matchedDefault = ADDRESS_TYPES.find((t) => t.label === value.title);
    const shouldSeed = !value.title || matchedDefault;
    const seededTitle = ADDRESS_TYPES.find((t) => t.value === nextType).label;
    onChange({
      ...value,
      addressType: nextType,
      title: shouldSeed ? seededTitle : value.title,
    });
  }

  const phoneTouched = value.phone.length > 0;
  const phoneValid = isValidTrPhone(value.phone);

  return (
    <form onSubmit={onSubmit} className="card space-y-3 p-4">
      <header className="flex items-center justify-between">
        <h2 className="text-sm font-semibold">{value.id ? 'Adresi düzenle' : 'Yeni adres'}</h2>
        <button type="button" onClick={onCancel} className="text-gray-400 hover:text-gray-600">
          <X className="h-4 w-4" />
        </button>
      </header>
      <div>
        <span className="mb-1 block text-xs font-medium text-gray-500">Adres tipi</span>
        <div className="flex flex-wrap gap-2">
          {ADDRESS_TYPES.map(({ value: v, label, Icon }) => {
            const active = value.addressType === v;
            return (
              <button
                key={v}
                type="button"
                onClick={() => onTypeChange(v)}
                className={`flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs font-medium transition ${
                  active
                    ? 'border-n11-pink bg-n11-pinkBg text-n11-pink ring-2 ring-n11-pink/30'
                    : 'border-gray-200 bg-white text-gray-600 hover:border-gray-300 hover:bg-gray-50'
                }`}
                aria-pressed={active}
              >
                <Icon className="h-3.5 w-3.5" />
                {label}
              </button>
            );
          })}
        </div>
      </div>
      <div className="grid gap-3 md:grid-cols-2">
        <Field label="Başlık (örn. Ev, Annemin evi)" required maxLength={60} value={value.title} onChange={set('title')} />
        <Field label="Alıcı Adı" required maxLength={120} value={value.recipientName} onChange={set('recipientName')} />
        <PhoneField
          label="Telefon"
          required
          maxLength={32}
          value={value.phone}
          onChange={set('phone')}
          touched={phoneTouched}
          valid={phoneValid}
        />
        <Field label="Posta Kodu" maxLength={16} value={value.postalCode} onChange={set('postalCode')} />
        <Field className="md:col-span-2" label="Adres" required maxLength={255} value={value.line1} onChange={set('line1')} />
        <SelectField label="İl" required value={value.city} onChange={onCityChange}>
          <option value="">Seçiniz…</option>
          {trLocations.map((c) => (
            <option key={c.city} value={c.city}>{c.city}</option>
          ))}
        </SelectField>
        <SelectField
          label="İlçe"
          value={value.district}
          onChange={set('district')}
          disabled={!value.city}
        >
          <option value="">{value.city ? 'Seçiniz…' : 'Önce il seç'}</option>
          {districts.map((d) => (
            <option key={d} value={d}>{d}</option>
          ))}
        </SelectField>
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

function SelectField({ label, className = '', children, ...props }) {
  return (
    <label className={`block ${className}`}>
      <span className="mb-1 block text-xs font-medium text-gray-500">{label}</span>
      <select className="input w-full" {...props}>{children}</select>
    </label>
  );
}

function PhoneField({ label, touched, valid, className = '', ...props }) {
  // Inline cue: red border + helper line once the user has typed something
  // but the digits don't add up to a TR mobile.  Untouched fields stay
  // neutral so the form doesn't yell at empty state.
  const showError = touched && !valid;
  return (
    <label className={`block ${className}`}>
      <span className="mb-1 block text-xs font-medium text-gray-500">{label}</span>
      <input
        type="tel"
        className={`input w-full ${showError ? 'border-red-400 focus:ring-red-300' : ''}`}
        placeholder="0555 123 45 67"
        autoComplete="tel"
        {...props}
      />
      {showError && (
        <span className="mt-1 block text-[11px] text-red-500">
          Geçerli bir TR cep numarası gir (örn. 0555 123 45 67)
        </span>
      )}
    </label>
  );
}
