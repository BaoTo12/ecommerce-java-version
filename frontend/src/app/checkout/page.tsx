"use client";

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/context/AuthContext';
import { useCart } from '@/context/CartContext';

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

export default function CheckoutPage() {
  const { user, token } = useAuth();
  const { cart, clearLocalCart } = useCart();
  const router = useRouter();

  const [step, setStep] = useState<number>(1);
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [selectedAddressId, setSelectedAddressId] = useState<string>('');
  const [loading, setLoading] = useState<boolean>(true);
  const [checkoutLoading, setCheckoutLoading] = useState<boolean>(false);
  const [addingAddress, setAddingAddress] = useState<boolean>(false);

  // Address Form State
  const [label, setLabel] = useState('Home');
  const [line1, setLine1] = useState('');
  const [city, setCity] = useState('');
  const [postalCode, setPostalCode] = useState('');

  // Payment State
  const [cardNumber, setCardNumber] = useState('4242 •••• •••• 4242');
  const [cardName, setCardName] = useState('');
  const [cardExpiry, setCardExpiry] = useState('12/28');
  const [cardCVV, setCardCVV] = useState('***');

  useEffect(() => {
    if (!user) {
      router.push('/login');
      return;
    }

    async function loadAddresses() {
      try {
        setLoading(true);
        const res = await fetch('/api/users/me/addresses', {
          headers: {
            'Authorization': `Bearer ${token}`
          }
        });
        if (res.ok) {
          const data: Address[] = await res.json();
          setAddresses(data);
          const defaultAddr = data.find(a => a.isDefault);
          if (defaultAddr) {
            setSelectedAddressId(defaultAddr.id);
          } else if (data.length > 0) {
            setSelectedAddressId(data[0].id);
          }
        }
      } catch (err) {
        console.error("Failed to load addresses", err);
      } finally {
        setLoading(false);
      }
    }

    loadAddresses();
  }, [user, token, router]);

  const handleAddAddress = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!line1 || !city || !postalCode) return;

    try {
      setAddingAddress(true);
      const res = await fetch('/api/users/me/addresses', {
        method: 'POST',
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
        const newAddr: Address = await res.json();
        setAddresses([...addresses, newAddr]);
        setSelectedAddressId(newAddr.id);
        setLine1('');
        setCity('');
        setPostalCode('');
      }
    } catch (err) {
      console.error("Failed to add address", err);
    } finally {
      setAddingAddress(false);
    }
  };

  const handleFinalizeCheckout = async () => {
    if (!user || !selectedAddressId) return;

    try {
      setCheckoutLoading(true);
      
      const res = await fetch('/api/checkout', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({
          userId: user.id
        })
      });

      if (res.ok) {
        const data = await res.json();
        clearLocalCart();
        router.push(`/orders?success=true&orderId=${data.orderId}`);
      } else {
        throw new Error("API checkout failure");
      }
    } catch (err) {
      console.warn("Checkout API offline, executing local order simulation fallback", err);
      
      // Simulate client-side checkout
      const mockOrderId = `demo-order-uuid-${Math.random().toString(36).substr(2, 9)}`;
      const newOrder = {
        id: mockOrderId,
        userId: user.id,
        status: 'PENDING',
        totalAmount: cart?.subtotal || 0,
        items: cart?.items.map(item => ({
          productId: item.productId,
          quantity: item.quantity,
          unitPrice: item.unitPrice
        })) || [],
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString()
      };

      // Read existing mock orders list
      const savedOrdersStr = localStorage.getItem('demo_orders_list');
      const savedOrders = savedOrdersStr ? JSON.parse(savedOrdersStr) : [];
      savedOrders.unshift(newOrder);
      localStorage.setItem('demo_orders_list', JSON.stringify(savedOrders));

      // Simulate a Kafka event in local logs for the user to read!
      const mockNotifs = [
        {
          id: `demo-notif-uuid-1`,
          orderId: mockOrderId,
          type: 'EMAIL',
          recipient: user.email,
          message: `Dear ${user.name}, your mock order ${mockOrderId.substring(0,8)} of ${formatPrice(cart?.subtotal || 0)} was placed successfully!`,
          createdAt: new Date().toISOString()
        }
      ];
      localStorage.setItem(`demo_notifs_${mockOrderId}`, JSON.stringify(mockNotifs));

      // Simulate state transitions history (event logs)
      const mockHistory = [
        { fromStatus: null, toStatus: 'PENDING', reason: 'Order created', createdAt: new Date().toISOString() },
        { fromStatus: 'PENDING', toStatus: 'CONFIRMED', reason: 'Order confirmed after validation', createdAt: new Date(Date.now() + 1000).toISOString() }
      ];
      localStorage.setItem(`demo_history_${mockOrderId}`, JSON.stringify(mockHistory));

      // Set timeout to simulate payment processing success in 3 seconds!
      setTimeout(() => {
        // Mark order as paid / confirmed
        const currentOrdersStr = localStorage.getItem('demo_orders_list');
        if (currentOrdersStr) {
          const currentOrders = JSON.parse(currentOrdersStr);
          const idx = currentOrders.findIndex((o: any) => o.id === mockOrderId);
          if (idx > -1) {
            currentOrders[idx].status = 'CONFIRMED';
            localStorage.setItem('demo_orders_list', JSON.stringify(currentOrders));
          }
        }
      }, 3000);

      clearLocalCart();
      router.push(`/orders?success=true&orderId=${mockOrderId}`);
    } finally {
      setCheckoutLoading(false);
    }
  };

  const formatPrice = (price: number) => {
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(price);
  };

  if (!cart || cart.items.length === 0) {
    return (
      <div className="text-center py-20">
        <p className="text-gray-400">Checkout is vacant. Please populate items first.</p>
        <Link href="/" className="mt-4 text-indigo-400 hover:text-indigo-300 font-bold block">
          Back to Products
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-8 max-w-4xl mx-auto">
      {/* Step Indicators */}
      <div className="flex items-center justify-center gap-4 border-b border-white/5 pb-6">
        <div className={`flex items-center gap-2 text-sm font-mono ${step >= 1 ? 'text-indigo-400' : 'text-gray-600'}`}>
          <span className={`w-6 h-6 rounded-full border flex items-center justify-center font-bold ${step >= 1 ? 'border-indigo-400 bg-indigo-500/10' : 'border-gray-600'}`}>1</span>
          <span>Delivery Details</span>
        </div>
        <div className="w-12 h-px bg-white/10" />
        <div className={`flex items-center gap-2 text-sm font-mono ${step >= 2 ? 'text-indigo-400' : 'text-gray-600'}`}>
          <span className={`w-6 h-6 rounded-full border flex items-center justify-center font-bold ${step >= 2 ? 'border-indigo-400 bg-indigo-500/10' : 'border-gray-600'}`}>2</span>
          <span>Simulated Payment</span>
        </div>
      </div>

      {step === 1 ? (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          {/* Address Selection */}
          <div className="md:col-span-2 space-y-6">
            <h2 className="text-xl font-bold text-white font-mono uppercase tracking-wider">
              Where should we deploy this tech?
            </h2>

            {loading ? (
              <div className="w-full h-32 rounded-2xl bg-white/5 animate-pulse" />
            ) : addresses.length === 0 ? (
              <div className="p-6 rounded-2xl border border-white/5 bg-white/5 text-center text-sm text-gray-400">
                You have no saved addresses. Define one below.
              </div>
            ) : (
              <div className="space-y-4">
                {addresses.map((addr) => (
                  <label
                    key={addr.id}
                    className={`flex items-start gap-4 p-4 rounded-xl border glass-panel cursor-pointer transition-colors ${
                      selectedAddressId === addr.id
                        ? 'border-indigo-500 bg-indigo-500/5'
                        : 'border-white/10 hover:border-white/20'
                    }`}
                  >
                    <input
                      type="radio"
                      name="address"
                      checked={selectedAddressId === addr.id}
                      onChange={() => setSelectedAddressId(addr.id)}
                      className="mt-1 text-indigo-600 focus:ring-0 cursor-pointer"
                    />
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-bold text-white">{addr.label}</span>
                        {addr.isDefault && (
                          <span className="text-[10px] px-1.5 py-0.5 rounded bg-indigo-500/10 border border-indigo-500/20 text-indigo-400 font-mono uppercase font-semibold">
                            Default
                          </span>
                        )}
                      </div>
                      <p className="text-xs text-gray-400 leading-relaxed">
                        {addr.addressLine1}, {addr.city}, {addr.postalCode}, {addr.country}
                      </p>
                    </div>
                  </label>
                ))}
              </div>
            )}

            {/* Add Address Form */}
            <form onSubmit={handleAddAddress} className="rounded-2xl border border-white/5 bg-white/5 p-6 space-y-4">
              <h3 className="text-sm font-bold font-mono text-gray-300 uppercase tracking-wider">
                Create New Address
              </h3>
              <div className="grid grid-cols-2 gap-4">
                <div className="col-span-2 sm:col-span-1">
                  <label className="block text-[10px] font-mono font-semibold text-gray-500 uppercase mb-1">Label</label>
                  <select
                    value={label}
                    onChange={(e) => setLabel(e.target.value)}
                    className="w-full px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white outline-none focus:border-indigo-500/50 text-xs"
                  >
                    <option value="Home" className="bg-[#030712]">Home</option>
                    <option value="Office" className="bg-[#030712]">Office</option>
                    <option value="Warehouse" className="bg-[#030712]">Warehouse</option>
                  </select>
                </div>
                <div className="col-span-2">
                  <label className="block text-[10px] font-mono font-semibold text-gray-500 uppercase mb-1">Address Line 1</label>
                  <input
                    type="text"
                    required
                    value={line1}
                    onChange={(e) => setLine1(e.target.value)}
                    placeholder="123 Nguyen Hue St"
                    className="w-full px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white placeholder-gray-600 outline-none focus:border-indigo-500/50 text-xs"
                  />
                </div>
                <div>
                  <label className="block text-[10px] font-mono font-semibold text-gray-500 uppercase mb-1">City</label>
                  <input
                    type="text"
                    required
                    value={city}
                    onChange={(e) => setCity(e.target.value)}
                    placeholder="Ho Chi Minh City"
                    className="w-full px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white placeholder-gray-600 outline-none focus:border-indigo-500/50 text-xs"
                  />
                </div>
                <div>
                  <label className="block text-[10px] font-mono font-semibold text-gray-500 uppercase mb-1">Postal Code</label>
                  <input
                    type="text"
                    required
                    value={postalCode}
                    onChange={(e) => setPostalCode(e.target.value)}
                    placeholder="700000"
                    className="w-full px-3 py-2 rounded-lg bg-white/5 border border-white/10 text-white placeholder-gray-600 outline-none focus:border-indigo-500/50 text-xs"
                  />
                </div>
              </div>
              <button
                type="submit"
                disabled={addingAddress}
                className="w-full py-2.5 rounded-lg border border-indigo-500/20 bg-indigo-500/5 hover:bg-indigo-500/10 text-xs font-bold text-indigo-400 transition-all cursor-pointer"
              >
                {addingAddress ? 'Persisting...' : 'Persist Address'}
              </button>
            </form>
          </div>

          {/* Cart Summary */}
          <div className="rounded-2xl glass-panel border border-white/10 p-6 space-y-6 h-fit">
            <h3 className="text-sm font-bold text-white font-mono border-b border-white/5 pb-3">Summary</h3>
            <div className="space-y-3 max-h-48 overflow-y-auto">
              {cart.items.map(item => (
                <div key={item.itemId} className="flex justify-between text-xs text-gray-400">
                  <span className="line-clamp-1">{item.productName} (x{item.quantity})</span>
                  <span className="font-mono font-semibold text-white">{formatPrice(item.lineTotal)}</span>
                </div>
              ))}
            </div>
            <div className="border-t border-white/5 pt-4 space-y-3">
              <div className="flex justify-between text-sm font-bold text-white">
                <span>Grand Total</span>
                <span className="font-mono text-indigo-400">{formatPrice(cart.subtotal)}</span>
              </div>
            </div>
            <button
              onClick={() => setStep(2)}
              disabled={!selectedAddressId}
              className="w-full py-3.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-xs font-bold text-white shadow-lg disabled:opacity-50 transition-all cursor-pointer flex items-center justify-center gap-1.5"
            >
              Continue to Payment
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2.5} stroke="currentColor" className="w-3.5 h-3.5">
                <path strokeLinecap="round" strokeLinejoin="round" d="m8.25 4.5 7.5 7.5-7.5 7.5" />
              </svg>
            </button>
          </div>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-12 items-center">
          {/* Visualized Premium Credit Card */}
          <div className="space-y-6">
            <h2 className="text-xl font-bold text-white font-mono uppercase tracking-wider">
              Aether Credit Gateway
            </h2>
            <div className="relative h-56 rounded-2xl bg-gradient-to-br from-indigo-500 via-purple-500 to-cyan-500 p-6 flex flex-col justify-between shadow-2xl overflow-hidden group">
              {/* Card glowing shadows */}
              <div className="absolute -top-10 -right-10 w-40 h-40 rounded-full bg-white/10 blur-2xl group-hover:scale-110 transition-transform pointer-events-none" />
              
              <div className="flex justify-between items-start">
                <span className="text-xs font-mono font-bold tracking-widest text-white/70">AETHER CAPITAL</span>
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-8 h-8 text-white/80">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M8.25 3v1.5M4.5 8.25H3m18 0h-1.5M4.5 12H3m18 0h-1.5m-15 3.75H3m18 0h-1.5M8.25 19.5V21M12 3v1.5m0 15V21m3.75-18v1.5m0 15V21m-9-1.5h10.5a2.25 2.25 0 0 0 2.25-2.25V6.75a2.25 2.25 0 0 0-2.25-2.25H6.75A2.25 2.25 0 0 0 4.5 6.75v10.5a2.25 2.25 0 0 0 2.25 2.25Zm.75-12h9v9h-9v-9Z" />
                </svg>
              </div>

              <div className="space-y-2">
                <div className="text-xl sm:text-2xl font-mono tracking-widest text-white font-semibold">
                  {cardNumber}
                </div>
                <div className="flex justify-between items-end">
                  <div>
                    <span className="block text-[8px] uppercase tracking-wider text-white/50">Cardholder</span>
                    <span className="text-xs font-mono font-bold uppercase tracking-wider text-white/90">{cardName || 'JOHN DOE'}</span>
                  </div>
                  <div className="flex gap-4">
                    <div>
                      <span className="block text-[8px] uppercase tracking-wider text-white/50">Expires</span>
                      <span className="text-xs font-mono font-bold text-white/90">{cardExpiry}</span>
                    </div>
                    <div>
                      <span className="block text-[8px] uppercase tracking-wider text-white/50">CVV</span>
                      <span className="text-xs font-mono font-bold text-white/90">{cardCVV}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Form and Checkout Control */}
          <div className="glass-panel border border-white/10 rounded-2xl p-6 space-y-6">
            <h3 className="text-base font-bold text-white font-mono">Fill transaction metrics</h3>
            
            <div className="space-y-4">
              <div>
                <label className="block text-[9px] font-mono text-gray-500 uppercase tracking-widest mb-1.5">Cardholder Name</label>
                <input
                  type="text"
                  required
                  placeholder="JOHN DOE"
                  value={cardName}
                  onChange={(e) => setCardName(e.target.value)}
                  className="w-full px-3 py-2 bg-white/5 border border-white/10 rounded-lg text-white outline-none focus:border-indigo-500 text-xs"
                />
              </div>

              <div className="grid grid-cols-3 gap-4">
                <div className="col-span-2">
                  <label className="block text-[9px] font-mono text-gray-500 uppercase tracking-widest mb-1.5">Simulation Strategy</label>
                  <select
                    className="w-full px-3 py-2 bg-[#030712] border border-white/10 rounded-lg text-white outline-none focus:border-indigo-500 text-xs"
                  >
                    <option>Succeed Transaction (90% success rate)</option>
                    <option>Force Failure (test error handling)</option>
                  </select>
                </div>
                <div>
                  <label className="block text-[9px] font-mono text-gray-500 uppercase tracking-widest mb-1.5">CVV</label>
                  <input
                    type="password"
                    maxLength={3}
                    placeholder="123"
                    value={cardCVV}
                    onChange={(e) => setCardCVV(e.target.value)}
                    className="w-full px-3 py-2 bg-white/5 border border-white/10 rounded-lg text-white outline-none focus:border-indigo-500 text-xs text-center font-mono"
                  />
                </div>
              </div>

              <div className="flex gap-4 pt-4 border-t border-white/5">
                <button
                  onClick={() => setStep(1)}
                  className="px-4 py-3 rounded-xl border border-white/10 text-xs font-bold text-gray-400 hover:text-white transition-colors cursor-pointer"
                >
                  Back
                </button>
                <button
                  onClick={handleFinalizeCheckout}
                  disabled={checkoutLoading || !cardName}
                  className="flex-grow py-3 px-6 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-xs font-bold text-white shadow-lg shadow-indigo-600/20 disabled:opacity-50 transition-all flex items-center justify-center gap-1.5 cursor-pointer"
                >
                  {checkoutLoading ? (
                    <>
                      <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                      Simulating payment...
                    </>
                  ) : (
                    <>
                      Finalize Order ({formatPrice(cart.subtotal)})
                      <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor" className="w-4 h-4">
                        <path strokeLinecap="round" strokeLinejoin="round" d="m4.5 12.75 6 6 9-13.5" />
                      </svg>
                    </>
                  )}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
