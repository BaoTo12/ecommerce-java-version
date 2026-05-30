"use client";

import React, { useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/context/AuthContext';
import { useRouter } from 'next/navigation';

export default function RegisterPage() {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const { register } = useAuth();
  const router = useRouter();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const success = await register(email, password, name, phone);
      if (success) {
        router.push('/');
      }
    } catch (err: any) {
      setError(err.message || 'Registration failed. Email might already be taken.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-[85vh] items-center justify-center py-12 px-4 sm:px-6 lg:px-8 relative">
      <div className="w-full max-w-md space-y-8 glass-panel border border-white/10 rounded-3xl p-8 md:p-10 shadow-2xl">
        {/* Title */}
        <div className="text-center space-y-2">
          <h2 className="text-3xl font-black tracking-tight text-white">
            Create an <span className="bg-gradient-to-r from-cyan-400 to-indigo-400 bg-clip-text text-transparent">AETHER</span> Account
          </h2>
          <p className="text-sm text-gray-400">
            Sign up to access and track premium tech orders.
          </p>
        </div>

        {/* Form */}
        <form className="space-y-5" onSubmit={handleSubmit}>
          {error && (
            <div className="p-4 rounded-xl border border-red-500/20 bg-red-500/10 text-xs font-semibold text-red-400 text-center animate-shake">
              {error}
            </div>
          )}

          <div className="space-y-4">
            <div>
              <label htmlFor="full-name" className="block text-xs font-mono font-semibold uppercase tracking-wider text-gray-400 mb-2">
                Full Name
              </label>
              <input
                id="full-name"
                name="name"
                type="text"
                required
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="John Doe"
                className="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder-gray-500 outline-none focus:border-indigo-500/50 focus:ring-2 focus:ring-indigo-500/10 transition-all text-sm"
              />
            </div>

            <div>
              <label htmlFor="email-address" className="block text-xs font-mono font-semibold uppercase tracking-wider text-gray-400 mb-2">
                Email Address
              </label>
              <input
                id="email-address"
                name="email"
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="john@example.com"
                className="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder-gray-500 outline-none focus:border-indigo-500/50 focus:ring-2 focus:ring-indigo-500/10 transition-all text-sm"
              />
            </div>

            <div>
              <label htmlFor="phone-number" className="block text-xs font-mono font-semibold uppercase tracking-wider text-gray-400 mb-2">
                Phone Number
              </label>
              <input
                id="phone-number"
                name="phone"
                type="tel"
                required
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                placeholder="0912345678"
                className="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder-gray-500 outline-none focus:border-indigo-500/50 focus:ring-2 focus:ring-indigo-500/10 transition-all text-sm"
              />
            </div>

            <div>
              <label htmlFor="password" className="block text-xs font-mono font-semibold uppercase tracking-wider text-gray-400 mb-2">
                Password
              </label>
              <input
                id="password"
                name="password"
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                className="w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 text-white placeholder-gray-500 outline-none focus:border-indigo-500/50 focus:ring-2 focus:ring-indigo-500/10 transition-all text-sm"
              />
            </div>
          </div>

          <div>
            <button
              type="submit"
              disabled={loading}
              className="w-full py-3.5 px-4 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-sm font-bold text-white shadow-lg shadow-indigo-600/20 hover:shadow-indigo-600/30 hover:scale-[1.02] active:scale-[0.98] transition-all flex items-center justify-center gap-2 cursor-pointer disabled:opacity-50"
            >
              {loading ? (
                <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin" />
              ) : (
                'Create Account'
              )}
            </button>
          </div>
        </form>

        {/* Footer */}
        <div className="text-center text-xs text-gray-500 pt-4 border-t border-white/5">
          Already have an account?{' '}
          <Link href="/login" className="text-indigo-400 hover:text-indigo-300 font-bold transition-colors">
            Sign In
          </Link>
        </div>
      </div>
    </div>
  );
}
