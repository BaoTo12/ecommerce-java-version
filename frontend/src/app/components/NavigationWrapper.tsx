"use client";

import React from 'react';
import Link from 'next/link';
import { useAuth } from '@/context/AuthContext';
import { useCart } from '@/context/CartContext';
import { useRouter, usePathname } from 'next/navigation';

export default function NavigationWrapper() {
  const { user, logout } = useAuth();
  const { cart } = useCart();
  const pathname = usePathname();

  const cartItemCount = cart?.items.reduce((total, item) => total + item.quantity, 0) || 0;

  const isActive = (path: string) => pathname === path;

  return (
    <header className="sticky top-0 z-50 w-full border-b border-white/10 glass-panel backdrop-blur-md">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex h-16 items-center justify-between">
          {/* Logo Brand */}
          <div className="flex items-center gap-8">
            <Link href="/" className="flex items-center gap-2 group">
              <span className="text-2xl font-black tracking-widest bg-gradient-to-r from-indigo-400 via-purple-400 to-cyan-400 bg-clip-text text-transparent group-hover:opacity-90 transition-opacity">
                AETHER
              </span>
              <span className="text-[10px] px-2 py-0.5 rounded-full border border-indigo-500/30 bg-indigo-500/10 text-indigo-400 uppercase tracking-widest font-semibold font-mono">
                Store
              </span>
            </Link>

            {/* Desktop Nav */}
            <nav className="hidden md:flex items-center gap-6">
              <Link
                href="/"
                className={`text-sm font-medium transition-colors hover:text-white ${
                  isActive('/') ? 'text-indigo-400 font-semibold' : 'text-gray-400'
                }`}
              >
                Products
              </Link>
              {user && (
                <>
                  <Link
                    href="/orders"
                    className={`text-sm font-medium transition-colors hover:text-white ${
                      isActive('/orders') ? 'text-indigo-400 font-semibold' : 'text-gray-400'
                    }`}
                  >
                    My Orders
                  </Link>
                  <Link
                    href="/profile"
                    className={`text-sm font-medium transition-colors hover:text-white ${
                      isActive('/profile') ? 'text-indigo-400 font-semibold' : 'text-gray-400'
                    }`}
                  >
                    Profile & Addresses
                  </Link>
                </>
              )}
            </nav>
          </div>

          {/* User Controls & Cart */}
          <div className="flex items-center gap-4">
            {/* Developer Console Toggle */}
            <Link
              href="/console"
              className="hidden sm:inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-purple-500/20 bg-purple-500/5 text-xs font-mono font-medium text-purple-400 hover:bg-purple-500/10 transition-colors"
            >
              <span className="w-1.5 h-1.5 rounded-full bg-purple-400 animate-pulse" />
              Dev Console
            </Link>

            {/* Shopping Cart Icon */}
            {user && (
              <Link
                href="/cart"
                className={`relative p-2 rounded-xl border border-white/5 bg-white/5 text-gray-300 hover:text-white hover:bg-white/10 hover:border-white/10 transition-all ${
                  isActive('/cart') ? 'border-indigo-500/30 text-indigo-400 bg-indigo-500/5' : ''
                }`}
              >
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  fill="none"
                  viewBox="0 0 24 24"
                  strokeWidth={1.5}
                  stroke="currentColor"
                  className="w-5 h-5"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    d="M2.25 3h1.386c.51 0 .955.343 1.087.835l.383 1.437M7.5 14.25a3 3 0 0 0-3 3h15.75m-12.75-3h11.218c1.121-2.3 2.1-4.684 2.924-7.138a60.114 60.114 0 0 0-16.536-1.84M7.5 14.25L5.106 5.272M6 20.25a.75.75 0 1 1-1.5 0 .75.75 0 0 1 1.5 0Zm12.75 0a.75.75 0 1 1-1.5 0 .75.75 0 0 1 1.5 0Z"
                  />
                </svg>
                {cartItemCount > 0 && (
                  <span className="absolute -top-1.5 -right-1.5 flex h-5 w-5 items-center justify-center rounded-full bg-indigo-600 text-[10px] font-bold text-white ring-2 ring-[#030712]">
                    {cartItemCount}
                  </span>
                )}
              </Link>
            )}

            {/* Auth Buttons */}
            {user ? (
              <div className="flex items-center gap-3">
                <div className="flex items-center gap-2">
                  <div className="flex h-8 w-8 items-center justify-center rounded-full bg-gradient-to-br from-indigo-500 to-purple-600 text-sm font-bold text-white shadow-lg shadow-indigo-500/20">
                    {user.name.charAt(0).toUpperCase()}
                  </div>
                  <span className="hidden lg:inline text-sm text-gray-300 font-medium">
                    {user.name}
                  </span>
                </div>
                <button
                  onClick={logout}
                  className="px-3 py-1.5 rounded-lg border border-white/10 hover:border-red-500/30 hover:bg-red-500/10 text-xs font-semibold text-gray-400 hover:text-red-400 transition-all cursor-pointer"
                >
                  Logout
                </button>
              </div>
            ) : (
              <div className="flex items-center gap-2">
                <Link
                  href="/login"
                  className="px-3 py-1.5 rounded-lg text-xs font-semibold text-gray-400 hover:text-white transition-colors"
                >
                  Sign In
                </Link>
                <Link
                  href="/register"
                  className="px-4 py-2 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-xs font-semibold text-white shadow-lg shadow-indigo-600/20 hover:shadow-indigo-600/30 hover:scale-[1.02] active:scale-[0.98] transition-all"
                >
                  Sign Up
                </Link>
              </div>
            )}
          </div>
        </div>
      </div>
    </header>
  );
}
