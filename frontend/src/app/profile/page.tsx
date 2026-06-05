"use client";

import React, { useEffect, useState, useCallback } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useRouter } from 'next/navigation';

interface Address {
  id: string;
  label: string;
  addressLine1: string;
  addressLine2: string | null;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  isDefault: boolean;
}

export default function ProfilePage() {
  const { user, token, isDemoMode, updateProfileState, refreshProfile } = useAuth();
  const router = useRouter();

  // Profile Edit State
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [profileLoading, setProfileLoading] = useState(false);
  const [profileSuccess, setProfileSuccess] = useState(false);

  // Address CRUD State
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [addressesLoading, setAddressesLoading] = useState(true);
  const [editingAddressId, setEditingAddressId] = useState<string | null>(null);
  
  // Address Form State
  const [label, setLabel] = useState('Home');
  const [line1, setLine1] = useState('');
  const [city, setCity] = useState('');
  const [postalCode, setPostalCode] = useState('');

  const fetchAddresses = useCallback(async () => {
    if (!token) return;

    if (isDemoMode || token.startsWith('mock-')) {
      const savedAddrs = localStorage.getItem('demo_addresses');
      if (savedAddrs) {
        setAddresses(JSON.parse(savedAddrs));
      } else {
        const initial = [
          {
            id: 'demo-address-id-1',
            label: 'Home',
            addressLine1: '123 Nguyen Hue St',
            addressLine2: '',
            city: 'Ho Chi Minh City',
            state: 'Ho Chi Minh City',
            postalCode: '700000',
            country: 'Vietnam',
            isDefault: true
          }
        ];
        localStorage.setItem('demo_addresses', JSON.stringify(initial));
        setAddresses(initial);
      }
      setAddressesLoading(false);
      return;
    }

    try {
      setAddressesLoading(true);
      const res = await fetch('/api/users/me/addresses', {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (res.ok) {
        const data = await res.json();
        setAddresses(data);
      }
    } catch (err) {
      console.error("Failed to load addresses", err);
    } finally {
      setAddressesLoading(false);
    }
  }, [token, isDemoMode]);

  useEffect(() => {
    if (!user) {
      router.push('/login');
      return;
    }
    setName(user.name);
    setPhone(user.phone);
    fetchAddresses();
  }, [user, router, fetchAddresses]);

  const handleUpdateProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!token) return;

    if (isDemoMode || token.startsWith('mock-')) {
      setProfileLoading(true);
      const updated = { ...user!, name, phone };
      updateProfileState(updated);
      setProfileSuccess(true);
      setProfileLoading(false);
      setTimeout(() => setProfileSuccess(false), 3000);
      return;
    }

    try {
      setProfileLoading(true);
      setProfileSuccess(false);
      const res = await fetch('/api/users/me', {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({ name, phone })
      });

      if (res.ok) {
        const updated = await res.json();
        updateProfileState(updated);
        setProfileSuccess(true);
        setTimeout(() => setProfileSuccess(false), 3000);
      }
    } catch (err) {
      console.error("Failed to update profile", err);
    } finally {
      setProfileLoading(false);
    }
  };

  const handleSaveAddress = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!token || !line1 || !city || !postalCode) return;

    if (isDemoMode || token.startsWith('mock-')) {
      const makeDefault = addresses.length === 0;
      let updatedList: Address[];
      if (editingAddressId) {
        updatedList = addresses.map(addr => addr.id === editingAddressId ? {
          ...addr, label, addressLine1: line1, city, postalCode
        } : addr);
      } else {
        const newAddr: Address = {
          id: `demo-address-uuid-${Math.random().toString(36).substring(2, 11)}`,
          label,
          addressLine1: line1,
          addressLine2: '',
          city,
          state: city,
          postalCode,
          country: 'Vietnam',
          isDefault: makeDefault
        };
        updatedList = [...addresses, newAddr];
      }
      localStorage.setItem('demo_addresses', JSON.stringify(updatedList));
      setAddresses(updatedList);
      setEditingAddressId(null);
      setLine1('');
      setCity('');
      setPostalCode('');
      return;
    }

    try {
      const url = editingAddressId 
        ? `/api/users/me/addresses/${editingAddressId}`
        : '/api/users/me/addresses';
      const method = editingAddressId ? 'PUT' : 'POST';

      const res = await fetch(url, {
        method: method,
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({
          label,
          addressLine1: line1,
          addressLine2: '',
          city,
          state: city,
          postalCode,
          country: 'Vietnam',
          isDefault: addresses.length === 0
        })
      });

      if (res.ok) {
        setEditingAddressId(null);
        setLine1('');
        setCity('');
        setPostalCode('');
        await fetchAddresses();
      }
    } catch (err) {
      console.error("Failed to persist address", err);
    }
  };

  const handleEditClick = (addr: Address) => {
    setEditingAddressId(addr.id);
    setLabel(addr.label);
    setLine1(addr.addressLine1);
    setCity(addr.city);
    setPostalCode(addr.postalCode);
  };

  const handleDeleteAddress = async (addressId: string) => {
    if (!confirm("Are you sure you want to delete this address?")) return;

    if (isDemoMode || (token && token.startsWith('mock-'))) {
      const updatedList = addresses.filter(addr => addr.id !== addressId);
      if (addresses.find(a => a.id === addressId)?.isDefault && updatedList.length > 0) {
        updatedList[0].isDefault = true;
      }
      localStorage.setItem('demo_addresses', JSON.stringify(updatedList));
      setAddresses(updatedList);
      return;
    }

    try {
      const res = await fetch(`/api/users/me/addresses/${addressId}`, {
        method: 'DELETE',
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (res.ok) {
        await fetchAddresses();
      }
    } catch (err) {
      console.error("Failed to delete address", err);
    }
  };

  return (
    <div className="space-y-8">
      {/* Title */}
      <div className="border-b border-white/5 pb-4">
        <h1 className="text-2xl font-black text-white tracking-wide uppercase font-mono">
          Profile Settings
        </h1>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Profile Card */}
        <div className="rounded-2xl glass-panel border border-white/10 p-6 space-y-6 h-fit">
          <h2 className="text-base font-bold font-mono text-gray-300 uppercase tracking-wider">
            Identity Specifications
          </h2>

          <form onSubmit={handleUpdateProfile} className="space-y-4">
            {profileSuccess && (
              <div className="p-3 text-center text-xs font-semibold rounded-lg border border-green-500/20 bg-green-500/10 text-green-400">
                Identity metrics updated successfully!
              </div>
            )}

            <div>
              <label className="block text-[10px] font-mono text-gray-500 uppercase tracking-widest mb-1.5">Email Address (readonly)</label>
              <input
                type="email"
                readOnly
                value={user?.email || ''}
                className="w-full px-3 py-2.5 bg-white/5 border border-white/5 rounded-lg text-gray-500 outline-none text-xs cursor-not-allowed font-mono"
              />
            </div>

            <div>
              <label className="block text-[10px] font-mono text-gray-500 uppercase tracking-widest mb-1.5">Full Name</label>
              <input
                type="text"
                required
                value={name}
                onChange={(e) => setName(e.target.value)}
                className="w-full px-3 py-2.5 bg-white/5 border border-white/10 rounded-lg text-white outline-none focus:border-indigo-500 text-xs"
              />
            </div>

            <div>
              <label className="block text-[10px] font-mono text-gray-500 uppercase tracking-widest mb-1.5">Phone Number</label>
              <input
                type="tel"
                required
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                className="w-full px-3 py-2.5 bg-white/5 border border-white/10 rounded-lg text-white outline-none focus:border-indigo-500 text-xs"
              />
            </div>

            <button
              type="submit"
              disabled={profileLoading}
              className="w-full py-3 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-xs font-bold text-white shadow-lg transition-all cursor-pointer disabled:opacity-50"
            >
              {profileLoading ? 'Updating...' : 'Update Identity'}
            </button>
          </form>
        </div>

        {/* Saved Addresses Panel */}
        <div className="lg:col-span-2 space-y-6">
          <div className="rounded-2xl glass-panel border border-white/10 p-6 space-y-6">
            <h2 className="text-base font-bold font-mono text-gray-300 uppercase tracking-wider">
              Deployment Addresses (Address Book)
            </h2>

            {addressesLoading ? (
              <div className="h-24 bg-white/5 animate-pulse rounded-xl" />
            ) : addresses.length === 0 ? (
              <p className="text-sm text-gray-400 italic">No saved addresses found. Define one using the controller below.</p>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                {addresses.map((addr) => (
                  <div
                    key={addr.id}
                    className={`p-4 rounded-xl border flex flex-col justify-between gap-4 ${
                      addr.isDefault 
                        ? 'border-indigo-500/40 bg-indigo-500/5'
                        : 'border-white/10 bg-white/5'
                    }`}
                  >
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-bold text-white">{addr.label}</span>
                        {addr.isDefault && (
                          <span className="text-[9px] px-1.5 py-0.5 rounded bg-indigo-500/10 border border-indigo-500/20 text-indigo-400 font-mono font-semibold">
                            Default
                          </span>
                        )}
                      </div>
                      <p className="text-xs text-gray-400 leading-relaxed font-mono">
                        {addr.addressLine1}, {addr.city}, {addr.postalCode}, {addr.country}
                      </p>
                    </div>

                    <div className="flex justify-end gap-2 border-t border-white/5 pt-3">
                      <button
                        onClick={() => handleEditClick(addr)}
                        className="px-2.5 py-1 rounded bg-white/5 hover:bg-white/10 text-[10px] font-bold text-gray-300 transition-all cursor-pointer"
                      >
                        Edit
                      </button>
                      <button
                        onClick={() => handleDeleteAddress(addr.id)}
                        className="px-2.5 py-1 rounded bg-red-500/5 hover:bg-red-500/10 text-[10px] font-bold text-red-400 transition-all cursor-pointer"
                      >
                        Delete
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Address Controller Form */}
          <div className="rounded-2xl glass-panel border border-white/10 p-6 space-y-6">
            <h2 className="text-base font-bold font-mono text-gray-300 uppercase tracking-wider">
              {editingAddressId ? 'Edit Address Entity' : 'Create Address Entity'}
            </h2>

            <form onSubmit={handleSaveAddress} className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-[10px] font-mono text-gray-500 uppercase tracking-widest mb-1.5">Label</label>
                  <select
                    value={label}
                    onChange={(e) => setLabel(e.target.value)}
                    className="w-full px-3 py-2 bg-[#030712] border border-white/10 rounded-lg text-white outline-none focus:border-indigo-500 text-xs"
                  >
                    <option value="Home">Home</option>
                    <option value="Office">Office</option>
                    <option value="Warehouse">Warehouse</option>
                  </select>
                </div>
                <div className="col-span-2">
                  <label className="block text-[10px] font-mono text-gray-500 uppercase tracking-widest mb-1.5">Address Line 1</label>
                  <input
                    type="text"
                    required
                    value={line1}
                    onChange={(e) => setLine1(e.target.value)}
                    placeholder="123 Nguyen Hue St"
                    className="w-full px-3 py-2 bg-white/5 border border-white/10 rounded-lg text-white outline-none focus:border-indigo-500 text-xs"
                  />
                </div>
                <div>
                  <label className="block text-[10px] font-mono text-gray-500 uppercase tracking-widest mb-1.5">City</label>
                  <input
                    type="text"
                    required
                    value={city}
                    onChange={(e) => setCity(e.target.value)}
                    placeholder="Ho Chi Minh City"
                    className="w-full px-3 py-2 bg-white/5 border border-white/10 rounded-lg text-white outline-none focus:border-indigo-500 text-xs"
                  />
                </div>
                <div>
                  <label className="block text-[10px] font-mono text-gray-500 uppercase tracking-widest mb-1.5">Postal Code</label>
                  <input
                    type="text"
                    required
                    value={postalCode}
                    onChange={(e) => setPostalCode(e.target.value)}
                    placeholder="700000"
                    className="w-full px-3 py-2 bg-white/5 border border-white/10 rounded-lg text-white outline-none focus:border-indigo-500 text-xs"
                  />
                </div>
              </div>

              <div className="flex gap-3 pt-2">
                {editingAddressId && (
                  <button
                    type="button"
                    onClick={() => {
                      setEditingAddressId(null);
                      setLine1('');
                      setCity('');
                      setPostalCode('');
                    }}
                    className="px-4 py-2.5 rounded-lg border border-white/10 text-xs font-bold text-gray-400 hover:text-white transition-colors cursor-pointer"
                  >
                    Cancel Edit
                  </button>
                )}
                <button
                  type="submit"
                  className="flex-grow py-2.5 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-xs font-bold text-white shadow-lg transition-all cursor-pointer text-center"
                >
                  {editingAddressId ? 'Save Address Entity' : 'Create Address Entity'}
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>
  );
}
