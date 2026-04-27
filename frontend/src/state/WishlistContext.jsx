import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import toast from 'react-hot-toast';
import { api } from '../api/client.js';
import { useAuth } from './AuthContext.jsx';
import {
  isInGuestWishlist,
  loadGuestWishlist,
  mergeIntoServerWishlist,
  toggleGuestWishlist,
} from '../utils/guestWishlist.js';

const WishlistContext = createContext(null);

/**
 * Two-mode wishlist mirroring the cart: localStorage when not authenticated,
 * server-backed when authenticated. The transition from guest → authed
 * fires merge-into-server before reloading, so favourites survive login.
 *
 * The context exposes a Set of product ids for O(1) "is favourited" checks
 * from product cards, plus a fully-hydrated list for the wishlist page.
 */
export function WishlistProvider({ children }) {
  const { isAuthed } = useAuth();
  const [items, setItems] = useState([]);
  const [favIds, setFavIds] = useState(() => new Set(loadGuestWishlist().map((e) => e.productId)));
  const wasAuthedRef = useRef(isAuthed);

  const loadFromServer = useCallback(async () => {
    try {
      const { data } = await api.get('/api/wishlist');
      setItems(data);
      setFavIds(new Set(data.map((i) => i.productId)));
    } catch {
      // toast intentionally silent — wishlist is non-critical to page render
    }
  }, []);

  // Initial sync + login transition
  useEffect(() => {
    let cancelled = false;
    async function sync() {
      const wasAuthed = wasAuthedRef.current;
      wasAuthedRef.current = isAuthed;
      if (!isAuthed) {
        const guest = loadGuestWishlist();
        if (!cancelled) {
          setItems([]); // guest list is hydrated lazily by ProductCard buttons
          setFavIds(new Set(guest.map((e) => e.productId)));
        }
        return;
      }
      // First time becoming authed in this session: merge guest favourites in.
      if (!wasAuthed && loadGuestWishlist().length > 0) {
        await mergeIntoServerWishlist(api);
      }
      if (!cancelled) await loadFromServer();
    }
    sync();
    return () => { cancelled = true; };
  }, [isAuthed, loadFromServer]);

  const toggle = useCallback(async (productId) => {
    if (!isAuthed) {
      const added = toggleGuestWishlist(productId);
      setFavIds((prev) => {
        const next = new Set(prev);
        if (added) next.add(productId); else next.delete(productId);
        return next;
      });
      toast(added ? 'Favorilere eklendi' : 'Favorilerden çıkarıldı', { icon: added ? '❤️' : '💔' });
      return added;
    }
    try {
      const { data } = await api.post(`/api/wishlist/${productId}/toggle`);
      const added = !!data?.added;
      setFavIds((prev) => {
        const next = new Set(prev);
        if (added) next.add(productId); else next.delete(productId);
        return next;
      });
      // Refresh hydrated list quietly so the /favorites page stays current.
      loadFromServer();
      toast(added ? 'Favorilere eklendi' : 'Favorilerden çıkarıldı', { icon: added ? '❤️' : '💔' });
      return added;
    } catch (err) {
      toast.error(err.response?.data?.message || 'İşlem başarısız');
      return null;
    }
  }, [isAuthed, loadFromServer]);

  const isFavourite = useCallback((productId) => favIds.has(productId), [favIds]);

  return (
    <WishlistContext.Provider value={{ items, favIds, isFavourite, toggle, refresh: loadFromServer, isGuest: !isAuthed }}>
      {children}
    </WishlistContext.Provider>
  );
}

export function useWishlist() {
  const ctx = useContext(WishlistContext);
  if (!ctx) throw new Error('useWishlist must be used inside WishlistProvider');
  return ctx;
}

// Re-export so legacy callers can read sync without going through the hook.
export { isInGuestWishlist };
