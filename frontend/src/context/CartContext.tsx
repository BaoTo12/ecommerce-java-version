"use client";

import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { useAuth } from './AuthContext';

export interface CartItem {
  itemId: string;
  productId: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface CartType {
  cartId: string;
  userId: string;
  status: string;
  items: CartItem[];
  subtotal: number;
}

interface CartContextType {
  cart: CartType | null;
  loading: boolean;
  addToCart: (productId: string, quantity: number) => Promise<boolean>;
  updateCartItem: (itemId: string, quantity: number) => Promise<boolean>;
  removeFromCart: (itemId: string) => Promise<boolean>;
  fetchCart: () => Promise<void>;
  clearLocalCart: () => void;
}

const SEEDED_PRODUCTS = [
  { id: 'a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d', sku: 'PHONE-IP15', name: 'iPhone 15 Pro Max', price: 34990000.00 },
  { id: 'b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e', sku: 'PHONE-SS24', name: 'Samsung Galaxy S24 Ultra', price: 31990000.00 },
  { id: 'c3d4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e7f', sku: 'LAPTOP-MBP', name: 'MacBook Pro 14"', price: 49990000.00 },
  { id: 'd4e5f6a7-b8c9-4d0e-1f2a-3b4c5d6e7f8a', sku: 'HEADPHONE-APM', name: 'AirPods Max', price: 13490000.00 },
  { id: 'e5f6a7b8-c9d0-4e1f-2a3b-4c5d6e7f8a9b', sku: 'TABLET-IPD', name: 'iPad Pro 12.9"', price: 28990000.00 }
];

const CartContext = createContext<CartContextType | undefined>(undefined);

export const CartProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [cart, setCart] = useState<CartType | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const { user, token, isDemoMode } = useAuth();

  const fetchCart = useCallback(async () => {
    if (!user) {
      setCart(null);
      return;
    }

    if (isDemoMode || (token && token.startsWith('mock-'))) {
      const savedCart = localStorage.getItem('demo_cart_state');
      if (savedCart) {
        setCart(JSON.parse(savedCart));
      } else {
        const initialCart: CartType = {
          cartId: 'demo-cart-uuid-99999',
          userId: user.id,
          status: 'ACTIVE',
          items: [],
          subtotal: 0
        };
        localStorage.setItem('demo_cart_state', JSON.stringify(initialCart));
        setCart(initialCart);
      }
      return;
    }
    
    try {
      setLoading(true);
      const res = await fetch(`/api/cart?userId=${user.id}`, {
        headers: {
          'Authorization': `Bearer ${token}`
        }
      });
      if (res.ok) {
        const data: CartType = await res.json();
        setCart(data);
      } else {
        setCart(null);
      }
    } catch (err) {
      console.warn("Failed to fetch cart, reading local storage demo fallback", err);
      // Fallback
      const savedCart = localStorage.getItem('demo_cart_state');
      if (savedCart) {
        setCart(JSON.parse(savedCart));
      }
    } finally {
      setLoading(false);
    }
  }, [user, token, isDemoMode]);

  useEffect(() => {
    fetchCart();
  }, [fetchCart]);

