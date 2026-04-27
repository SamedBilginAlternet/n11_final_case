import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../api/client.js';
import Pagination from '../components/Pagination.jsx';
import ProductCard from '../components/ProductCard.jsx';

const PAGE_SIZE = 8;

export default function ProductListPage() {
  const [params, setParams] = useSearchParams();
  const [page, setPage] = useState(() => Number(params.get('page') || 0));
  const [categories, setCategories] = useState([]);
  const [data, setData] = useState({ content: [], totalPages: 0, totalElements: 0 });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const category = params.get('category') || '';
  const q = params.get('q') || '';

  useEffect(() => {
    api.get('/api/categories').then((res) => setCategories(res.data)).catch(() => setCategories([]));
  }, []);

  useEffect(() => {
    setPage(Number(params.get('page') || 0));
  }, [params]);

  useEffect(() => {
    setLoading(true);
    setError(null);
    const search = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
    if (category) search.set('category', category);
    if (q) search.set('q', q);
    api.get(`/api/products?${search.toString()}`)
      .then((res) => setData(res.data))
      .catch((err) => setError(err.response?.data?.message || 'Ürünler yüklenemedi'))
      .finally(() => setLoading(false));
  }, [page, category, q]);

  function setFilter(key, value) {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    next.set('page', '0');
    setParams(next);
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold tracking-tight">Tüm Ürünler</h1>
        <input
          type="search"
          placeholder="Ürün ara…"
          defaultValue={q}
          onKeyDown={(e) => {
            if (e.key === 'Enter') setFilter('q', e.currentTarget.value.trim());
          }}
          className="input max-w-xs"
        />
      </div>

      <div className="flex flex-wrap gap-2">
        <FilterChip active={!category} onClick={() => setFilter('category', '')}>
          Hepsi
        </FilterChip>
        {categories.map((c) => (
          <FilterChip key={c.slug} active={category === c.slug} onClick={() => setFilter('category', c.slug)}>
            {c.name}
          </FilterChip>
        ))}
      </div>

      {error && <p className="rounded bg-red-50 p-3 text-sm text-red-600">{error}</p>}

      {loading ? (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <div key={i} className="card h-72 animate-pulse bg-gray-100" />
          ))}
        </div>
      ) : data.content.length === 0 ? (
        <p className="text-gray-500">Aramaya uygun ürün bulunamadı.</p>
      ) : (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
          {data.content.map((product) => (
            <ProductCard key={product.id} product={product} />
          ))}
        </div>
      )}

      <Pagination
        page={page}
        totalPages={data.totalPages}
        totalElements={data.totalElements}
        onChange={(next) => {
          const merged = new URLSearchParams(params);
          merged.set('page', String(next));
          setParams(merged);
        }}
      />
    </div>
  );
}

function FilterChip({ active, onClick, children }) {
  return (
    <button
      onClick={onClick}
      className={`rounded-full border px-3 py-1 text-xs font-medium transition-colors ${
        active ? 'border-n11-pink bg-n11-pink text-white' : 'border-gray-300 bg-white text-gray-600 hover:bg-gray-50'
      }`}
    >
      {children}
    </button>
  );
}
