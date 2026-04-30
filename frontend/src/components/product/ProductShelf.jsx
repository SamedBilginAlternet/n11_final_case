import { useEffect, useState } from 'react';
import { api } from '../../api/client.js';
import ProductCard from '../ProductCard.jsx';

/**
 * Long-format product shelf for the homepage's category sections.
 *
 * Mobile (<md): horizontal swipe carousel — 1.5 cards visible to hint at
 * overflow, matches what users expect from marketplace apps.
 * Desktop (>=md): wide grid (3 → 6 columns) so a 6-item shelf renders as
 * a single row across the full content width.
 *
 * Independent from {@link ProductRail} on purpose — the rail is constrained
 * to the right column of a CampaignBlock (3 cols, 2 rows), the shelf is
 * full-width (1 row, 6 cols).
 */
export default function ProductShelf({ categorySlug, sort, size = 6 }) {
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
        {Array.from({ length: 6 }).map((_, i) => (
          <div key={i} className="h-72 w-44 flex-shrink-0 animate-pulse rounded-md bg-gray-100 sm:h-80 sm:w-56" />
        ))}
      </div>
    );
  }

  if (items.length === 0) {
    return <p className="text-sm text-gray-500">Henüz ürün yok.</p>;
  }

  return (
    <>
      <div className="flex gap-3 overflow-x-auto pb-2 md:hidden">
        {items.map((p) => (
          <div key={p.id} className="w-44 flex-shrink-0">
            <ProductCard product={p} />
          </div>
        ))}
      </div>
      <div className="hidden grid-cols-3 gap-3 md:grid lg:grid-cols-6">
        {items.map((p) => (
          <ProductCard key={p.id} product={p} />
        ))}
      </div>
    </>
  );
}
