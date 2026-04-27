/**
 * Guest wishlist persisted to localStorage so anonymous users can favourite
 * products without an account. Stores ONLY product ids (with timestamps) —
 * the rendering layer hydrates them via the public /api/products/{id}
 * endpoint.
 *
 * On login {@link mergeIntoServerWishlist} pushes every locally-favourited
 * id to the authenticated wishlist, then clears local state.
 */

const STORAGE_KEY = 'n11.guestWishlist';

export function loadGuestWishlist() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function persist(list) {
  if (list.length === 0) {
    localStorage.removeItem(STORAGE_KEY);
  } else {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(list));
  }
}

export function isInGuestWishlist(productId) {
  return loadGuestWishlist().some((e) => e.productId === productId);
}

export function toggleGuestWishlist(productId) {
  const current = loadGuestWishlist();
  const has = current.some((e) => e.productId === productId);
  const next = has
    ? current.filter((e) => e.productId !== productId)
    : [...current, { productId, addedAt: new Date().toISOString() }];
  persist(next);
  return !has; // true if it was just added
}

export function clearGuestWishlist() {
  localStorage.removeItem(STORAGE_KEY);
}

export async function mergeIntoServerWishlist(api) {
  const local = loadGuestWishlist();
  if (local.length === 0) return;
  // Best-effort: fire and forget per item, ignore individual failures so a
  // single missing/deleted product doesn't block the merge.
  await Promise.allSettled(
    local.map((entry) => api.post(`/api/wishlist/${entry.productId}`)),
  );
  clearGuestWishlist();
}
