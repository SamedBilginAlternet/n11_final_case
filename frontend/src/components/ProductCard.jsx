import { Link } from 'react-router-dom';
import { Truck } from 'lucide-react';
import HeartButton from './product/HeartButton.jsx';
import RatingStars from './product/RatingStars.jsx';
import SafeImage from './SafeImage.jsx';
import { formatCurrency } from '../utils/format.js';

export default function ProductCard({ product, badge = 'KUPONLU ÜRÜN', campaign = '4 AL 3 ÖDE', oldPrice }) {
  const previousPrice = oldPrice ?? Number(product.price) * 1.2;

  return (
    <article className="group relative flex h-full flex-col overflow-hidden rounded-md border border-gray-200 bg-white transition-shadow hover:shadow-soft">
      {badge && (
        <span className="badge-black absolute left-2 top-2 z-10 px-2 py-1 text-[10px]">{badge}</span>
      )}
      <HeartButton productId={product.id} />

      <Link to={`/products/${product.slug}`} className="block">
        <div className="aspect-square overflow-hidden bg-gray-50">
          <SafeImage src={product.imageUrl} alt={product.name} loading="lazy" className="h-full w-full object-cover transition-transform group-hover:scale-105" />
        </div>

        <div className="flex w-full items-center justify-center gap-1.5 bg-n11-black py-1.5 text-[11px] font-bold uppercase tracking-wider text-white">
          <Truck size={14} strokeWidth={2} aria-hidden />
          ÜCRETSİZ KARGO
        </div>

        <div className="space-y-2 p-3">
          <h3 className="line-clamp-2 min-h-[2.5rem] text-sm leading-snug text-gray-800">
            {product.name}
          </h3>

          <RatingStars value={product.ratingAverage} count={product.ratingCount} />

          {campaign && <span className="badge-pink">{campaign}</span>}

          <div className="pt-1">
            <p className="text-xs text-gray-400 line-through">
              {formatCurrency(previousPrice, product.currency)}
            </p>
            <div className="flex items-baseline gap-2">
              <span className="text-[10px] font-bold uppercase tracking-wider text-n11-pink">SEPETTE</span>
              <span className="text-base font-extrabold text-n11-black">
                {formatCurrency(product.price, product.currency)}
              </span>
            </div>
          </div>
        </div>
      </Link>
    </article>
  );
}
