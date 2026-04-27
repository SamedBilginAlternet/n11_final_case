import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import toast from 'react-hot-toast';
import { api } from '../api/client.js';
import { useAuth } from './AuthContext.jsx';
import {
  loadGuestCart,
  addItemToGuestCart,
  updateGuestQuantity,
  removeFromGuestCart,
  clearGuestCart,
  mergeIntoServerCart,
} from '../utils/guestCart.js';

const CartContext = createContext(null);

const EMPTY_CART = {
  id: null,
  userId: null,
  items: [],
  subtotal: 0,
  discounts: [],
  totalDiscount: 0,
  totalAmount: 0,
  currency: 'TRY',
  totalQuantity: 0,
  couponCode: null,
};

/**
 * Cart state with two modes:
 *
 *   isAuthed = false  →  guest cart from localStorage (no discounts, no coupon)
 *   isAuthed = true   →  server cart via /api/cart  (full Quote with discounts)
 *
 * Login transition (false → true): every guest line is POSTed to the server
 * cart, then localStorage is cleared. The merge happens before the first
 * server fetch so the response already contains the merged contents.
 *
 * Logout transition (true → false): server state is dropped, the cart falls
 * back to whatever localStorage holds (usually empty unless the user added
 * something while logged-out previously).
 */
export function CartProvider({ children }) {
  const { isAuthed } = useAuth();
  const [cart, setCart] = useState(() => (isAuthed ? EMPTY_CART : loadGuestCart()));
  const [loading, setLoading] = useState(false);
  const wasAuthedRef = useRef(isAuthed);

  const refresh = useCallback(async () => {
    if (!isAuthed) {
      setCart(loadGuestCart());
      return;
    }
    setLoading(true);
    try {
      const { data } = await api.get('/api/cart');
      setCart(data);
    } catch (err) {
      console.error('cart fetch failed', err);
    } finally {
      setLoading(false);
    }
  }, [isAuthed]);

  useEffect(() => {
    const wasAuthed = wasAuthedRef.current;
    wasAuthedRef.current = isAuthed;

    if (!wasAuthed && isAuthed) {
      // login transition — merge guest items into server cart
      (async () => {
        setLoading(true);
        try {
          const merged = await mergeIntoServerCart(api);
          if (merged) {
            clearGuestCart();
            setCart(merged);
            toast.success('Sepetin hesabına aktarıldı');
            return;
          }
          await refresh();
        } catch (err) {
          console.error('guest→server merge failed', err);
          await refresh();
        } finally {
          setLoading(false);
        }
      })();
      return;
    }

    if (wasAuthed && !isAuthed) {
      // logout transition — fall back to (probably empty) guest cart
      setCart(loadGuestCart());
      return;
    }

    refresh();
  }, [isAuthed, refresh]);

  const addItem = useCallback(
    async (productOrId, quantity = 1) => {
      try {
        if (!isAuthed) {
          if (typeof productOrId !== 'object' || productOrId === null) {
            throw new Error('Guest add-to-cart requires the full product object');
          }
          const next = addItemToGuestCart(productOrId, quantity);
          setCart(next);
          toast.success('Sepete eklendi');
          return;
        }
        const productId = typeof productOrId === 'object' ? productOrId.id : productOrId;
        const { data } = await api.post('/api/cart/items', { productId, quantity });
        setCart(data);
        toast.success('Sepete eklendi');
      } catch (err) {
        toast.error(err.response?.data?.message || 'Sepete eklenemedi');
        throw err;
      }
    },
    [isAuthed],
  );

  const updateQuantity = useCallback(
    async (itemId, quantity) => {
      if (!isAuthed) {
        setCart(updateGuestQuantity(itemId, quantity));
        return;
      }
      const { data } = await api.put(`/api/cart/items/${itemId}`, { quantity });
      setCart(data);
    },
    [isAuthed],
  );

  const removeItem = useCallback(
    async (itemId) => {
      if (!isAuthed) {
        setCart(removeFromGuestCart(itemId));
        toast('Ürün sepetten kaldırıldı');
        return;
      }
      const { data } = await api.delete(`/api/cart/items/${itemId}`);
      setCart(data);
      toast('Ürün sepetten kaldırıldı');
    },
    [isAuthed],
  );

  const applyCoupon = useCallback(
    async (code) => {
      if (!isAuthed) {
        toast.error('Kupon uygulamak için giriş yapmalısın');
        throw new Error('Coupon requires login');
      }
      try {
        const { data } = await api.post('/api/cart/coupon', { code });
        setCart(data);
        toast.success(`Kupon uygulandı: ${data.couponCode}`);
      } catch (err) {
        const message = err.response?.data?.message
          || (err.response?.status === 404 ? 'Kupon bulunamadı'
            : err.response?.status === 410 ? 'Kupon süresi doldu veya kullanım hakkı bitti'
              : 'Kupon uygulanamadı');
        toast.error(message);
        throw err;
      }
    },
    [isAuthed],
  );

  const clearCoupon = useCallback(async () => {
    if (!isAuthed) return;
    const { data } = await api.delete('/api/cart/coupon');
    setCart(data);
    toast('Kupon kaldırıldı');
  }, [isAuthed]);

  return (
    <CartContext.Provider
      value={{
        cart,
        loading,
        refresh,
        addItem,
        updateQuantity,
        removeItem,
        applyCoupon,
        clearCoupon,
        isGuest: !isAuthed,
      }}
    >
      {children}
    </CartContext.Provider>
  );
}

export function useCart() {
  const ctx = useContext(CartContext);
  if (!ctx) throw new Error('useCart must be used inside CartProvider');
  return ctx;
}
