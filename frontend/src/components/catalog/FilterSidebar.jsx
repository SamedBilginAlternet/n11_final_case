import { useEffect, useState } from 'react';
import { Star } from 'lucide-react';

/**
 * Sidebar filter panel for the catalog page.
 *
 * Controlled by the parent's URLSearchParams — every interaction calls
 * `onChange(patch)` with a partial { key: value | null } map; the parent
 * merges it into the URL state and the data fetcher re-runs.  Keeping the
 * URL as the single source of truth means deep links and back/forward
 * navigation just work, no extra state plumbing needed.
 */
export default function FilterSidebar({ facets, params, onChange }) {
  const selectedCategoryIds = parseCsvNumbers(params.get('categoryIds'));
  const minPrice = params.get('minPrice') ?? '';
  const maxPrice = params.get('maxPrice') ?? '';
  const minRating = Number(params.get('minRating') || 0);
  const inStockOnly = params.get('inStockOnly') === 'true';

  const [priceDraft, setPriceDraft] = useState({ min: minPrice, max: maxPrice });
  useEffect(() => {
    setPriceDraft({ min: minPrice, max: maxPrice });
  }, [minPrice, maxPrice]);

  const categories = facets?.categories || [];
  const totalRange = facets ? `${formatTry(facets.minPrice)} – ${formatTry(facets.maxPrice)}` : '';

  function toggleCategory(id) {
    const next = new Set(selectedCategoryIds);
    if (next.has(id)) next.delete(id); else next.add(id);
    onChange({
      categoryIds: next.size ? Array.from(next).join(',') : null,
      // Clear single-category shorthand if user starts using multi-select
      categoryId: null,
      category: null,
    });
  }

  function applyPrice() {
    onChange({
      minPrice: priceDraft.min ? String(priceDraft.min) : null,
      maxPrice: priceDraft.max ? String(priceDraft.max) : null,
    });
  }

  function clearAll() {
    onChange({
      categoryIds: null, categoryId: null, category: null,
      minPrice: null, maxPrice: null, minRating: null, inStockOnly: null,
    });
  }

  const hasActive = selectedCategoryIds.length || minPrice || maxPrice || minRating || inStockOnly;

  return (
    <aside className="space-y-6 rounded-md border border-gray-200 bg-white p-4">
      <header className="flex items-center justify-between">
        <h2 className="text-sm font-semibold uppercase tracking-wider text-gray-700">Filtrele</h2>
        {hasActive ? (
          <button onClick={clearAll} className="text-xs font-medium text-n11-pink hover:underline">
            Temizle
          </button>
        ) : null}
      </header>

      <section className="space-y-2">
        <h3 className="text-xs font-bold uppercase tracking-wider text-gray-500">Kategori</h3>
        <ul className="max-h-64 overflow-y-auto pr-1">
          {categories.map((c) => (
            <li key={c.id}>
              <label className="flex cursor-pointer items-center gap-2 py-1.5 text-sm">
                <input
                  type="checkbox"
                  checked={selectedCategoryIds.includes(c.id)}
                  onChange={() => toggleCategory(c.id)}
                  className="h-4 w-4 rounded border-gray-300 text-n11-pink focus:ring-n11-pink"
                />
                <span className="flex-1 text-gray-700">{c.name}</span>
                <span className="text-xs text-gray-400">{c.count}</span>
              </label>
            </li>
          ))}
          {categories.length === 0 && <li className="py-2 text-xs text-gray-400">Yükleniyor…</li>}
        </ul>
      </section>

      <section className="space-y-2">
        <h3 className="text-xs font-bold uppercase tracking-wider text-gray-500">Fiyat</h3>
        {totalRange && <p className="text-[11px] text-gray-400">Mevcut aralık: {totalRange}</p>}
        <div className="flex items-center gap-2">
          <input
            type="number"
            inputMode="numeric"
            placeholder="min"
            value={priceDraft.min}
            onChange={(e) => setPriceDraft({ ...priceDraft, min: e.target.value })}
            onBlur={applyPrice}
            onKeyDown={(e) => e.key === 'Enter' && applyPrice()}
            className="input w-full text-sm"
          />
          <span className="text-gray-400">–</span>
          <input
            type="number"
            inputMode="numeric"
            placeholder="max"
            value={priceDraft.max}
            onChange={(e) => setPriceDraft({ ...priceDraft, max: e.target.value })}
            onBlur={applyPrice}
            onKeyDown={(e) => e.key === 'Enter' && applyPrice()}
            className="input w-full text-sm"
          />
        </div>
      </section>

      <section className="space-y-2">
        <h3 className="text-xs font-bold uppercase tracking-wider text-gray-500">Puan</h3>
        <div className="space-y-1">
          {[4, 3, 2, 1].map((r) => (
            <button
              key={r}
              onClick={() => onChange({ minRating: minRating === r ? null : String(r) })}
              className={`flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-sm transition-colors ${
                minRating === r ? 'bg-n11-pinkBg text-n11-pink' : 'hover:bg-gray-50'
              }`}
            >
              <span className="flex">
                {Array.from({ length: 5 }).map((_, i) => (
                  <Star
                    key={i}
                    size={14}
                    className={i < r ? 'fill-amber-400 text-amber-400' : 'text-gray-300'}
                  />
                ))}
              </span>
              <span className="text-gray-600">ve üstü</span>
            </button>
          ))}
        </div>
      </section>

      <section>
        <label className="flex cursor-pointer items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={inStockOnly}
            onChange={() => onChange({ inStockOnly: inStockOnly ? null : 'true' })}
            className="h-4 w-4 rounded border-gray-300 text-n11-pink focus:ring-n11-pink"
          />
          <span className="text-gray-700">Sadece stoktakiler</span>
        </label>
      </section>
    </aside>
  );
}

function parseCsvNumbers(raw) {
  if (!raw) return [];
  return raw.split(',').map((s) => Number(s.trim())).filter((n) => !Number.isNaN(n));
}

function formatTry(v) {
  if (v == null) return '—';
  return new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY', maximumFractionDigits: 0 }).format(Number(v));
}