  const addToCart = async (productId: string, quantity: number): Promise<boolean> => {
    if (!user) return false;

    if (isDemoMode || (token && token.startsWith('mock-'))) {
      // Local storage mock simulation
      const currentCartStr = localStorage.getItem('demo_cart_state');
      let currentCart: CartType;
      if (currentCartStr) {
        currentCart = JSON.parse(currentCartStr);
      } else {
        currentCart = {
          cartId: 'demo-cart-uuid-99999',
          userId: user.id,
          status: 'ACTIVE',
          items: [],
          subtotal: 0
        };
      }

      const prod = SEEDED_PRODUCTS.find(p => p.id === productId);
      if (!prod) return false;

      const existingIndex = currentCart.items.findIndex(item => item.productId === productId);
      if (existingIndex > -1) {
        currentCart.items[existingIndex].quantity += quantity;
        currentCart.items[existingIndex].lineTotal = currentCart.items[existingIndex].quantity * currentCart.items[existingIndex].unitPrice;
      } else {
        const newItem: CartItem = {
          itemId: `demo-item-uuid-${Math.random().toString(36).substr(2, 9)}`,
          productId,
          productName: prod.name,
          quantity,
          unitPrice: prod.price,
          lineTotal: quantity * prod.price
        };
        currentCart.items.push(newItem);
      }

      currentCart.subtotal = currentCart.items.reduce((sum, item) => sum + item.lineTotal, 0);
      localStorage.setItem('demo_cart_state', JSON.stringify(currentCart));
      setCart(currentCart);
      return true;
    }

    try {
      setLoading(true);
      const res = await fetch('/api/cart/items', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({
          userId: user.id,
          productId,
          quantity
        })
      });

      if (res.ok) {
        const data: CartType = await res.json();
        setCart(data);
        return true;
      }
      return false;
    } catch (err) {
      console.warn("API add to cart failed, using local simulation fallback", err);
      // Fallback
      return false;
    } finally {
      setLoading(false);
    }
  };

  const updateCartItem = async (itemId: string, quantity: number): Promise<boolean> => {
    if (!user) return false;

    if (isDemoMode || (token && token.startsWith('mock-'))) {
      const currentCartStr = localStorage.getItem('demo_cart_state');
      if (!currentCartStr) return false;
      const currentCart: CartType = JSON.parse(currentCartStr);

      const existingIndex = currentCart.items.findIndex(item => item.itemId === itemId);
      if (existingIndex > -1) {
        currentCart.items[existingIndex].quantity = quantity;
        currentCart.items[existingIndex].lineTotal = quantity * currentCart.items[existingIndex].unitPrice;
        currentCart.subtotal = currentCart.items.reduce((sum, item) => sum + item.lineTotal, 0);
        localStorage.setItem('demo_cart_state', JSON.stringify(currentCart));
        setCart(currentCart);
        return true;
      }
      return false;
    }

    try {
      setLoading(true);
      const res = await fetch(`/api/cart/items/${itemId}`, {
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({ quantity })
      });

      if (res.ok) {
        const data: CartType = await res.json();
        setCart(data);
        return true;
      }
      return false;
    } catch (err) {
      console.warn("API patch failed, updating local state", err);
      return false;
    } finally {
      setLoading(false);
    }
  };

  const removeFromCart = async (itemId: string): Promise<boolean> => {
    if (!user) return false;

    if (isDemoMode || (token && token.startsWith('mock-'))) {
      const currentCartStr = localStorage.getItem('demo_cart_state');
      if (!currentCartStr) return false;
      const currentCart: CartType = JSON.parse(currentCartStr);

      currentCart.items = currentCart.items.filter(item => item.itemId !== itemId);
      currentCart.subtotal = currentCart.items.reduce((sum, item) => sum + item.lineTotal, 0);
      localStorage.setItem('demo_cart_state', JSON.stringify(currentCart));
      setCart(currentCart);
      return true;
    }

    try {
      setLoading(true);
      const res = await fetch(`/api/cart/items/${itemId}`, {
        method: 'DELETE',
        headers: {
          'Authorization': `Bearer ${token}`
        }
      });

      if (res.ok) {
        await fetchCart();
        return true;
      }
      return false;
    } catch (err) {
      console.warn("API delete item failed, removing locally", err);
      return false;
    } finally {
      setLoading(false);
    }
  };

  const clearLocalCart = () => {
    setCart(null);
    localStorage.removeItem('demo_cart_state');
  };

  return (
    <CartContext.Provider value={{ cart, loading, addToCart, updateCartItem, removeFromCart, fetchCart, clearLocalCart }}>
      {children}
    </CartContext.Provider>
  );
};

export const useCart = () => {
  const context = useContext(CartContext);
  if (!context) {
    throw new Error('useCart must be used within a CartProvider');
  }
  return context;
};
