import { useEffect, useState } from 'react';
import { api } from '../../api/client.js';
import ProductCard from '../ProductCard.jsx';

export default function ProductRail({ categorySlug, size = 6 }) {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const params = new URLSearchParams({ size: String(size) });
    if (categorySlug) params.set('category', categorySlug);
    api
      .get(`/api/products?${params.toString()}`)
      .then((res) => setItems(res.data.content || []))
      .catch(() => setItems([]))
      .finally(() => setLoading(false));
  }, [categorySlug, size]);

  if (loading) {
    return (
      <div className="flex gap-3 overflow-x-auto pb-2">
        {Array.from({ length: 4 }).map((_, i) => (
          <div key={i} className="h-80 w-56 flex-shrink-0 animate-pulse rounded-md bg-gray-100" />
        ))}
      </div>
    );
  }

  if (items.length === 0) {
    return <p className="text-sm text-gray-500">Henüz ürün yok.</p>;
  }

  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-3 xl:grid-cols-3">
      {items.map((p) => (
        <ProductCard key={p.id} product={p} />
      ))}
    </div>
  );
}
