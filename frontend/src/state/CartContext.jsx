import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { api } from '../api/client.js';
import { useAuth } from './AuthContext.jsx';

const CartContext = createContext(null);

export function CartProvider({ children }) {
  const { isAuthed } = useAuth();
  const [cart, setCart] = useState({ items: [], totalAmount: 0, totalQuantity: 0, currency: 'TRY' });
  const [loading, setLoading] = useState(false);

  const refresh = useCallback(async () => {
    if (!isAuthed) {
      setCart({ items: [], totalAmount: 0, totalQuantity: 0, currency: 'TRY' });
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

  return (
    <CartContext.Provider value={{ cart, loading, refresh, addItem, updateQuantity, removeItem }}>
      {children}
    </CartContext.Provider>
  );
}

export function useCart() {
  const ctx = useContext(CartContext);
  if (!ctx) throw new Error('useCart must be used inside CartProvider');
  return ctx;
}
