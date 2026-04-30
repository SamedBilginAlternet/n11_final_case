import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Heart } from 'lucide-react';
import { api } from '../api/client.js';
import { useCart } from '../state/CartContext.jsx';
import { useWishlist } from '../state/WishlistContext.jsx';
import RatingStars from '../components/product/RatingStars.jsx';
import RecommendationStrip from '../components/product/RecommendationStrip.jsx';
import ReviewsSection from '../components/product/ReviewsSection.jsx';
import { formatCurrency } from '../utils/format.js';

export default function ProductDetailPage() {
  const { slug } = useParams();
  const { addItem } = useCart();
  const { isFavourite, toggle: toggleWishlist } = useWishlist();

  const [product, setProduct] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [quantity, setQuantity] = useState(1);
  const [adding, setAdding] = useState(false);

  useEffect(() => {
    setLoading(true);
    api.get(`/api/products/slug/${slug}`)
      .then((res) => setProduct(res.data))
      .catch((err) => setError(err.response?.status === 404 ? 'Ürün bulunamadı' : 'Ürün yüklenemedi'))
      .finally(() => setLoading(false));
  }, [slug]);

  if (loading) return <div className="card h-64 animate-pulse bg-gray-100" />;
  if (error) return <p className="rounded bg-red-50 p-3 text-sm text-red-600">{error}</p>;
  if (!product) return null;

  const inStock = product.stock > 0;
  const oldPrice = Number(product.price) * 1.2;

  async function onAddToCart() {
    setAdding(true);
    try {
      await addItem(product, quantity);
    } finally {
      setAdding(false);
    }
  }

  function refreshAggregate() {
    api.get(`/api/products/slug/${slug}`).then((res) => setProduct(res.data)).catch(() => {});
  }

  return (
    <div className="space-y-8">
    <div className="grid gap-6 lg:grid-cols-2">
      <div className="card overflow-hidden">
        {product.imageUrl ? (
          <img src={product.imageUrl} alt={product.name} className="aspect-square w-full object-cover" />
        ) : (
          <div className="aspect-square bg-gray-100" />
        )}
      </div>

      <div className="space-y-4">
        <p className="text-xs uppercase tracking-wider text-gray-400">{product.categoryName}</p>
        <h1 className="text-xl font-semibold tracking-tight md:text-2xl">{product.name}</h1>

        <RatingStars value={product.ratingAverage} count={product.ratingCount} size="md" />

        <div>
          <p className="text-sm text-gray-400 line-through">{formatCurrency(oldPrice, product.currency)}</p>
          <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
            <span className="text-xs font-bold uppercase tracking-wider text-n11-pink">SEPETTE</span>
            <span className="text-2xl font-extrabold text-n11-black sm:text-3xl">{formatCurrency(product.price, product.currency)}</span>
          </div>
        </div>

        <p className="text-sm leading-relaxed text-gray-600 md:text-base">{product.description}</p>

        <div className="flex items-center gap-3">
          <span className={inStock ? 'text-sm text-emerald-600' : 'text-sm text-red-500'}>
            {inStock ? `${product.stock} adet stokta` : 'Stokta yok'}
          </span>
        </div>

        <div className="flex flex-wrap items-stretch gap-2 sm:gap-3">
          <input
            type="number"
            min={1}
            max={Math.max(1, product.stock)}
            value={quantity}
            onChange={(e) => setQuantity(Math.max(1, Number(e.target.value)))}
            className="input w-20 sm:w-24"
          />
          <button onClick={onAddToCart} disabled={!inStock || adding} className="btn-primary flex-1 sm:flex-none">
            {adding ? 'Ekleniyor…' : 'Sepete Ekle'}
          </button>
          <button
            type="button"
            onClick={() => toggleWishlist(product.id)}
            aria-pressed={isFavourite(product.id)}
            aria-label={isFavourite(product.id) ? 'Favorilerden çıkar' : 'Favorilere ekle'}
            className={`inline-flex w-full items-center justify-center gap-2 rounded-md border px-4 py-2 text-sm font-medium transition sm:w-auto ${
              isFavourite(product.id)
                ? 'border-n11-pink bg-n11-pink/10 text-n11-pink hover:bg-n11-pink/15'
                : 'border-gray-300 text-gray-700 hover:border-n11-pink hover:text-n11-pink'
            }`}
          >
            <Heart
              className="h-4 w-4"
              strokeWidth={1.8}
              fill={isFavourite(product.id) ? 'currentColor' : 'none'}
            />
            {isFavourite(product.id) ? 'Favorilerde' : 'Favorilere Ekle'}
          </button>
        </div>
      </div>
    </div>

    <RecommendationStrip productId={product.id} />

    <ReviewsSection productId={product.id} onAggregateChange={refreshAggregate} />
    </div>
  );
}
