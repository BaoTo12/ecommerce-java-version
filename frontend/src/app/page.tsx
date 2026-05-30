"use client";

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
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

export default function CatalogPage() {
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [search, setSearch] = useState<string>('');
  const [debouncedSearch, setDebouncedSearch] = useState<string>('');
  const { addToCart } = useCart();
  const { user } = useAuth();
  const [addingId, setAddingId] = useState<string | null>(null);

  // Debounce search input
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(search);
    }, 400);
    return () => clearTimeout(timer);
  }, [search]);

  useEffect(() => {
    async function fetchProducts() {
      try {
        setLoading(true);
        const url = debouncedSearch 
          ? `/api/catalog?keyword=${encodeURIComponent(debouncedSearch)}`
          : '/api/catalog';
        const res = await fetch(url);
        if (res.ok) {
          const data = await res.json();
          setProducts(data.content || []);
        } else {
          throw new Error("HTTP failure");
        }
      } catch (err) {
        console.warn("Catalog API offline, loading seeded mock products", err);
        const mockDb = [
          { id: 'a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d', sku: 'PHONE-IP15', name: 'iPhone 15 Pro Max', price: 34990000.00, description: 'Apple iPhone 15 Pro Max 256GB' },
          { id: 'b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e', sku: 'PHONE-SS24', name: 'Samsung Galaxy S24 Ultra', price: 31990000.00, description: 'Samsung Galaxy S24 Ultra 512GB' },
          { id: 'c3d4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e7f', sku: 'LAPTOP-MBP', name: 'MacBook Pro 14"', price: 49990000.00, description: 'Apple MacBook Pro 14-inch M3 Pro' },
          { id: 'd4e5f6a7-b8c9-4d0e-1f2a-3b4c5d6e7f8a', sku: 'HEADPHONE-APM', name: 'AirPods Max', price: 13490000.00, description: 'Apple AirPods Max - Space Gray' },
          { id: 'e5f6a7b8-c9d0-4e1f-2a3b-4c5d6e7f8a9b', sku: 'TABLET-IPD', name: 'iPad Pro 12.9"', price: 28990000.00, description: 'Apple iPad Pro 12.9-inch M2 256GB' }
        ];
        if (debouncedSearch) {
          setProducts(mockDb.filter(p => p.name.toLowerCase().includes(debouncedSearch.toLowerCase())));
        } else {
          setProducts(mockDb);
        }
      } finally {
        setLoading(false);
      }
    }
    fetchProducts();
  }, [debouncedSearch]);

  const handleAddToCart = async (e: React.MouseEvent, productId: string) => {
    e.preventDefault();
    if (!user) {
      alert("Please sign in to add items to your cart.");
      return;
    }
    try {
      setAddingId(productId);
      await addToCart(productId, 1);
    } finally {
      setAddingId(null);
    }
  };

  const formatPrice = (price: number) => {
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(price);
  };

  return (
    <div className="space-y-12">
      {/* Premium Hero Banner */}
      <section className="relative rounded-3xl overflow-hidden glass-panel border border-white/10 p-8 md:p-16 flex flex-col justify-center items-start gap-6 shadow-2xl shadow-indigo-500/5">
        <div className="absolute top-0 right-0 w-[40%] h-full opacity-35 bg-gradient-to-l from-indigo-500/20 to-transparent pointer-events-none" />
        <span className="px-3 py-1 rounded-full border border-cyan-400/30 bg-cyan-400/10 text-cyan-400 text-xs font-semibold uppercase tracking-widest font-mono">
          Summer Tech Collection
        </span>
        <h1 className="text-4xl md:text-6xl font-black tracking-tight leading-tight max-w-2xl bg-gradient-to-r from-white via-gray-200 to-gray-500 bg-clip-text text-transparent">
          The Future of <br className="hidden sm:inline" />
          <span className="bg-gradient-to-r from-indigo-400 via-purple-400 to-cyan-400 bg-clip-text text-transparent font-extrabold">
            Premium Electronics
          </span>
        </h1>
        <p className="text-gray-400 text-base md:text-lg max-w-lg">
          Experience highly-crafted personal tech devices engineered for speed and aesthetic brilliance.
        </p>
        <div className="w-full max-w-md mt-4 relative">
          <input
            type="text"
            placeholder="Search premium devices..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full px-5 py-3.5 rounded-2xl border border-white/10 bg-white/5 backdrop-blur-md text-white placeholder-gray-500 outline-none focus:border-indigo-500/50 focus:ring-2 focus:ring-indigo-500/10 transition-all text-sm"
          />
          <svg
            xmlns="http://www.w3.org/2000/svg"
            fill="none"
            viewBox="0 0 24 24"
            strokeWidth={2}
            stroke="currentColor"
            className="w-5 h-5 absolute right-4 top-1/2 -translate-y-1/2 text-gray-500 pointer-events-none"
          >
            <path strokeLinecap="round" strokeLinejoin="round" d="m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.602 10.602Z" />
          </svg>
        </div>
      </section>

      {/* Grid Products Catalog */}
      <section className="space-y-6">
        <div className="flex items-center justify-between border-b border-white/5 pb-4">
          <h2 className="text-xl font-bold text-white tracking-wider uppercase font-mono">
            Featured Catalog
          </h2>
          <span className="text-sm text-gray-400 font-mono">
            Showing {products.length} premium models
          </span>
        </div>

        {loading ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-8 py-12">
            {[1, 2, 3].map((n) => (
              <div key={n} className="h-[420px] rounded-2xl bg-white/5 border border-white/5 animate-pulse flex flex-col justify-between p-6">
                <div className="h-48 w-full bg-white/5 rounded-xl" />
                <div className="space-y-4">
                  <div className="h-6 w-2/3 bg-white/5 rounded" />
                  <div className="h-4 w-5/6 bg-white/5 rounded" />
                </div>
                <div className="h-10 w-full bg-white/5 rounded-xl" />
              </div>
            ))}
          </div>
        ) : products.length === 0 ? (
          <div className="text-center py-20 rounded-2xl glass-panel border border-white/5">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
              strokeWidth={1.5}
              stroke="currentColor"
              className="w-12 h-12 mx-auto text-gray-600 mb-4"
            >
              <path strokeLinecap="round" strokeLinejoin="round" d="m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.602 10.602Z" />
            </svg>
            <p className="text-gray-400 font-medium text-lg">No premium devices match your search.</p>
            <button onClick={() => setSearch('')} className="mt-2 text-indigo-400 hover:text-indigo-300 font-semibold text-sm">
              Clear filters
            </button>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-8">
            {products.map((product) => (
              <Link
                href={`/product/${product.id}`}
                key={product.id}
                className="group flex flex-col justify-between h-[450px] rounded-2xl overflow-hidden glass-panel border border-white/10 hover:border-indigo-500/40 hover:shadow-2xl hover:shadow-indigo-500/5 hover:-translate-y-1 transition-all duration-300 relative"
              >
                {/* Product Photograph Banner */}
                <div className="h-48 relative overflow-hidden bg-gray-950">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img
                    src={getProductImage(product.sku)}
                    alt={product.name}
                    className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
                  />
                  <div className="absolute inset-0 bg-gradient-to-t from-gray-950/80 to-transparent" />
                  <span className="absolute top-4 left-4 px-2 py-0.5 rounded-md border border-white/20 bg-black/60 text-[10px] text-gray-300 font-mono tracking-widest uppercase">
                    {product.sku}
                  </span>
                </div>

                {/* Info and Description */}
                <div className="p-6 flex-grow flex flex-col justify-between gap-4">
                  <div className="space-y-2">
                    <h3 className="text-lg font-bold text-white group-hover:text-indigo-400 transition-colors">
                      {product.name}
                    </h3>
                    <p className="text-gray-400 text-xs line-clamp-2 leading-relaxed">
                      {product.description}
                    </p>
                  </div>

                  <div className="flex items-end justify-between mt-auto">
                    <div className="flex flex-col">
                      <span className="text-[10px] text-gray-500 uppercase tracking-widest font-mono">Price</span>
                      <span className="text-xl font-black text-white bg-gradient-to-r from-white to-gray-400 bg-clip-text text-transparent">
                        {formatPrice(product.price)}
                      </span>
                    </div>

                    <button
                      onClick={(e) => handleAddToCart(e, product.id)}
                      disabled={addingId === product.id}
                      className="px-4 py-2.5 rounded-xl bg-white/5 border border-white/10 hover:bg-indigo-600 hover:border-indigo-500 text-xs font-bold text-white hover:shadow-lg hover:shadow-indigo-500/20 active:scale-[0.97] transition-all flex items-center gap-1.5 cursor-pointer disabled:opacity-50"
                    >
                      {addingId === product.id ? (
                        <div className="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                      ) : (
                        <svg
                          xmlns="http://www.w3.org/2000/svg"
                          fill="none"
                          viewBox="0 0 24 24"
                          strokeWidth={2}
                          stroke="currentColor"
                          className="w-3.5 h-3.5"
                        >
                          <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
                        </svg>
                      )}
                      Cart
                    </button>
                  </div>
                </div>
              </Link>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
