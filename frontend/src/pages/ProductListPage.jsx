import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../api/client.js';
import Pagination from '../components/Pagination.jsx';
import ProductCard from '../components/ProductCard.jsx';
import FilterSidebar from '../components/catalog/FilterSidebar.jsx';

const PAGE_SIZE = 12;

const SORT_OPTIONS = [
  { value: 'relevance', label: 'En alakalı' },
  { value: 'price_asc', label: 'Fiyat: Artan' },
  { value: 'price_desc', label: 'Fiyat: Azalan' },
  { value: 'rating', label: 'Puan' },
  { value: 'newest', label: 'En yeni' },
];

export default function ProductListPage() {
  const [params, setParams] = useSearchParams();
  const [data, setData] = useState({ content: [], totalPages: 0, totalElements: 0 });
  const [facets, setFacets] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // Build the API query string from URL params, dropping empty values so the
  // backend gets a clean param set instead of "minPrice=&maxPrice=".
  const apiQuery = useMemo(() => buildApiQuery(params, PAGE_SIZE), [params]);

  useEffect(() => {
    setLoading(true);
    setError(null);
    Promise.all([
      api.get(`/api/products?${apiQuery.list}`).then((res) => res.data),
      api.get(`/api/products/facets?${apiQuery.facets}`).then((res) => res.data).catch(() => null),
    ])
      .then(([page, fac]) => {
        setData(page);
        setFacets(fac);
      })
      .catch((err) => setError(err.response?.data?.message || 'Ürünler yüklenemedi'))
      .finally(() => setLoading(false));
  }, [apiQuery.list, apiQuery.facets]);

  function patchParams(patch) {
    const next = new URLSearchParams(params);
    Object.entries(patch).forEach(([k, v]) => {
      if (v == null || v === '') next.delete(k);
      else next.set(k, String(v));
    });
    next.set('page', '0'); // any filter change resets to page 1
    setParams(next);
  }

  const q = params.get('q') || '';
  const sort = params.get('sort') || 'relevance';
  const page = Number(params.get('page') || 0);
  const total = facets?.totalMatches ?? data.totalElements;

  return (
    <div className="grid gap-6 lg:grid-cols-[260px_1fr]">
      <div className="hidden lg:block">
        <FilterSidebar facets={facets} params={params} onChange={patchParams} />
      </div>

      <div className="space-y-4">
        <header className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">
              {q ? `"${q}" için sonuçlar` : 'Tüm Ürünler'}
            </h1>
            <p className="text-sm text-gray-500">{total} ürün bulundu</p>
          </div>
          <div className="flex items-center gap-2">
            <label className="text-xs text-gray-500">Sırala:</label>
            <select
              value={sort}
              onChange={(e) => patchParams({ sort: e.target.value === 'relevance' ? null : e.target.value })}
              className="input w-40 text-sm"
            >
              {SORT_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>{o.label}</option>
              ))}
            </select>
          </div>
        </header>

        <div className="lg:hidden">
          <details className="rounded-md border border-gray-200 bg-white p-3">
            <summary className="cursor-pointer text-sm font-semibold text-gray-700">Filtreler</summary>
            <div className="mt-3">
              <FilterSidebar facets={facets} params={params} onChange={patchParams} />
            </div>
          </details>
        </div>

        {error && <p className="rounded bg-red-50 p-3 text-sm text-red-600">{error}</p>}

        {loading ? (
          <div className="grid grid-cols-2 gap-3 sm:gap-4 md:grid-cols-3 xl:grid-cols-4">
            {Array.from({ length: PAGE_SIZE }).map((_, i) => (
              <div key={i} className="card h-72 animate-pulse bg-gray-100" />
            ))}
          </div>
        ) : data.content.length === 0 ? (
          <div className="rounded-md border border-dashed border-gray-300 bg-gray-50 p-10 text-center">
            <p className="text-sm text-gray-600">Aramaya uygun ürün bulunamadı.</p>
            <p className="mt-1 text-xs text-gray-400">Filtreleri biraz gevşetmeyi dene.</p>
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-3 sm:gap-4 md:grid-cols-3 xl:grid-cols-4">
            {data.content.map((product) => (
              <ProductCard key={product.id} product={product} />
            ))}
          </div>
        )}

        <Pagination
          page={page}
          totalPages={data.totalPages}
          totalElements={data.totalElements}
          onChange={(nextPage) => {
            const merged = new URLSearchParams(params);
            merged.set('page', String(nextPage));
            setParams(merged);
          }}
        />
      </div>
    </div>
  );
}

function buildApiQuery(params, pageSize) {
  const list = new URLSearchParams();
  const facets = new URLSearchParams();
  // Whitelisted keys — anything else lurking in the URL is ignored.
  const filterKeys = ['q', 'category', 'categoryId', 'categoryIds', 'minPrice', 'maxPrice', 'minRating', 'inStockOnly'];
  filterKeys.forEach((k) => {
    const v = params.get(k);
    if (v != null && v !== '') {
      list.set(k, v);
      facets.set(k, v);
    }
  });
  const sort = params.get('sort');
  if (sort && sort !== 'relevance') list.set('sort', sort);
  list.set('page', String(Number(params.get('page') || 0)));
  list.set('size', String(pageSize));
  return { list: list.toString(), facets: facets.toString() };
}
