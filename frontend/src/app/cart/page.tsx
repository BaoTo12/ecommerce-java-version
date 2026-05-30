"use client";

import React from 'react';
import Link from 'next/link';
import { useCart } from '@/context/CartContext';
import { getProductImage } from '@/app/utils/productImages';

export default function CartPage() {
  const { cart, loading, updateCartItem, removeFromCart } = useCart();

  const formatPrice = (price: number) => {
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(price);
  };

  const handleQuantityChange = async (itemId: string, currentQty: number, change: number) => {
    const newQty = currentQty + change;
    if (newQty < 1) return;
    await updateCartItem(itemId, newQty);
  };

  if (loading && !cart) {
    return (
      <div className="flex h-[60vh] items-center justify-center">
        <div className="flex flex-col items-center gap-4">
          <div className="w-10 h-10 border-4 border-indigo-500 border-t-transparent rounded-full animate-spin" />
          <span className="text-gray-400 font-mono text-sm">Reviewing cart items...</span>
        </div>
      </div>
    );
  }

  const items = cart?.items || [];

  return (
    <div className="space-y-8">
      <div className="border-b border-white/5 pb-4 flex items-center justify-between">
        <h1 className="text-2xl font-black text-white tracking-wide uppercase font-mono">
          Your Shopping Cart
        </h1>
        <span className="text-sm text-gray-400 font-mono">
          {items.length} unique {items.length === 1 ? 'model' : 'models'}
        </span>
      </div>

      {items.length === 0 ? (
        <div className="text-center py-20 rounded-2xl glass-panel border border-white/5 space-y-6">
          <div className="flex justify-center">
            <div className="p-4 rounded-full border border-indigo-500/20 bg-indigo-500/5 text-indigo-400">
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-10 h-10">
                <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 3h1.386c.51 0 .955.343 1.087.835l.383 1.437M7.5 14.25a3 3 0 0 0-3 3h15.75m-12.75-3h11.218c1.121-2.3 2.1-4.684 2.924-7.138a60.114 60.114 0 0 0-16.536-1.84M7.5 14.25L5.106 5.272M6 20.25a.75.75 0 1 1-1.5 0 .75.75 0 0 1 1.5 0Zm12.75 0a.75.75 0 1 1-1.5 0 .75.75 0 0 1 1.5 0Z" />
              </svg>
            </div>
          </div>
          <div className="space-y-2">
            <h3 className="text-lg font-bold text-white">Your Cart is Vacant</h3>
            <p className="text-sm text-gray-400 max-w-sm mx-auto">
              Explore our tech collections and deploy high-performance gadgets here.
            </p>
          </div>
          <Link
            href="/"
            className="inline-flex items-center justify-center px-6 py-3 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-xs font-bold text-white shadow-lg shadow-indigo-600/20 hover:shadow-indigo-600/30 transition-all cursor-pointer"
          >
            Browse Marketplace
          </Link>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
          {/* Items List */}
          <div className="lg:col-span-2 space-y-4">
            {items.map((item) => (
              <div
                key={item.itemId}
                className="flex items-center gap-6 rounded-2xl glass-panel border border-white/10 p-4 shadow-md hover:border-white/15 transition-colors relative"
              >
                {/* Photograph */}
                <div className="w-20 h-20 rounded-xl overflow-hidden bg-gray-950 flex-shrink-0">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img
                    src="https://images.unsplash.com/photo-1526738549149-8e07eca6c147?q=80&w=200&auto=format&fit=crop" // Abstract fall back or mapped later
                    alt={item.productName}
                    className="w-full h-full object-cover"
                  />
                </div>

                {/* Details */}
                <div className="flex-grow flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                  <div className="space-y-1">
                    <h3 className="text-base font-bold text-white hover:text-indigo-400 transition-colors">
                      {item.productName}
                    </h3>
                    <div className="text-xs text-gray-500 font-mono">
                      Unit: {formatPrice(item.unitPrice)}
                    </div>
                  </div>

                  {/* Quantity Actions */}
                  <div className="flex items-center gap-4">
                    <div className="flex items-center rounded-lg border border-white/10 bg-white/5 p-0.5">
                      <button
                        onClick={() => handleQuantityChange(item.itemId, item.quantity, -1)}
                        disabled={item.quantity <= 1}
                        className="p-1.5 hover:text-white text-gray-400 transition-colors cursor-pointer disabled:opacity-30"
                      >
                        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2.5} stroke="currentColor" className="w-3.5 h-3.5">
                          <path strokeLinecap="round" strokeLinejoin="round" d="M5 12h14" />
                        </svg>
                      </button>
                      <span className="px-3 text-xs font-bold font-mono text-white">
                        {item.quantity}
                      </span>
                      <button
                        onClick={() => handleQuantityChange(item.itemId, item.quantity, 1)}
                        className="p-1.5 hover:text-white text-gray-400 transition-colors cursor-pointer"
                      >
                        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2.5} stroke="currentColor" className="w-3.5 h-3.5">
                          <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
                        </svg>
                      </button>
                    </div>

                    <div className="text-right sm:min-w-[100px]">
                      <div className="text-sm font-bold text-white font-mono">
                        {formatPrice(item.lineTotal)}
                      </div>
                    </div>
                  </div>
                </div>

                {/* Remove Actions */}
                <button
                  onClick={() => removeFromCart(item.itemId)}
                  className="p-2 rounded-lg border border-white/5 hover:border-red-500/30 hover:bg-red-500/10 text-gray-500 hover:text-red-400 transition-all cursor-pointer self-start sm:self-center"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-4 h-4">
                    <path strokeLinecap="round" strokeLinejoin="round" d="m14.74 9-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 0 1-2.244 2.077H8.084a2.25 2.25 0 0 1-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 0 0-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 0 1 3.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 0 0-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 0 0-7.5 0" />
                  </svg>
                </button>
              </div>
            ))}
          </div>

          {/* Checkout Panel summary */}
          <div className="rounded-2xl glass-panel border border-white/10 p-6 shadow-lg h-fit space-y-6">
            <h3 className="text-lg font-bold text-white font-mono border-b border-white/5 pb-3">
              Order Specification
            </h3>

            <div className="space-y-4">
              <div className="flex justify-between text-sm text-gray-400">
                <span>Items Subtotal</span>
                <span className="font-mono font-semibold text-white">
                  {formatPrice(cart?.subtotal || 0)}
                </span>
              </div>
              <div className="flex justify-between text-sm text-gray-400">
                <span>Shipping</span>
                <span className="text-green-400 font-mono">Complimentary</span>
              </div>
              <div className="flex justify-between text-sm text-gray-400">
                <span>Tax Estimations</span>
                <span className="font-mono text-white">Included</span>
              </div>
              <div className="flex justify-between border-t border-white/5 pt-4 text-base font-bold text-white">
                <span>Total Amount</span>
                <span className="font-mono bg-gradient-to-r from-indigo-400 to-purple-400 bg-clip-text text-transparent text-lg">
                  {formatPrice(cart?.subtotal || 0)}
                </span>
              </div>
            </div>

            <Link
              href="/checkout"
              className="w-full py-4 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-sm font-bold text-white shadow-lg shadow-indigo-600/20 hover:shadow-indigo-600/30 hover:scale-[1.01] transition-all flex items-center justify-center gap-2 cursor-pointer"
            >
              Configure Checkout
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2.5} stroke="currentColor" className="w-4 h-4">
                <path strokeLinecap="round" strokeLinejoin="round" d="M13.5 4.5 21 12m0 0-7.5 7.5M21 12H3" />
              </svg>
            </Link>
          </div>
        </div>
      )}
    </div>
  );
}
