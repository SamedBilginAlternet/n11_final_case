import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { api } from '../api/client.js';
import { useAuth } from './AuthContext.jsx';

const EMPTY_CART = {
  items: [],
  subtotal: 0,
  discounts: [],
  totalDiscount: 0,
  totalAmount: 0,
  totalQuantity: 0,
  currency: 'TRY',
  couponCode: null,
};

const CartContext = createContext(null);

export function CartProvider({ children }) {
  const { isAuthed } = useAuth();
  const [cart, setCart] = useState(EMPTY_CART);
  const [loading, setLoading] = useState(false);

  const refresh = useCallback(async () => {
    if (!isAuthed) {
      setCart(EMPTY_CART);
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
    refresh();
  }, [refresh]);

  const addItem = useCallback(async (productId, quantity = 1) => {
    try {
      const { data } = await api.post('/api/cart/items', { productId, quantity });
      setCart(data);
      toast.success('Sepete eklendi');
    } catch (err) {
      toast.error(err.response?.data?.message || 'Sepete eklenemedi');
      throw err;
    }
  }, []);

  const updateQuantity = useCallback(async (itemId, quantity) => {
    const { data } = await api.put(`/api/cart/items/${itemId}`, { quantity });
    setCart(data);
  }, []);

  const removeItem = useCallback(async (itemId) => {
    const { data } = await api.delete(`/api/cart/items/${itemId}`);
    setCart(data);
    toast('Ürün sepetten kaldırıldı');
  }, []);

  const applyCoupon = useCallback(async (code) => {
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
  }, []);

  const clearCoupon = useCallback(async () => {
    const { data } = await api.delete('/api/cart/coupon');
    setCart(data);
    toast('Kupon kaldırıldı');
  }, []);

  return (
    <CartContext.Provider
      value={{ cart, loading, refresh, addItem, updateQuantity, removeItem, applyCoupon, clearCoupon }}
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
