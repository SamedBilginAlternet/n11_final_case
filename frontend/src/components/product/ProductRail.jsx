import { useEffect, useState } from 'react';
import { api } from '../../api/client.js';
import ProductCard from '../ProductCard.jsx';

/**
 * 3-column product grid used inside CampaignBlock — fits the 7/12 right
 * column of the campaign layout. For the new long-scroll homepage shelves
 * use {@link ProductShelf} instead, which exposes a mobile carousel + a
 * 6-column desktop grid.
 */
export default function ProductRail({ categorySlug, sort, size = 6 }) {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const params = new URLSearchParams({ size: String(size) });
    if (categorySlug) params.set('category', categorySlug);
    if (sort && sort !== 'relevance') params.set('sort', sort);
    setLoading(true);
    api
      .get(`/api/products?${params.toString()}`)
      .then((res) => setItems(res.data.content || []))
      .catch(() => setItems([]))
      .finally(() => setLoading(false));
  }, [categorySlug, sort, size]);

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
