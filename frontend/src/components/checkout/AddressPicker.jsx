import { Link } from 'react-router-dom';
import { MapPin, Plus, Star } from 'lucide-react';

export default function AddressPicker({ addresses, selectedId, onSelect }) {
  if (addresses.length === 0) {
    return (
      <div className="card flex items-center justify-between gap-3 p-4">
        <div className="flex items-center gap-2">
          <MapPin className="h-5 w-5 text-n11-pink" />
          <div>
            <p className="text-sm font-medium">Teslimat adresin yok</p>
            <p className="text-xs text-gray-500">Sipariş vermek için önce bir adres ekle.</p>
          </div>
        </div>
        <Link to="/account/addresses?returnTo=/checkout" className="btn-primary flex items-center gap-1.5 text-sm">
          <Plus className="h-4 w-4" /> Adres ekle
        </Link>
      </div>
    );
  }
  return (
    <div className="grid gap-2 sm:grid-cols-2">
      {addresses.map((a) => {
        const active = selectedId === a.id;
        return (
          <button
            key={a.id}
            type="button"
            onClick={() => onSelect(a.id)}
            className={`rounded-md border px-3 py-3 text-left text-sm transition ${
              active
                ? 'border-n11-pink bg-n11-pinkBg ring-2 ring-n11-pink/30'
                : 'border-gray-200 hover:border-gray-300 hover:bg-gray-50'
            }`}
          >
            <div className="flex items-center justify-between">
              <span className="font-medium text-gray-800">{a.title}</span>
              {a.defaultAddress && (
                <span className="flex items-center gap-1 text-[10px] font-semibold uppercase text-amber-600">
                  <Star className="h-3 w-3" fill="currentColor" /> Varsayılan
                </span>
              )}
            </div>
            <p className="text-xs text-gray-500">{a.recipientName} · {a.phone}</p>
            <p className="mt-1 line-clamp-2 text-xs text-gray-600">
              {a.line1}, {[a.district, a.city].filter(Boolean).join(' / ')}
            </p>
          </button>
        );
      })}
    </div>
  );
}
