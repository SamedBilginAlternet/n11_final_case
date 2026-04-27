import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api/client.js';
import { useAuth } from '../state/AuthContext.jsx';
import { useCart } from '../state/CartContext.jsx';
import RatingStars from '../components/product/RatingStars.jsx';
import { formatCurrency } from '../utils/format.js';

export default function ProductDetailPage() {
  const { slug } = useParams();
  const { isAuthed } = useAuth();
  const { addItem } = useCart();

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
    if (!isAuthed) return;
    setAdding(true);
    try {
      await addItem(product.id, quantity);
    } finally {
      setAdding(false);
    }
  }

  return (
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
        <h1 className="text-2xl font-semibold tracking-tight">{product.name}</h1>

        <RatingStars value={product.ratingAverage} count={product.ratingCount} size="md" />

        <div>
          <p className="text-sm text-gray-400 line-through">{formatCurrency(oldPrice, product.currency)}</p>
          <div className="flex items-baseline gap-3">
            <span className="text-xs font-bold uppercase tracking-wider text-n11-pink">SEPETTE</span>
            <span className="text-3xl font-extrabold text-n11-black">{formatCurrency(product.price, product.currency)}</span>
          </div>
        </div>

        <p className="leading-relaxed text-gray-600">{product.description}</p>

        <div className="flex items-center gap-3">
          <span className={inStock ? 'text-sm text-emerald-600' : 'text-sm text-red-500'}>
            {inStock ? `${product.stock} adet stokta` : 'Stokta yok'}
          </span>
        </div>

        {isAuthed ? (
          <div className="flex items-center gap-3">
            <input
              type="number"
              min={1}
              max={Math.max(1, product.stock)}
              value={quantity}
              onChange={(e) => setQuantity(Math.max(1, Number(e.target.value)))}
              className="input w-24"
            />
            <button onClick={onAddToCart} disabled={!inStock || adding} className="btn-primary">
              {adding ? 'Ekleniyor…' : 'Sepete Ekle'}
            </button>
          </div>
        ) : (
          <p className="text-sm text-gray-500">
            Sepete eklemek için{' '}
            <Link to="/login" className="font-medium text-n11-pink hover:text-n11-pinkDark">
              giriş yap
            </Link>
            .
          </p>
        )}
      </div>
    </div>
  );
}
