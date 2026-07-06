import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Heart, ShoppingCart, Trash2 } from 'lucide-react';
import toast from 'react-hot-toast';
import { motion, AnimatePresence } from 'framer-motion';
import { api } from '../api/client.js';
import { useAuth } from '../state/AuthContext.jsx';
import { useCart } from '../state/CartContext.jsx';
import { useWishlist } from '../state/WishlistContext.jsx';
import { loadGuestWishlist } from '../utils/guestWishlist.js';
import { formatCurrency } from '../utils/format.js';
import SafeImage from '../components/SafeImage.jsx';

export default function WishlistPage() {
  const { isAuthed } = useAuth();
  const { items: serverItems, refresh, toggle } = useWishlist();
  const { addItem } = useCart();
  const [guestItems, setGuestItems] = useState([]);
  const [loading, setLoading] = useState(true);

  // Hydrate guest wishlist from /api/products/{id} so we can render names/prices.
  useEffect(() => {
    let cancelled = false;
    async function loadGuest() {
      const ids = loadGuestWishlist();
      if (ids.length === 0) {
        if (!cancelled) {
          setGuestItems([]);
          setLoading(false);
        }
        return;
      }
      const results = await Promise.all(
        ids.map((entry) =>
          api.get(`/api/products/${entry.productId}`)
            .then((res) => ({ ...res.data, addedAt: entry.addedAt }))
            .catch(() => null),
        ),
      );
      if (!cancelled) {
        setGuestItems(results.filter(Boolean));
        setLoading(false);
      }
    }

    if (!isAuthed) {
      setLoading(true);
      loadGuest();
    } else {
      setLoading(false);
      refresh();
    }
    return () => { cancelled = true; };
  }, [isAuthed, refresh]);

  const items = isAuthed
    ? serverItems
    : guestItems.map((p) => ({
        productId: p.id,
        slug: p.slug,
        name: p.name,
        imageUrl: p.imageUrl,
        price: p.price,
        currency: p.currency,
        stock: p.stock,
        addedAt: p.addedAt,
      }));

  async function onMoveToCart(item) {
    try {
      await addItem({ id: item.productId, name: item.name, slug: item.slug, price: item.price, currency: item.currency, imageUrl: item.imageUrl, stock: item.stock }, 1);
      await toggle(item.productId);
      toast.success('Sepete eklendi, favorilerden çıkarıldı');
    } catch (err) {
      toast.error(err.response?.data?.message || 'Sepete eklenemedi');
    }
  }

  if (loading) return <div className="card h-32 animate-pulse bg-gray-100" />;

  return (
    <div className="space-y-4">
      <header className="flex items-center justify-between">
        <h1 className="flex items-center gap-2 text-2xl font-semibold tracking-tight">
          <Heart className="h-5 w-5 text-n11-pink" fill="currentColor" /> Favorilerim
        </h1>
        <span className="text-sm text-gray-500">{items.length} ürün</span>
      </header>

      {items.length === 0 ? (
        <div className="card flex flex-col items-center gap-3 py-16 text-center">
          <Heart className="h-12 w-12 text-gray-300" />
          <p className="text-gray-500">Henüz favori ürünün yok.</p>
          <Link to="/" className="btn-primary">Alışverişe başla</Link>
        </div>
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <AnimatePresence>
            {items.map((item) => (
              <motion.article
                key={item.productId}
                layout
                initial={{ opacity: 0, scale: 0.9 }}
                animate={{ opacity: 1, scale: 1 }}
                exit={{ opacity: 0, scale: 0.85 }}
                className="card overflow-hidden"
              >
                <Link to={`/products/${item.slug}`} className="block">
                  <div className="aspect-square overflow-hidden bg-gray-50">
                    <SafeImage src={item.imageUrl} alt={item.name} loading="lazy" className="h-full w-full object-cover" />
                  </div>
                  <div className="space-y-1 p-3">
                    <h3 className="line-clamp-2 min-h-[2.5rem] text-sm leading-snug text-gray-800">{item.name}</h3>
                    <p className="text-base font-extrabold text-n11-black">
                      {formatCurrency(item.price, item.currency)}
                    </p>
                  </div>
                </Link>
                <div className="flex items-center justify-between gap-2 border-t border-gray-100 p-2">
                  <button
                    onClick={() => onMoveToCart(item)}
                    disabled={!item.stock}
                    className="btn-primary flex flex-1 items-center justify-center gap-1.5 text-xs"
                  >
                    <ShoppingCart className="h-3.5 w-3.5" />
                    {item.stock ? 'Sepete Ekle' : 'Stokta Yok'}
                  </button>
                  <button
                    onClick={() => toggle(item.productId)}
                    aria-label="Favoriden çıkar"
                    className="grid h-8 w-8 place-items-center rounded-md text-gray-400 hover:bg-gray-50 hover:text-red-500"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>
              </motion.article>
            ))}
          </AnimatePresence>
        </div>
      )}
    </div>
  );
}
