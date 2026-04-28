import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Sparkles, Truck } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import HeartButton from './HeartButton.jsx';
import RatingStars from './RatingStars.jsx';
import { api } from '../../api/client.js';
import { formatCurrency } from '../../utils/format.js';

/**
 * Horizontal-scroll recommendation strip on the product detail page.
 *
 * Each card shows the standard product summary plus an optional one-sentence
 * AI explanation underneath. The reason badge animates in independently so
 * the strip never blocks on Groq — if the API returns reason=null (Groq
 * disabled or rate-limited), the cards still ship and the badge area stays
 * empty.
 */
export default function RecommendationStrip({ productId }) {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!productId) return;
    setLoading(true);
    api
      .get(`/api/products/${productId}/recommendations`)
      .then((res) => setItems(res.data || []))
      .catch(() => setItems([]))
      .finally(() => setLoading(false));
  }, [productId]);

  if (loading) {
    return (
      <section className="space-y-4">
        <Header />
        <div className="flex gap-3 overflow-x-auto pb-2">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="h-80 w-56 flex-shrink-0 animate-pulse rounded-md bg-gray-100" />
          ))}
        </div>
      </section>
    );
  }

  if (items.length === 0) return null;

  return (
    <section className="space-y-4">
      <Header />
      <div className="-mx-4 overflow-x-auto px-4 pb-2 scrollbar-thin">
        <div className="flex gap-3">
          <AnimatePresence>
            {items.map(({ product, reason }, idx) => (
              <motion.article
                key={product.id}
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.35, delay: idx * 0.06 }}
                className="group relative flex w-56 flex-shrink-0 flex-col overflow-hidden rounded-md border border-gray-200 bg-white transition-shadow hover:shadow-soft"
              >
                <HeartButton productId={product.id} />
                <Link to={`/products/${product.slug}`} className="block">
                  <div className="aspect-square overflow-hidden bg-gray-50">
                    {product.imageUrl ? (
                      <img
                        src={product.imageUrl}
                        alt={product.name}
                        loading="lazy"
                        className="h-full w-full object-cover transition-transform group-hover:scale-105"
                      />
                    ) : (
                      <div className="grid h-full place-items-center text-gray-300">no image</div>
                    )}
                  </div>
                  <div className="flex w-full items-center justify-center gap-1.5 bg-n11-black py-1.5 text-[10px] font-bold uppercase tracking-wider text-white">
                    <Truck size={12} strokeWidth={2} aria-hidden />
                    ÜCRETSİZ KARGO
                  </div>
                  <div className="space-y-2 p-3">
                    <h3 className="line-clamp-2 min-h-[2.5rem] text-sm leading-snug text-gray-800">
                      {product.name}
                    </h3>
                    <RatingStars value={product.ratingAverage} count={product.ratingCount} />
                    {reason && (
                      <motion.p
                        initial={{ opacity: 0, height: 0 }}
                        animate={{ opacity: 1, height: 'auto' }}
                        transition={{ duration: 0.3, delay: 0.2 + idx * 0.06 }}
                        className="flex items-start gap-1.5 rounded-md bg-gradient-to-br from-fuchsia-50 to-purple-50 p-2 text-[11px] leading-snug text-purple-800"
                      >
                        <Sparkles size={12} className="mt-0.5 flex-shrink-0 text-fuchsia-500" />
                        <span>{reason}</span>
                      </motion.p>
                    )}
                    <div className="pt-1">
                      <div className="flex items-baseline gap-2">
                        <span className="text-[10px] font-bold uppercase tracking-wider text-n11-pink">
                          SEPETTE
                        </span>
                        <span className="text-base font-extrabold text-n11-black">
                          {formatCurrency(product.price, product.currency)}
                        </span>
                      </div>
                    </div>
                  </div>
                </Link>
              </motion.article>
            ))}
          </AnimatePresence>
        </div>
      </div>
    </section>
  );
}

function Header() {
  return (
    <div className="flex items-center gap-2">
      <Sparkles className="text-fuchsia-500" size={20} />
      <h2 className="text-lg font-semibold tracking-tight">Sana özel öneriler</h2>
      <span className="rounded-full bg-gradient-to-r from-fuchsia-100 to-purple-100 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wider text-purple-700">
        AI destekli
      </span>
    </div>
  );
}
