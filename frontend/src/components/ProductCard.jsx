import { formatCurrency } from '../utils/format.js';

export default function ProductCard({ product }) {
  return (
    <article className="card overflow-hidden transition-shadow hover:shadow-md">
      <div className="aspect-square overflow-hidden bg-slate-100">
        {product.imageUrl ? (
          <img src={product.imageUrl} alt={product.name} loading="lazy" className="h-full w-full object-cover" />
        ) : (
          <div className="flex h-full items-center justify-center text-slate-400">no image</div>
        )}
      </div>
      <div className="p-3">
        <p className="text-xs uppercase tracking-wide text-slate-400">{product.categoryName}</p>
        <h3 className="mt-1 line-clamp-2 text-sm font-medium text-slate-800">{product.name}</h3>
        <div className="mt-2 flex items-baseline justify-between">
          <span className="text-lg font-semibold text-n11-orange">{formatCurrency(product.price, product.currency)}</span>
          {product.stock > 0 ? (
            <span className="text-xs text-emerald-600">Stokta</span>
          ) : (
            <span className="text-xs text-red-500">Tükendi</span>
          )}
        </div>
      </div>
    </article>
  );
}
