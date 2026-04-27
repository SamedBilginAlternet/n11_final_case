import { beforeEach, describe, it, expect, vi } from 'vitest';
import {
  loadGuestCart,
  addItemToGuestCart,
  updateGuestQuantity,
  removeFromGuestCart,
  clearGuestCart,
  mergeIntoServerCart,
} from './guestCart.js';

const product = (overrides = {}) => ({
  id: 99,
  name: 'iPhone 15',
  imageUrl: 'img.png',
  price: '64999.00',
  currency: 'TRY',
  stock: 10,
  ...overrides,
});

describe('guestCart', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('starts empty', () => {
    const cart = loadGuestCart();
    expect(cart.items).toEqual([]);
    expect(cart.totalQuantity).toBe(0);
    expect(cart.totalAmount).toBe(0);
  });

  it('adds a new item with computed line total', () => {
    const cart = addItemToGuestCart(product(), 2);
    expect(cart.items).toHaveLength(1);
    expect(cart.items[0].quantity).toBe(2);
    expect(cart.items[0].lineTotal).toBe(129998);
    expect(cart.totalAmount).toBe(129998);
    expect(cart.totalQuantity).toBe(2);
  });

  it('increments existing line on second add', () => {
    addItemToGuestCart(product(), 1);
    const cart = addItemToGuestCart(product(), 3);
    expect(cart.items).toHaveLength(1);
    expect(cart.items[0].quantity).toBe(4);
  });

  it('clamps quantity to stock', () => {
    const cart = addItemToGuestCart(product({ stock: 2 }), 5);
    expect(cart.items[0].quantity).toBe(2);
  });

  it('updates quantity', () => {
    const initial = addItemToGuestCart(product(), 1);
    const cart = updateGuestQuantity(initial.items[0].id, 3);
    expect(cart.items[0].quantity).toBe(3);
    expect(cart.totalAmount).toBe(64999 * 3);
  });

  it('removes line when quantity goes to zero', () => {
    const initial = addItemToGuestCart(product(), 1);
    const cart = updateGuestQuantity(initial.items[0].id, 0);
    expect(cart.items).toEqual([]);
  });

  it('removes a line', () => {
    const initial = addItemToGuestCart(product(), 1);
    const cart = removeFromGuestCart(initial.items[0].id);
    expect(cart.items).toEqual([]);
    expect(localStorage.getItem('n11.guestCart')).toBeNull();
  });

  it('clears storage explicitly', () => {
    addItemToGuestCart(product(), 1);
    clearGuestCart();
    expect(localStorage.getItem('n11.guestCart')).toBeNull();
  });

  it('mergeIntoServerCart replays each item via api.post and returns last response', async () => {
    addItemToGuestCart(product({ id: 1 }), 2);
    addItemToGuestCart(product({ id: 2, name: 'AirPods' }), 1);

    const lastResponse = { data: { id: 7, items: [{ id: 1 }, { id: 2 }] } };
    const api = {
      post: vi.fn()
        .mockResolvedValueOnce({ data: { id: 7, items: [{ id: 1 }] } })
        .mockResolvedValueOnce(lastResponse),
    };

    const result = await mergeIntoServerCart(api);

    expect(api.post).toHaveBeenCalledTimes(2);
    expect(api.post).toHaveBeenNthCalledWith(1, '/api/cart/items', { productId: 1, quantity: 2 });
    expect(api.post).toHaveBeenNthCalledWith(2, '/api/cart/items', { productId: 2, quantity: 1 });
    expect(result).toEqual(lastResponse.data);
  });

  it('mergeIntoServerCart is a no-op for empty cart', async () => {
    const api = { post: vi.fn() };
    const result = await mergeIntoServerCart(api);
    expect(api.post).not.toHaveBeenCalled();
    expect(result).toBeNull();
  });
});
