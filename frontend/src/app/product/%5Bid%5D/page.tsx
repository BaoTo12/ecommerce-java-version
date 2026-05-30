"use client";

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { getProductImage } from '@/app/utils/productImages';
import { useCart } from '@/context/CartContext';
import { useAuth } from '@/context/AuthContext';

interface Product {
  id: string;
  sku: string;
  name: string;
  description: string;
  price: number;
}

interface Inventory {
  productId: string;
  quantity: number;
}

export default function ProductDetailPage() {
  const params = useParams();
  const router = useRouter();
  const productId = params.id as string;

  const [product, setProduct] = useState<Product | null>(null);
  const [stock, setStock] = useState<number | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [quantity, setQuantity] = useState<number>(1);
  const { addToCart } = useCart();
  const { user } = useAuth();
  const [adding, setAdding] = useState<boolean>(false);
  const [added, setAdded] = useState<boolean>(false);

  useEffect(() => {
    if (!productId) return;

    async function loadDetails() {
      try {
        setLoading(true);
        const prodRes = await fetch(`/api/catalog/${productId}`);
        if (!prodRes.ok) {
          throw new Error("Product not found");
        }
        const prodData = await prodRes.json();
        setProduct(prodData);

        try {
          const invRes = await fetch(`/api/inventory/${productId}`);
          if (invRes.ok) {
            const invData: Inventory = await invRes.json();
            setStock(invData.quantity);
          }
        } catch (invErr) {
          console.error("Failed to fetch inventory stock levels", invErr);
          setStock(null);
        }

      } catch (err) {
        console.warn("Product Details API offline, switching to demo mock database", err);
        const mockDb = [
          { id: 'a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d', sku: 'PHONE-IP15', name: 'iPhone 15 Pro Max', price: 34990000.00, description: 'Apple iPhone 15 Pro Max 256GB' },
          { id: 'b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e', sku: 'PHONE-SS24', name: 'Samsung Galaxy S24 Ultra', price: 31990000.00, description: 'Samsung Galaxy S24 Ultra 512GB' },
          { id: 'c3d4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e7f', sku: 'LAPTOP-MBP', name: 'MacBook Pro 14"', price: 49990000.00, description: 'Apple MacBook Pro 14-inch M3 Pro' },
          { id: 'd4e5f6a7-b8c9-4d0e-1f2a-3b4c5d6e7f8a', sku: 'HEADPHONE-APM', name: 'AirPods Max', price: 13490000.00, description: 'Apple AirPods Max - Space Gray' },
          { id: 'e5f6a7b8-c9d0-4e1f-2a3b-4c5d6e7f8a9b', sku: 'TABLET-IPD', name: 'iPad Pro 12.9"', price: 28990000.00, description: 'Apple iPad Pro 12.9-inch M2 256GB' }
        ];
        const localProd = mockDb.find(p => p.id === productId);
        if (localProd) {
          setProduct(localProd);
          setStock(40); // default stock in fallback mode
        } else {
          router.push('/');
        }
      } finally {
        setLoading(false);
      }
    }

    loadDetails();
  }, [productId, router]);

  const handleAddToCart = async () => {
    if (!user) {
      alert("Please sign in to add items to your cart.");
      router.push('/login');
      return;
    }
    if (!product) return;

    try {
      setAdding(true);
      const success = await addToCart(product.id, quantity);
      if (success) {
        setAdded(true);
        setTimeout(() => setAdded(false), 2000);
      }
    } finally {
      setAdding(false);
    }
  };

  const formatPrice = (price: number) => {
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(price);
  };

  if (loading) {
    return (
      <div className="flex h-[60vh] items-center justify-center">
        <div className="flex flex-col items-center gap-4">
          <div className="w-10 h-10 border-4 border-indigo-500 border-t-transparent rounded-full animate-spin" />
          <span className="text-gray-400 font-mono text-sm">Decoding product metrics...</span>
        </div>
      </div>
    );
  }

  if (!product) return null;

  return (
    <div className="space-y-8 py-4">
      {/* Back Button */}
      <Link
        href="/"
        className="inline-flex items-center gap-2 text-sm text-gray-400 hover:text-white transition-colors"
      >
        <svg
          xmlns="http://www.w3.org/2000/svg"
          fill="none"
          viewBox="0 0 24 24"
          strokeWidth={2}
          stroke="currentColor"
          className="w-4 h-4"
        >
          <path strokeLinecap="round" strokeLinejoin="round" d="M10.5 19.5 3 12m0 0 7.5-7.5M3 12h18" />
        </svg>
        Back to Catalog
      </Link>

      {/* Main Details Panel */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-12 rounded-3xl glass-panel border border-white/10 p-6 md:p-10 shadow-2xl relative">
        {/* Left Column: Photograph */}
        <div className="rounded-2xl overflow-hidden bg-gray-950 aspect-video md:aspect-square relative max-h-[500px]">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={getProductImage(product.sku)}
            alt={product.name}
            className="w-full h-full object-cover"
          />
          <div className="absolute inset-0 bg-gradient-to-t from-gray-950/70 to-transparent" />
        </div>

        {/* Right Column: Specs and Control */}
        <div className="flex flex-col justify-between py-2 space-y-6">
          <div className="space-y-4">
            <span className="px-2.5 py-0.5 rounded-md border border-indigo-500/30 bg-indigo-500/10 text-xs text-indigo-400 uppercase tracking-widest font-mono font-semibold">
              SKU: {product.sku}
            </span>
            <h1 className="text-3xl md:text-4xl font-extrabold text-white leading-tight">
              {product.name}
            </h1>
            
            {/* Real-time Stock Badge */}
            <div className="flex items-center gap-2">
              {stock !== null ? (
                stock > 10 ? (
                  <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full border border-green-500/20 bg-green-500/10 text-xs font-semibold text-green-400 font-mono">
                    <span className="w-1.5 h-1.5 rounded-full bg-green-400 animate-pulse" />
                    {stock} Available (In Stock)
                  </span>
                ) : stock > 0 ? (
                  <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full border border-yellow-500/20 bg-yellow-500/10 text-xs font-semibold text-yellow-400 font-mono">
                    <span className="w-1.5 h-1.5 rounded-full bg-yellow-400 animate-pulse" />
                    Hurry! Only {stock} remaining!
                  </span>
                ) : (
                  <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full border border-red-500/20 bg-red-500/10 text-xs font-semibold text-red-400 font-mono">
                    <span className="w-1.5 h-1.5 rounded-full bg-red-400" />
                    Out of Stock
                  </span>
                )
              ) : (
                <span className="px-3 py-1 rounded-full border border-white/5 bg-white/5 text-xs text-gray-500 font-mono">
                  Inventory Unresolved
                </span>
              )}
            </div>

            <p className="text-gray-400 text-sm leading-relaxed">
              {product.description}
            </p>
          </div>

          <div className="space-y-6 pt-6 border-t border-white/5">
            {/* Pricing Section */}
            <div className="flex flex-col">
              <span className="text-xs text-gray-500 uppercase tracking-widest font-mono">Unit MSRP</span>
              <span className="text-3xl font-black bg-gradient-to-r from-white via-gray-100 to-gray-400 bg-clip-text text-transparent">
                {formatPrice(product.price)}
              </span>
            </div>

            {/* Quantity Selector & Cart Control */}
            {stock !== 0 && (
              <div className="flex flex-wrap items-center gap-4">
                <div className="flex items-center rounded-xl border border-white/10 bg-white/5 p-1">
                  <button
                    onClick={() => setQuantity(Math.max(1, quantity - 1))}
                    className="p-2.5 hover:text-white text-gray-400 transition-colors cursor-pointer"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2.5} stroke="currentColor" className="w-4 h-4">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M5 12h14" />
                    </svg>
                  </button>
                  <span className="px-4 text-sm font-bold font-mono text-white">
                    {quantity}
                  </span>
                  <button
                    onClick={() => setQuantity(stock !== null ? Math.min(stock, quantity + 1) : quantity + 1)}
                    className="p-2.5 hover:text-white text-gray-400 transition-colors cursor-pointer"
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2.5} stroke="currentColor" className="w-4 h-4">
                      <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
                    </svg>
                  </button>
                </div>

                <button
                  onClick={handleAddToCart}
                  disabled={adding}
                  className={`flex-grow md:flex-grow-0 md:px-8 py-3.5 rounded-xl text-sm font-bold text-white shadow-lg cursor-pointer transition-all flex items-center justify-center gap-2 ${
                    added
                      ? 'bg-green-600 hover:bg-green-500 shadow-green-600/20'
                      : 'bg-indigo-600 hover:bg-indigo-500 shadow-indigo-600/20 hover:shadow-indigo-600/30 hover:scale-[1.02] active:scale-[0.98]'
                  }`}
                >
                  {adding ? (
                    <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                  ) : added ? (
                    <>
                      <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2.5} stroke="currentColor" className="w-5 h-5">
                        <path strokeLinecap="round" strokeLinejoin="round" d="m4.5 12.75 6 6 9-13.5" />
                      </svg>
                      Added to Cart!
                    </>
                  ) : (
                    <>
                      <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor" className="w-5 h-5">
                        <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 3h1.386c.51 0 .955.343 1.087.835l.383 1.437M7.5 14.25a3 3 0 0 0-3 3h15.75m-12.75-3h11.218c1.121-2.3 2.1-4.684 2.924-7.138a60.114 60.114 0 0 0-16.536-1.84M7.5 14.25L5.106 5.272M6 20.25a.75.75 0 1 1-1.5 0 .75.75 0 0 1 1.5 0Zm12.75 0a.75.75 0 1 1-1.5 0 .75.75 0 0 1 1.5 0Z" />
                      </svg>
                      Deploy to Cart
                    </>
                  )}
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
