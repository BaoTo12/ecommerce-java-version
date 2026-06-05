"use client";

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/context/AuthContext';
import { useCart } from '@/context/CartContext';
import { useToast } from '@/context/ToastContext';

interface Address {
  id: string;
  label: string;
  addressLine1: string;
  addressLine2?: string | null;
  city: string;
  state?: string | null;
  postalCode: string;
  country: string;
  isDefault: boolean;
}

interface CouponDiscountResult {
  couponId: string;
  code: string;
  discountAmount: number;
  finalAmount: number;
}

export default function CheckoutPage() {
  const { user, token, isDemoMode } = useAuth();
  const { cart, clearLocalCart } = useCart();
  const { showToast } = useToast();
  const router = useRouter();

  // Page UI state
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [selectedAddressId, setSelectedAddressId] = useState<string>('');
  const [showAddressSelector, setShowAddressSelector] = useState<boolean>(false);
  const [loading, setLoading] = useState<boolean>(true);
  const [checkoutLoading, setCheckoutLoading] = useState<boolean>(false);
  const [addingAddress, setAddingAddress] = useState<boolean>(false);

  // Address Form State
  const [label, setLabel] = useState('Home');
  const [line1, setLine1] = useState('');
  const [city, setCity] = useState('');
  const [postalCode, setPostalCode] = useState('');
  const [isNewAddressDefault, setIsNewAddressDefault] = useState(false);

  // Message / Notes
  const [notes, setNotes] = useState('');

  // Payment State
  const [paymentMethod, setPaymentMethod] = useState<'cod' | 'card'>('card');
  const [cardNumber, setCardNumber] = useState('4242 4242 4242 4242');
  const [cardName, setCardName] = useState('');
  const [cardExpiry, setCardExpiry] = useState('12/28');
  const [cardCVV, setCardCVV] = useState('123');
  const [simulationStrategy, setSimulationStrategy] = useState('SUCCEED');

  // Coupon States
  const [couponInput, setCouponInput] = useState('');
  const [appliedCoupon, setAppliedCoupon] = useState<CouponDiscountResult | null>(null);
  const [couponError, setCouponError] = useState('');
  const [validatingCoupon, setValidatingCoupon] = useState(false);

  const handleStrategyChange = (strategy: string) => {
    setSimulationStrategy(strategy);
    if (strategy === 'SUCCEED') {
      setCardNumber('4242 4242 4242 4242');
      setCardExpiry('12/28');
      setCardCVV('123');
    } else if (strategy === 'DECLINED') {
      setCardNumber('4000 0000 0000 0002');
      setCardExpiry('12/28');
      setCardCVV('123');
    } else if (strategy === 'INSUFFICIENT_FUNDS') {
      setCardNumber('4000 0000 0000 3022');
      setCardExpiry('12/28');
      setCardCVV('123');
    } else if (strategy === 'EXPIRED_CARD') {
      setCardNumber('4000 0000 0000 0115');
      setCardExpiry('01/20');
      setCardCVV('123');
    } else if (strategy === 'INCORRECT_CVC') {
      setCardNumber('4000 0000 0000 0123');
      setCardExpiry('12/28');
      setCardCVV('999');
    } else if (strategy === 'TIMEOUT') {
      setCardNumber('4242 4242 4242 4242');
      setCardExpiry('12/28');
      setCardCVV('123');
    }
  };

  const handleCardNumberChange = (value: string) => {
    const clean = value.replace(/\D/g, '').substring(0, 16);
    const formatted = clean.replace(/(\d{4})(?=\d)/g, '$1 ');
    setCardNumber(formatted);
  };

  const handleExpiryChange = (value: string) => {
    const clean = value.replace(/\D/g, '').substring(0, 4);
    if (clean.length > 2) {
      setCardExpiry(`${clean.substring(0, 2)}/${clean.substring(2)}`);
    } else {
      setCardExpiry(clean);
    }
  };

  // Load Addresses
  useEffect(() => {
    if (!user) {
      router.push('/login');
      return;
    }

    async function loadAddresses() {
      if (isDemoMode || (token && token.startsWith('mock-'))) {
        const savedAddrs = localStorage.getItem('demo_addresses');
        const data: Address[] = savedAddrs ? JSON.parse(savedAddrs) : [
          {
            id: 'demo-address-id-1',
            label: 'Home',
            addressLine1: '123 Nguyen Hue St',
            city: 'Ho Chi Minh City',
            postalCode: '700000',
            country: 'Vietnam',
            isDefault: true
          }
        ];
        setAddresses(data);
        setSelectedAddressId(data.find(a => a.isDefault)?.id || data[0]?.id || '');
        setLoading(false);
        return;
      }

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
  }, [user, token, router, isDemoMode]);

  // Select new address and toggle defaults
  const handleSelectAddress = async (addressId: string) => {
    if (isDemoMode || (token && token.startsWith('mock-'))) {
      const data = addresses.map(addr => ({ ...addr, isDefault: addr.id === addressId }));
      setAddresses(data);
      setSelectedAddressId(addressId);
      localStorage.setItem('demo_addresses', JSON.stringify(data));
      showToast("Delivery address updated (Demo Mode).", "success");
      return;
    }

    try {
      const target = addresses.find(a => a.id === addressId);
      if (!target) return;

      // Unset previous defaults and set target to default
      const promises = addresses.map(async (addr) => {
        if (addr.id === addressId && !addr.isDefault) {
          const res = await fetch(`/api/users/me/addresses/${addr.id}`, {
            method: 'PUT',
            headers: {
              'Content-Type': 'application/json',
              'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({
              label: addr.label,
              addressLine1: addr.addressLine1,
              addressLine2: addr.addressLine2 || '',
              city: addr.city,
              state: addr.state || addr.city,
              postalCode: addr.postalCode,
              country: addr.country,
              isDefault: true
            })
          });
          return res;
        } else if (addr.id !== addressId && addr.isDefault) {
          const res = await fetch(`/api/users/me/addresses/${addr.id}`, {
            method: 'PUT',
            headers: {
              'Content-Type': 'application/json',
              'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({
              label: addr.label,
              addressLine1: addr.addressLine1,
              addressLine2: addr.addressLine2 || '',
              city: addr.city,
              state: addr.state || addr.city,
              postalCode: addr.postalCode,
              country: addr.country,
              isDefault: false
            })
          });
          return res;
        }
        return null;
      });

      await Promise.all(promises.filter(Boolean));

      // Reload addresses
      const res = await fetch('/api/users/me/addresses', {
        headers: {
          'Authorization': `Bearer ${token}`
        }
      });
      if (res.ok) {
        const data: Address[] = await res.json();
        setAddresses(data);
        setSelectedAddressId(addressId);
        showToast("Delivery address updated.", "success");
      }
    } catch (err) {
      console.error("Failed to select address", err);
      showToast("Could not switch default address.", "error");
    }
  };

  // Add Address Form Handler
  const handleAddAddress = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!line1 || !city || !postalCode) return;

    if (isDemoMode || (token && token.startsWith('mock-'))) {
      const makeDefault = addresses.length === 0 || isNewAddressDefault;
      const newAddr: Address = {
        id: `demo-address-uuid-${Math.random().toString(36).substring(2, 11)}`,
        label,
        addressLine1: line1,
        city,
        postalCode,
        country: 'Vietnam',
        isDefault: makeDefault
      };
      const updatedList = [...addresses.map(a => makeDefault ? { ...a, isDefault: false } : a), newAddr];
      setAddresses(updatedList);
      setSelectedAddressId(newAddr.id);
      localStorage.setItem('demo_addresses', JSON.stringify(updatedList));
      setLine1('');
      setCity('');
      setPostalCode('');
      setIsNewAddressDefault(false);
      showToast("New address added (Demo Mode).", "success");
      return;
    }

    try {
      setAddingAddress(true);
      const makeDefault = addresses.length === 0 || isNewAddressDefault;

      // If we are setting this new address as default, unset other defaults first
      if (makeDefault && addresses.length > 0) {
        const resetPromises = addresses
          .filter(a => a.isDefault)
          .map(async (addr) => {
            await fetch(`/api/users/me/addresses/${addr.id}`, {
              method: 'PUT',
              headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
              },
              body: JSON.stringify({
                label: addr.label,
                addressLine1: addr.addressLine1,
                addressLine2: addr.addressLine2 || '',
                city: addr.city,
                state: addr.state || addr.city,
                postalCode: addr.postalCode,
                country: addr.country,
                isDefault: false
              })
            });
          });
        await Promise.all(resetPromises);
      }

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
          isDefault: makeDefault
        })
      });

      if (res.ok) {
        const newAddr: Address = await res.json();
        const updatedList = [...addresses.map(a => makeDefault ? { ...a, isDefault: false } : a), newAddr];
        setAddresses(updatedList);
        setSelectedAddressId(newAddr.id);
        setLine1('');
        setCity('');
        setPostalCode('');
        setIsNewAddressDefault(false);
        showToast("New address added successfully.", "success");
      } else {
        showToast("Failed to save address.", "error");
      }
    } catch (err) {
      console.error("Failed to add address", err);
    } finally {
      setAddingAddress(false);
    }
  };

  // Coupon Application
  const handleApplyCoupon = async () => {
    if (!couponInput || !cart) return;

    if (isDemoMode || (token && token.startsWith('mock-'))) {
      if (couponInput === 'FLASH20') {
        const discountAmount = Math.round(cart.subtotal * 0.2);
        setAppliedCoupon({
          couponId: 'demo-coupon-id',
          code: 'FLASH20',
          discountAmount,
          finalAmount: cart.subtotal - discountAmount
        });
        showToast(`Coupon "FLASH20" applied successfully (Demo Mode)!`, "success");
      } else {
        setCouponError("Invalid coupon code.");
        showToast("Invalid coupon code.", "error");
      }
      return;
    }

    try {
      setValidatingCoupon(true);
      setCouponError('');
      
      const res = await fetch('/api/coupons/validate', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({
          couponCode: couponInput,
          orderTotal: cart.subtotal
        })
      });

      if (res.ok) {
        const data: CouponDiscountResult = await res.json();
        setAppliedCoupon(data);
        showToast(`Coupon "${data.code}" applied successfully!`, "success");
      } else {
        const errorText = await res.text();
        let message = "Invalid coupon code.";
        try {
          const errorObj = JSON.parse(errorText);
          message = errorObj.message || message;
        } catch (e) {}
        setCouponError(message);
        showToast(message, "error");
      }
    } catch (err) {
      console.error("Failed to validate coupon", err);
      setCouponError("Coupon validation service offline.");
    } finally {
      setValidatingCoupon(false);
    }
  };

  // Place Order (Checkout Session + Checkout Execute)
  const handlePlaceOrder = async () => {
    if (!user || !selectedAddressId || !cart) {
      showToast("Please provide delivery and cart details.", "warning");
      return;
    }

    try {
      setCheckoutLoading(true);
      const finalAmount = appliedCoupon ? appliedCoupon.finalAmount : cart.subtotal;

      if (isDemoMode || (token && token.startsWith('mock-'))) {
        throw new Error("Demo Mode Local Simulation Bypass");
      }

      // 1. Create Checkout Session
      const sessionRes = await fetch('/api/orders/checkout/session', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({
          cartId: cart.cartId,
          totalAmount: finalAmount
        })
      });

      if (!sessionRes.ok) {
        const errorText = await sessionRes.text();
        let message = "Declined to create checkout session.";
        try {
          const errorObj = JSON.parse(errorText);
          message = errorObj.message || message;
        } catch (e) {}
        throw new Error(message);
      }

      const sessionData = await sessionRes.json();
      const idempotencyKey = sessionData.idempotencyKey; // UUID from backend

      // Guard check for idempotency session redirects early
      if (sessionData.status === 'SUCCESS' && sessionData.orderId) {
        showToast("Duplicate transaction detected. Order retrieved.", "warning");
        clearLocalCart();
        router.push(`/orders?success=true&orderId=${sessionData.orderId}`);
        return;
      }

      showToast("Session initialized. Completing payment details...", "info");

      // 2. Prepare payload for execution
      const isCOD = paymentMethod === 'cod';
      const execBody = {
        cardNumber: isCOD ? '4242 4242 4242 4242' : cardNumber.replace(/\s/g, ''),
        cvc: isCOD ? '123' : cardCVV,
        cardName: isCOD ? 'CASH ON DELIVERY' : cardName || 'JOHN DOE',
        expiry: isCOD ? '12/28' : cardExpiry,
        strategy: isCOD ? 'SUCCEED' : simulationStrategy
      };

      // 3. Execute Checkout
      const executeRes = await fetch('/api/orders/checkout/execute', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`,
          'Idempotency-Key': idempotencyKey
        },
        body: JSON.stringify(execBody)
      });

      const rawResult = await executeRes.text();
      let resultData;
      try {
        resultData = JSON.parse(rawResult);
      } catch (e) {
        resultData = { status: executeRes.ok ? 'SUCCESS' : 'FAILED', message: rawResult };
      }

      if (executeRes.ok && resultData.status === 'SUCCESS') {
        showToast("Payment processed & order placed successfully!", "success");
        clearLocalCart();
        router.push(`/orders?success=true&orderId=${resultData.orderId}`);
      } else {
        const failMessage = resultData.message || "Simulated gateway transaction failed.";
        showToast(`Checkout failed: ${failMessage}`, "error");
        clearLocalCart();
        router.push(`/orders?success=false&orderId=${resultData.orderId || ''}`);
      }

    } catch (err: any) {
      console.warn("Backend checkout offline, executing local order simulation fallback", err);
      
      // Simulate client-side checkout fallback
      const mockOrderId = `demo-order-uuid-${Math.random().toString(36).substr(2, 9)}`;
      const finalAmount = appliedCoupon ? appliedCoupon.finalAmount : cart.subtotal;
      
      const newOrder = {
        id: mockOrderId,
        userId: user.id,
        status: 'PENDING',
        totalAmount: finalAmount,
        notes: notes || "Order placed via Web Premium UI (Simulation)",
        items: cart.items.map(item => ({
          productId: item.productId,
          productName: item.productName,
          quantity: item.quantity,
          unitPrice: item.unitPrice
        })),
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString()
      };

      const savedOrdersStr = localStorage.getItem('demo_orders_list');
      const savedOrders = savedOrdersStr ? JSON.parse(savedOrdersStr) : [];
      savedOrders.unshift(newOrder);
      localStorage.setItem('demo_orders_list', JSON.stringify(savedOrders));

      const mockNotifs = [
        {
          id: `demo-notif-uuid-1`,
          orderId: mockOrderId,
          type: 'EMAIL',
          recipient: user.email,
          message: `Dear ${user.name}, your mock order ${mockOrderId.substring(0,8)} of ${formatPrice(finalAmount)} was placed successfully!`,
          createdAt: new Date().toISOString()
        }
      ];
      localStorage.setItem(`demo_notifs_${mockOrderId}`, JSON.stringify(mockNotifs));

      const mockHistory = [
        { fromStatus: null, toStatus: 'PENDING', reason: 'Order created', createdAt: new Date().toISOString() },
        { fromStatus: 'PENDING', toStatus: 'CONFIRMED', reason: 'Order confirmed after validation', createdAt: new Date(Date.now() + 1000).toISOString() }
      ];
      localStorage.setItem(`demo_history_${mockOrderId}`, JSON.stringify(mockHistory));

      setTimeout(() => {
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
      showToast("Simulation checkout complete.", "success");
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
      <div className="text-center py-24 glass-panel border border-white/5 rounded-2xl max-w-lg mx-auto">
        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-16 h-16 mx-auto text-gray-500 mb-4">
          <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 3h1.386c.51 0 .955.343 1.087.835l.383 1.437M7.5 14.25a3 3 0 0 0-3 3h15.75m-12.75-3h11.218c1.121-2.3 2.1-4.684 2.924-7.138a60.114 60.114 0 0 0-16.536-1.84M7.5 14.25 5.106 5.272M6 20.25a.75.75 0 1 1-1.5 0 .75.75 0 0 1 1.5 0Zm12.75 0a.75.75 0 1 1-1.5 0 .75.75 0 0 1 1.5 0Z" />
        </svg>
        <p className="text-gray-400">Checkout is vacant. Please populate items first.</p>
        <Link href="/" className="mt-4 inline-block bg-[#ee4d2d] hover:bg-[#d03d1e] text-white px-6 py-2.5 rounded-lg text-xs font-bold transition-all">
          Back to Products
        </Link>
      </div>
    );
  }

  const finalAmount = appliedCoupon ? appliedCoupon.finalAmount : cart.subtotal;
  const activeAddress = addresses.find(a => a.id === selectedAddressId);

  return (
    <div className="space-y-6 max-w-5xl mx-auto pb-12">
      {/* Header title */}
      <div className="flex items-center gap-3 border-b border-white/5 pb-4">
        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2.2} stroke="currentColor" className="w-8 h-8 text-[#ee4d2d]">
          <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 10.5V6a3.75 3.75 0 1 0-7.5 0v4.5m11.356-1.993 1.263 12c.07.665-.45 1.243-1.119 1.243H4.25a1.125 1.125 0 0 1-1.12-1.243l1.264-12A1.125 1.125 0 0 1 5.513 7.5h12.974c.576 0 1.059.435 1.119 1.007ZM8.625 10.5a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Zm7.5 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Z" />
        </svg>
        <h1 className="text-2xl font-black text-white font-mono uppercase tracking-wider">
          Checkout
        </h1>
      </div>

      {/* 1. SHIPPING ADDRESS SECTION (Top with stripes) */}
      <div className="relative border border-white/10 rounded-2xl p-6 glass-panel overflow-hidden">
        {/* Shopee-style border top stripe pattern */}
        <div 
          className="absolute top-0 left-0 w-full h-[4px]" 
          style={{
            backgroundImage: 'repeating-linear-gradient(45deg, #ee4d2d, #ee4d2d 15px, #4f46e5 15px, #4f46e5 30px, #06b6d4 30px, #06b6d4 45px)'
          }}
        />

        <div className="flex justify-between items-start mb-4">
          <div className="flex items-center gap-2 text-[#ee4d2d]">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="w-5 h-5">
              <path fillRule="evenodd" d="m11.54 22.351.07.04.028.016a.76.76 0 0 0 .723 0l.028-.015.071-.041a16.975 16.975 0 0 0 1.144-.742 19.58 19.58 0 0 0 2.683-2.282c1.944-1.99 3.963-4.98 3.963-8.827a8.25 8.25 0 0 0-16.5 0c0 3.846 2.02 6.837 3.963 8.827a19.58 19.58 0 0 0 2.682 2.282 16.975 16.975 0 0 0 1.145.742ZM12 13.5a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z" clipRule="evenodd" />
            </svg>
            <span className="text-sm font-bold uppercase tracking-wider font-mono">Delivery Address</span>
          </div>

          <button
            onClick={() => setShowAddressSelector(!showAddressSelector)}
            className="text-xs font-semibold text-indigo-400 hover:text-indigo-300 font-mono transition-colors flex items-center gap-1"
          >
            {showAddressSelector ? "Close Selection" : "Change Address"}
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2.5} stroke="currentColor" className={`w-3 h-3 transition-transform ${showAddressSelector ? 'rotate-180' : ''}`}>
              <path strokeLinecap="round" strokeLinejoin="round" d="m19.5 8.25-7.5 7.5-7.5-7.5" />
            </svg>
          </button>
        </div>

        {loading ? (
          <div className="w-full h-12 bg-white/5 animate-pulse rounded-lg" />
        ) : activeAddress ? (
          <div className="flex flex-col sm:flex-row sm:items-center gap-2 sm:gap-4 text-sm">
            <span className="font-bold text-white font-mono">{activeAddress.label}</span>
            <div className="h-4 w-px bg-white/10 hidden sm:block" />
            <span className="text-gray-300">
              {activeAddress.addressLine1}, {activeAddress.city}, {activeAddress.postalCode}, {activeAddress.country}
            </span>
            {activeAddress.isDefault && (
              <span className="inline-block px-1.5 py-0.5 rounded text-[9px] font-bold uppercase border border-[#ee4d2d]/30 text-[#ee4d2d] bg-[#ee4d2d]/10 tracking-widest mt-1 sm:mt-0 w-fit">
                Default
              </span>
            )}
          </div>
        ) : (
          <div className="text-sm text-gray-400">
            No saved addresses found. Please add a new delivery address below.
          </div>
        )}

        {/* Saved address list & address creation accordion */}
        {showAddressSelector && (
          <div className="mt-6 border-t border-white/5 pt-6 space-y-6">
            <div className="space-y-3 max-h-60 overflow-y-auto pr-2">
              {addresses.map((addr) => (
                <div
                  key={addr.id}
                  onClick={() => handleSelectAddress(addr.id)}
                  className={`flex items-start gap-4 p-4 rounded-xl border glass-panel cursor-pointer transition-all ${
                    selectedAddressId === addr.id
                      ? 'border-[#ee4d2d] bg-[#ee4d2d]/5 glow-orange'
                      : 'border-white/10 hover:border-white/20'
                  }`}
                >
                  <input
                    type="radio"
                    name="address-selector"
                    checked={selectedAddressId === addr.id}
                    onChange={() => handleSelectAddress(addr.id)}
                    className="mt-1 text-[#ee4d2d] focus:ring-0 cursor-pointer"
                  />
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-bold text-white font-mono">{addr.label}</span>
                      {addr.isDefault && (
                        <span className="text-[9px] px-1.5 py-0.5 rounded bg-[#ee4d2d]/10 border border-[#ee4d2d]/20 text-[#ee4d2d] font-semibold tracking-wider">
                          DEFAULT
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-gray-400">
                      {addr.addressLine1}, {addr.city}, {addr.postalCode}, {addr.country}
                    </p>
                  </div>
                </div>
              ))}
            </div>

            {/* Inline Add Address form */}
            <form onSubmit={handleAddAddress} className="rounded-xl border border-white/5 bg-white/5 p-5 space-y-4">
              <h4 className="text-xs font-bold font-mono text-gray-300 uppercase tracking-wider">
                + Add New Delivery Address
              </h4>
              <div className="grid grid-cols-2 gap-4">
                <div className="col-span-2 sm:col-span-1">
                  <label className="block text-[10px] font-mono font-semibold text-gray-500 uppercase mb-1">Label</label>
                  <select
                    value={label}
                    onChange={(e) => setLabel(e.target.value)}
                    className="w-full px-3 py-2 rounded-lg bg-black border border-white/10 text-white outline-none focus:border-[#ee4d2d]/50 text-xs font-mono"
                  >
                    <option value="Home">Home</option>
                    <option value="Office">Office</option>
                    <option value="Warehouse">Warehouse</option>
                  </select>
                </div>
                <div className="col-span-2">
                  <label className="block text-[10px] font-mono font-semibold text-gray-500 uppercase mb-1">Address Line 1</label>
                  <input
                    type="text"
                    required
                    value={line1}
                    onChange={(e) => setLine1(e.target.value)}
                    placeholder="e.g. 123 Nguyen Hue St"
                    className="w-full px-3 py-2 rounded-lg bg-black/40 border border-white/10 text-white placeholder-gray-600 outline-none focus:border-[#ee4d2d]/50 text-xs font-mono"
                  />
                </div>
                <div>
                  <label className="block text-[10px] font-mono font-semibold text-gray-500 uppercase mb-1">City</label>
                  <input
                    type="text"
                    required
                    value={city}
                    onChange={(e) => setCity(e.target.value)}
                    placeholder="e.g. Ho Chi Minh City"
                    className="w-full px-3 py-2 rounded-lg bg-black/40 border border-white/10 text-white placeholder-gray-600 outline-none focus:border-[#ee4d2d]/50 text-xs font-mono"
                  />
                </div>
                <div>
                  <label className="block text-[10px] font-mono font-semibold text-gray-500 uppercase mb-1">Postal Code</label>
                  <input
                    type="text"
                    required
                    value={postalCode}
                    onChange={(e) => setPostalCode(e.target.value)}
                    placeholder="e.g. 700000"
                    className="w-full px-3 py-2 rounded-lg bg-black/40 border border-white/10 text-white placeholder-gray-600 outline-none focus:border-[#ee4d2d]/50 text-xs font-mono"
                  />
                </div>
              </div>

              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="new-address-default"
                  checked={isNewAddressDefault}
                  onChange={(e) => setIsNewAddressDefault(e.target.checked)}
                  className="rounded text-[#ee4d2d] focus:ring-0 cursor-pointer bg-white/5 border-white/10"
                />
                <label htmlFor="new-address-default" className="text-xs text-gray-400 select-none cursor-pointer font-mono">
                  Set as my default shipping address
                </label>
              </div>

              <button
                type="submit"
                disabled={addingAddress}
                className="w-full py-2.5 rounded-lg border border-[#ee4d2d]/20 bg-[#ee4d2d]/5 hover:bg-[#ee4d2d]/10 text-xs font-bold text-[#ee4d2d] transition-all cursor-pointer font-mono"
              >
                {addingAddress ? 'Saving address...' : 'Save New Address'}
              </button>
            </form>
          </div>
        )}
      </div>

      {/* 2. PRODUCTS ORDERED SECTION (Middle) */}
      <div className="glass-panel border border-white/10 rounded-2xl overflow-hidden">
        {/* Table header */}
        <div className="grid grid-cols-12 gap-4 px-6 py-4 border-b border-white/5 bg-white/5 text-[10px] font-mono text-gray-500 uppercase font-semibold tracking-wider">
          <div className="col-span-6">Products Ordered</div>
          <div className="col-span-2 text-center">Unit Price</div>
          <div className="col-span-2 text-center">Quantity</div>
          <div className="col-span-2 text-right">Subtotal</div>
        </div>

        {/* Product Items */}
        <div className="divide-y divide-white/5">
          {cart.items.map((item) => (
            <div key={item.itemId} className="grid grid-cols-12 gap-4 px-6 py-5 items-center">
              <div className="col-span-6 flex items-center gap-4">
                <div className="w-12 h-12 rounded-lg bg-white/5 border border-white/5 flex items-center justify-center text-gray-400 font-mono text-xs flex-shrink-0">
                  <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-6 h-6">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M20.25 7.5l-.625 10.632a2.25 2.25 0 0 1-2.247 2.118H6.622a2.25 2.25 0 0 1-2.247-2.118L3.75 7.5M10 11.25h4M3.375 7.5h17.25c.621 0 1.125-.504 1.125-1.125v-1.5c0-.621-.504-1.125-1.125-1.125H3.375c-.621 0-1.125.504-1.125 1.125v1.5c0 .621.504 1.125 1.125 1.125z" />
                  </svg>
                </div>
                <div>
                  <h3 className="text-sm font-bold text-white line-clamp-1 leading-snug">{item.productName}</h3>
                  <span className="text-[10px] font-mono text-gray-500">ID: {item.productId.substring(0,8)}</span>
                </div>
              </div>
              <div className="col-span-2 text-center text-sm font-mono text-gray-300">
                {formatPrice(item.unitPrice)}
              </div>
              <div className="col-span-2 text-center text-sm font-mono text-gray-300">
                x{item.quantity}
              </div>
              <div className="col-span-2 text-right text-sm font-bold font-mono text-white">
                {formatPrice(item.lineTotal)}
              </div>
            </div>
          ))}
        </div>

        {/* Message and Shipping Option panels */}
        <div className="grid grid-cols-1 md:grid-cols-2 border-t border-white/5 divide-y md:divide-y-0 md:divide-x divide-white/5 bg-black/25">
          {/* Notes Message Input */}
          <div className="p-6 flex flex-col justify-center space-y-2">
            <div className="flex items-center gap-2 text-gray-400 text-xs font-mono">
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-4 h-4">
                <path strokeLinecap="round" strokeLinejoin="round" d="M7.5 8.25h9m-9 3h9m-9 3h9m-11.25-10.5h13.5c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125H3.375c-.621 0-1.125-.504-1.125-1.125V3.375c0-.621.504-1.125 1.125-1.125z" />
              </svg>
              <span>Message for Seller:</span>
            </div>
            <input
              type="text"
              placeholder="Leave a message or instructions for the seller..."
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              className="w-full px-3 py-2 bg-white/5 border border-white/10 rounded-lg text-xs placeholder-gray-600 outline-none text-white focus:border-[#ee4d2d]/40"
            />
          </div>

          {/* Shopee Shipping Mock Panel */}
          <div className="p-6 space-y-3">
            <div className="flex justify-between items-center text-xs font-mono">
              <span className="text-gray-400">Shipping Option:</span>
              <span className="font-bold text-green-400">FREE</span>
            </div>
            <div className="flex items-start gap-3 p-3 rounded-lg border border-[#ee4d2d]/10 bg-[#ee4d2d]/5">
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-5 h-5 text-[#ee4d2d] mt-0.5 flex-shrink-0">
                <path strokeLinecap="round" strokeLinejoin="round" d="M8.25 18.75a1.5 1.5 0 0 1-3 0m3 0a1.5 1.5 0 0 0-3 0m3 0h6m-9 0H3.375a1.125 1.125 0 0 1-1.125-1.125V14.25m17.25 4.5a1.5 1.5 0 0 1-3 0m3 0a1.5 1.5 0 0 0-3 0m3 0h1.125c.621 0 1.129-.504 1.09-1.124l-.847-13.56A1.125 1.125 0 0 0 19.338 3H16.25m-10.5 11.25h10.5a1.125 1.125 0 0 0 1.125-1.125V11.25M4 14.25h12.5M4 14.25v-3.375c0-.621.504-1.125 1.125-1.125H12m-.75-3.5h7.062" />
              </svg>
              <div className="space-y-1">
                <span className="text-xs font-bold text-white block">Standard Express</span>
                <span className="text-[10px] text-gray-400 block">
                  Guaranteed arrival within 2-4 business days.
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* 3. SHOP VOUCHER / COUPON SECTION */}
      <div className="glass-panel border border-white/10 rounded-2xl p-6 flex flex-col sm:flex-row gap-4 justify-between items-center">
        <div className="flex items-center gap-3 w-full sm:w-auto">
          <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.8} stroke="currentColor" className="w-6 h-6 text-[#ee4d2d]">
            <path strokeLinecap="round" strokeLinejoin="round" d="M9.568 3H5.25A2.25 2.25 0 0 0 3 5.25v4.318c0 .597.237 1.17.659 1.591l9.581 9.581a1.125 1.125 0 0 0 1.591 0l7.222-7.222a1.125 1.125 0 0 0 0-1.591L11.16 3.659A2.25 2.25 0 0 0 9.568 3Z" />
            <path strokeLinecap="round" strokeLinejoin="round" d="M6 6h.008v.008H6V6Z" />
          </svg>
          <div>
            <h3 className="text-sm font-bold text-white">Shop Vouchers</h3>
            <p className="text-[10px] font-mono text-gray-500 uppercase">Input coupon code to reduce prices</p>
          </div>
        </div>

        <div className="flex gap-2 w-full sm:w-80">
          <input
            type="text"
            placeholder="e.g. FLASH20"
            value={couponInput}
            onChange={(e) => setCouponInput(e.target.value.toUpperCase())}
            disabled={appliedCoupon !== null}
            className="flex-grow px-3 py-2 rounded-lg bg-black/40 border border-white/10 text-white placeholder-gray-600 outline-none focus:border-[#ee4d2d]/50 text-xs font-mono uppercase"
          />
          {appliedCoupon ? (
            <button
              onClick={() => {
                setAppliedCoupon(null);
                setCouponInput('');
              }}
              className="px-4 py-2 rounded-lg border border-red-500/20 bg-red-500/5 hover:bg-red-500/10 text-xs font-bold text-red-400 transition-all cursor-pointer font-mono"
            >
              Clear
            </button>
          ) : (
            <button
              onClick={handleApplyCoupon}
              disabled={validatingCoupon || !couponInput}
              className="px-4 py-2 rounded-lg border border-[#ee4d2d]/20 bg-[#ee4d2d]/5 hover:bg-[#ee4d2d]/10 text-xs font-bold text-[#ee4d2d] transition-all cursor-pointer font-mono disabled:opacity-50"
            >
              {validatingCoupon ? 'Checking...' : 'Apply'}
            </button>
          )}
        </div>

        {couponError && (
          <p className="text-[10px] text-red-400 font-mono w-full text-right sm:w-auto mt-1 sm:mt-0">{couponError}</p>
        )}
        {appliedCoupon && (
          <p className="text-[10px] text-green-400 font-mono w-full text-right sm:w-auto mt-1 sm:mt-0">
            Active Discount: -{formatPrice(appliedCoupon.discountAmount)} ({appliedCoupon.code})
          </p>
        )}
      </div>

      {/* 4. PAYMENT METHOD SELECTOR */}
      <div className="glass-panel border border-white/10 rounded-2xl p-6 space-y-6">
        <div className="flex items-center gap-2 text-white pb-3 border-b border-white/5">
          <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.8} stroke="currentColor" className="w-5 h-5 text-[#ee4d2d]">
            <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 8.25h19.5M2.25 9h19.5m-16.5 5.25h6m-6 2.25h3m-5.625-10.5h16.5a1.5 1.5 0 0 1 1.5 1.5v11.25a1.5 1.5 0 0 1-1.5 1.5H3.75a1.5 1.5 0 0 1-1.5-1.5V6a1.5 1.5 0 0 1 1.5-1.5z" />
          </svg>
          <span className="text-sm font-bold uppercase tracking-wider font-mono">Payment Method</span>
        </div>

        <div className="grid grid-cols-2 gap-4">
          {/* Card Method option */}
          <button
            onClick={() => setPaymentMethod('card')}
            className={`flex items-center gap-3 p-4 rounded-xl border text-left transition-all ${
              paymentMethod === 'card'
                ? 'border-[#ee4d2d] bg-[#ee4d2d]/5 text-white font-semibold'
                : 'border-white/10 hover:border-white/20 text-gray-400'
            }`}
          >
            <div className={`w-4 h-4 rounded-full border-2 flex items-center justify-center flex-shrink-0 ${paymentMethod === 'card' ? 'border-[#ee4d2d]' : 'border-gray-500'}`}>
              {paymentMethod === 'card' && <div className="w-2 h-2 rounded-full bg-[#ee4d2d]" />}
            </div>
            <div className="space-y-0.5">
              <span className="text-xs font-bold block">Credit / Debit Card</span>
              <span className="text-[10px] text-gray-500 block">Instant processing gate</span>
            </div>
          </button>

          {/* COD Option */}
          <button
            onClick={() => setPaymentMethod('cod')}
            className={`flex items-center gap-3 p-4 rounded-xl border text-left transition-all ${
              paymentMethod === 'cod'
                ? 'border-[#ee4d2d] bg-[#ee4d2d]/5 text-white font-semibold'
                : 'border-white/10 hover:border-white/20 text-gray-400'
            }`}
          >
            <div className={`w-4 h-4 rounded-full border-2 flex items-center justify-center flex-shrink-0 ${paymentMethod === 'cod' ? 'border-[#ee4d2d]' : 'border-gray-500'}`}>
              {paymentMethod === 'cod' && <div className="w-2 h-2 rounded-full bg-[#ee4d2d]" />}
            </div>
            <div className="space-y-0.5">
              <span className="text-xs font-bold block">Cash on Delivery (COD)</span>
              <span className="text-[10px] text-gray-500 block">Pay in cash on delivery</span>
            </div>
          </button>
        </div>

        {/* Card Details inline */}
        {paymentMethod === 'card' && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-8 pt-4 items-center">
            {/* Visual Credit Card */}
            <div className="space-y-4">
              <span className="block text-[10px] font-mono font-bold text-gray-500 uppercase tracking-widest">Card Preview</span>
              <div className="relative h-48 rounded-2xl bg-gradient-to-br from-indigo-600 via-purple-600 to-pink-500 p-6 flex flex-col justify-between shadow-2xl overflow-hidden group">
                <div className="absolute -top-10 -right-10 w-40 h-40 rounded-full bg-white/10 blur-2xl group-hover:scale-115 transition-transform pointer-events-none" />
                
                <div className="flex justify-between items-start">
                  <span className="text-[10px] font-mono font-bold tracking-widest text-white/80">PREMIUM CREDIT GATEWAY</span>
                  <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-7 h-7 text-white/80">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M8.25 3v1.5M4.5 8.25H3m18 0h-1.5M4.5 12H3m18 0h-1.5m-15 3.75H3m18 0h-1.5M8.25 19.5V21M12 3v1.5m0 15V21m3.75-18v1.5m0 15V21m-9-1.5h10.5a2.25 2.25 0 0 0 2.25-2.25V6.75a2.25 2.25 0 0 0-2.25-2.25H6.75A2.25 2.25 0 0 0 4.5 6.75v10.5a2.25 2.25 0 0 0 2.25 2.25Zm.75-12h9v9h-9v-9Z" />
                  </svg>
                </div>

                <div className="space-y-2">
                  <div className="text-lg font-mono tracking-widest text-white font-bold">
                    {cardNumber}
                  </div>
                  <div className="flex justify-between items-end">
                    <div>
                      <span className="block text-[7px] uppercase tracking-wider text-white/50">Cardholder</span>
                      <span className="text-[10px] font-mono font-bold uppercase tracking-wider text-white/90 truncate max-w-[150px]">{cardName || 'JOHN DOE'}</span>
                    </div>
                    <div className="flex gap-4">
                      <div>
                        <span className="block text-[7px] uppercase tracking-wider text-white/50">Expires</span>
                        <span className="text-[10px] font-mono font-bold text-white/90">{cardExpiry}</span>
                      </div>
                      <div>
                        <span className="block text-[7px] uppercase tracking-wider text-white/50">CVV</span>
                        <span className="text-[10px] font-mono font-bold text-white/90">{cardCVV}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {/* Credit Card inputs */}
            <div className="space-y-4">
              <div>
                <label className="block text-[9px] font-mono text-gray-500 uppercase tracking-widest mb-1.5">Simulation Strategy</label>
                <select
                  value={simulationStrategy}
                  onChange={(e) => handleStrategyChange(e.target.value)}
                  className="w-full px-3 py-2 bg-black/60 border border-white/10 rounded-lg text-white outline-none focus:border-[#ee4d2d] text-xs font-mono"
                >
                  <option value="SUCCEED">Succeed Transaction (4242 4242 4242 4242)</option>
                  <option value="DECLINED">Force Fail: Card Declined (4000 0000 0000 0002)</option>
                  <option value="INSUFFICIENT_FUNDS">Force Fail: Insufficient Funds (4000 0000 0000 3022)</option>
                  <option value="EXPIRED_CARD">Force Fail: Expired Card (4000 0000 0000 0115)</option>
                  <option value="INCORRECT_CVC">Force Fail: Incorrect CVV/CVC (4000 0000 0000 0123)</option>
                  <option value="TIMEOUT">Force Fail: Gateway Timeout (Sleeps 3s)</option>
                </select>
              </div>

              <div>
                <label className="block text-[9px] font-mono text-gray-500 uppercase tracking-widest mb-1.5">Card Number</label>
                <input
                  type="text"
                  required
                  placeholder="4242 4242 4242 4242"
                  value={cardNumber}
                  onChange={(e) => handleCardNumberChange(e.target.value)}
                  className="w-full px-3 py-2 bg-white/5 border border-white/10 rounded-lg text-white outline-none focus:border-[#ee4d2d] text-xs font-mono"
                />
              </div>

              <div>
                <label className="block text-[9px] font-mono text-gray-500 uppercase tracking-widest mb-1.5">Cardholder Name</label>
                <input
                  type="text"
                  required
                  placeholder="JOHN DOE"
                  value={cardName}
                  onChange={(e) => setCardName(e.target.value)}
                  className="w-full px-3 py-2 bg-white/5 border border-white/10 rounded-lg text-white outline-none focus:border-[#ee4d2d] text-xs font-mono uppercase"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-[9px] font-mono text-gray-500 uppercase tracking-widest mb-1.5">Expiry Date</label>
                  <input
                    type="text"
                    required
                    placeholder="MM/YY"
                    maxLength={5}
                    value={cardExpiry}
                    onChange={(e) => handleExpiryChange(e.target.value)}
                    className="w-full px-3 py-2 bg-white/5 border border-white/10 rounded-lg text-white outline-none focus:border-[#ee4d2d] text-xs text-center font-mono"
                  />
                </div>
                <div>
                  <label className="block text-[9px] font-mono text-gray-500 uppercase tracking-widest mb-1.5">CVV / CVC</label>
                  <input
                    type="password"
                    maxLength={4}
                    placeholder="123"
                    value={cardCVV}
                    onChange={(e) => setCardCVV(e.target.value.replace(/\D/g, ''))}
                    className="w-full px-3 py-2 bg-white/5 border border-white/10 rounded-lg text-white outline-none focus:border-[#ee4d2d] text-xs text-center font-mono"
                  />
                </div>
              </div>
            </div>
          </div>
        )}

        {/* COD Details inline */}
        {paymentMethod === 'cod' && (
          <div className="p-4 rounded-xl border border-dashed border-white/10 bg-white/5 text-sm space-y-1.5">
            <h4 className="font-bold text-white font-mono flex items-center gap-1.5">
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-5 h-5 text-green-400">
                <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              Cash On Delivery Enabled
            </h4>
            <p className="text-gray-400 text-xs leading-relaxed">
              No immediate card charging. You will pay the courier in cash upon receiving your items at your shipping address. Please ensure someone is present to receive and clear the payment.
            </p>
          </div>
        )}
      </div>

      {/* 5. BILLING SUMMARY & PLACE ORDER (Bottom Block) */}
      <div className="glass-panel border border-white/10 rounded-2xl p-6 flex flex-col md:flex-row md:items-center justify-between gap-6">
        <div className="space-y-2 text-sm">
          <div className="flex justify-between md:justify-start md:gap-8 text-gray-400">
            <span>Merchandise Subtotal:</span>
            <span className="font-mono text-white text-right md:text-left">{formatPrice(cart.subtotal)}</span>
          </div>
          {appliedCoupon && (
            <div className="flex justify-between md:justify-start md:gap-8 text-green-400 font-mono">
              <span>Coupon Discount ({appliedCoupon.code}):</span>
              <span className="text-right md:text-left">-{formatPrice(appliedCoupon.discountAmount)}</span>
            </div>
          )}
          <div className="flex justify-between md:justify-start md:gap-8 text-gray-400">
            <span>Shipping Subtotal:</span>
            <span className="text-right md:text-left font-mono">Free</span>
          </div>
          <div className="flex justify-between md:justify-start md:gap-8 text-base font-bold text-white border-t border-white/5 pt-2">
            <span>Total Payment:</span>
            <span className="font-mono text-xl text-[#ee4d2d] text-right md:text-left">{formatPrice(finalAmount)}</span>
          </div>
        </div>

        <div className="flex gap-4 items-center justify-end">
          <button
            onClick={handlePlaceOrder}
            disabled={checkoutLoading || !selectedAddressId || (paymentMethod === 'card' && !cardName)}
            className="w-full md:w-60 py-4 px-8 rounded-xl bg-[#ee4d2d] hover:bg-[#d03d1e] disabled:opacity-50 text-sm font-bold text-white shadow-lg tracking-wider uppercase transition-all flex items-center justify-center gap-2 cursor-pointer font-mono"
          >
            {checkoutLoading ? (
              <>
                <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                Processing...
              </>
            ) : (
              <>
                Place Order
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2.5} stroke="currentColor" className="w-4 h-4">
                  <path strokeLinecap="round" strokeLinejoin="round" d="m4.5 12.75 6 6 9-13.5" />
                </svg>
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}

