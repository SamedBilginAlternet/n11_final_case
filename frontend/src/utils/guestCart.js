/**
 * Guest cart persisted to localStorage so anonymous shoppers can browse +
 * add to cart without authenticating. The shape mirrors the server CartDto
 * closely enough that consumers (CartPage, header, mappers) don't need a
 * conditional on isAuthed for read paths.
 *
 * Server-only fields (`id`, `userId`, real `cart_item.id`, applied discounts,
 * coupon validation) stay null/empty until the user signs in — at which point
 * `mergeIntoServerCart()` POSTs every guest item to the server cart and the
 * localStorage entry is wiped.
 */

const STORAGE_KEY = 'n11.guestCart';

const EMPTY = Object.freeze({
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
});

export function loadGuestCart() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return cloneEmpty();
    const parsed = JSON.parse(raw);
    return recompute(Array.isArray(parsed.items) ? parsed.items : []);
  } catch {
    return cloneEmpty();
  }
}

export function clearGuestCart() {
  localStorage.removeItem(STORAGE_KEY);
}

/**
 * @param {object} product  ProductDto / ProductSnapshot — needs id, name, price,
 *                          currency, optional imageUrl, optional stock
 * @param {number} quantity additive — existing line is incremented
 */
export function addItemToGuestCart(product, quantity = 1) {
  if (!product || quantity <= 0) return loadGuestCart();
  const items = loadItems();
  const existing = items.find((i) => i.productId === product.id);

  if (existing) {
    existing.quantity = clampStock(existing.quantity + quantity, product.stock);
  } else {
    items.push({
      id: -Date.now(),                          // negative pseudo-id, never collides with DB
      productId: product.id,
      productName: product.name,
      imageUrl: product.imageUrl ?? null,
      quantity: clampStock(quantity, product.stock),
      unitPrice: Number(product.price),
      currency: product.currency ?? 'TRY',
    });
  }
  return persist(items);
}

export function updateGuestQuantity(itemId, quantity) {
  const items = loadItems();
  const target = items.find((i) => i.id === itemId);
  if (!target) return loadGuestCart();
  if (quantity <= 0) {
    return removeFromGuestCart(itemId);
  }
  target.quantity = quantity;
  return persist(items);
}

export function removeFromGuestCart(itemId) {
  const items = loadItems().filter((i) => i.id !== itemId);
  return persist(items);
}

/**
 * Replays every guest line into the server cart via the supplied axios
 * instance. Stops on the first error so partial merges are still caught
 * (the user keeps localStorage state and can retry by reloading).
 *
 * Returns the server cart from the last successful POST. Caller should
 * `clearGuestCart()` only after a non-throwing completion.
 */
export async function mergeIntoServerCart(api) {
  const items = loadItems();
  if (items.length === 0) return null;
  let serverCart = null;
  for (const item of items) {
    const { data } = await api.post('/api/cart/items', {
      productId: item.productId,
      quantity: item.quantity,
    });
    serverCart = data;
  }
  return serverCart;
}

// --- internal -------------------------------------------------------------

function loadItems() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed.items) ? parsed.items : [];
  } catch {
    return [];
  }
}

function persist(items) {
  const cart = recompute(items);
  if (items.length === 0) {
    localStorage.removeItem(STORAGE_KEY);
  } else {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ items }));
  }
  return cart;
}

function recompute(items) {
  const enriched = items.map((i) => ({
    ...i,
    lineTotal: round2(Number(i.unitPrice) * i.quantity),
  }));
  const subtotal = enriched.reduce((acc, i) => acc + i.lineTotal, 0);
  const totalQuantity = enriched.reduce((acc, i) => acc + i.quantity, 0);
  const currency = enriched[0]?.currency ?? 'TRY';

  return {
    ...cloneEmpty(),
    items: enriched,
    subtotal: round2(subtotal),
    totalAmount: round2(subtotal),
    totalQuantity,
    currency,
  };
}

function cloneEmpty() {
  return { ...EMPTY, items: [], discounts: [] };
}

function clampStock(qty, stock) {
  if (stock == null) return qty;
  return Math.min(qty, stock);
}

function round2(n) {
  return Math.round(n * 100) / 100;
}
