"use client";

import React, { Suspense, useEffect, useState, useCallback } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';

interface OrderItem {
  productId: string;
  quantity: number;
  unitPrice: number;
}

interface Order {
  id: string;
  userId: string;
  status: string;
  totalAmount: number;
  items: OrderItem[];
  createdAt: string;
  updatedAt: string;
}

interface HistoryLog {
  fromStatus: string | null;
  toStatus: string;
  reason: string;
  createdAt: string;
}

interface NotificationLog {
  id: string;
  orderId: string;
  type: string;
  recipient: string;
  message: string;
  createdAt: string;
}

function OrdersList() {
  const { user, token, isDemoMode } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();

  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null);
  const [orderHistory, setOrderHistory] = useState<HistoryLog[]>([]);
  const [notifs, setNotifs] = useState<NotificationLog[]>([]);
  const [detailsLoading, setDetailsLoading] = useState<boolean>(false);

  const showSuccessBanner = searchParams.get('success') === 'true';
  const recentOrderId = searchParams.get('orderId');

  const fetchOrders = useCallback(async () => {
    if (!user || !token) return;

    if (isDemoMode || token.startsWith('mock-')) {
      const savedOrdersStr = localStorage.getItem('demo_orders_list');
      if (savedOrdersStr) {
        setOrders(JSON.parse(savedOrdersStr));
      } else {
        setOrders([]);
      }
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      const res = await fetch(`/api/orders?userId=${user.id}`, {
        headers: {
          'Authorization': `Bearer ${token}`
        }
      });
      if (res.ok) {
        const data = await res.json();
        setOrders(data.content || []);
      } else {
        throw new Error("HTTP failure");
      }
    } catch (err) {
      console.warn("Orders API offline, loading demo mock orders list", err);
      const savedOrdersStr = localStorage.getItem('demo_orders_list');
      if (savedOrdersStr) {
        setOrders(JSON.parse(savedOrdersStr));
      } else {
        setOrders([]);
      }
    } finally {
      setLoading(false);
    }
  }, [user, token, isDemoMode]);

  useEffect(() => {
    if (!user) {
      router.push('/login');
      return;
    }
    fetchOrders();
  }, [user, router, fetchOrders]);

  const loadOrderDetails = async (orderId: string) => {
    if (selectedOrderId === orderId) {
      setSelectedOrderId(null);
      return;
    }

    if (isDemoMode || (token && token.startsWith('mock-'))) {
      setSelectedOrderId(orderId);
      const savedHistory = localStorage.getItem(`demo_history_${orderId}`);
      setOrderHistory(savedHistory ? JSON.parse(savedHistory) : []);
      const savedNotifs = localStorage.getItem(`demo_notifs_${orderId}`);
      setNotifs(savedNotifs ? JSON.parse(savedNotifs) : []);
      return;
    }

    try {
      setDetailsLoading(true);
      setSelectedOrderId(orderId);
      setOrderHistory([]);
      setNotifs([]);

      // 1. Fetch order state transitions history (Disabled)
      setOrderHistory([]);

      // 2. Fetch notification records triggered in notification-service
      try {
        const notifRes = await fetch(`/api/notifications/${orderId}`, {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        if (notifRes.ok) {
          const notifData = await notifRes.json();
          setNotifs(notifData);
        }
      } catch (e) {
        console.error("Could not fetch notifications", e);
      }

    } catch (err) {
      console.warn("Order Details API offline, switching to demo simulated trace database", err);
      // Fallback
      const savedHistory = localStorage.getItem(`demo_history_${orderId}`);
      if (savedHistory) {
        setOrderHistory(JSON.parse(savedHistory));
      }
      
      const savedNotifs = localStorage.getItem(`demo_notifs_${orderId}`);
      if (savedNotifs) {
        setNotifs(JSON.parse(savedNotifs));
      }
    } finally {
      setDetailsLoading(false);
    }
  };

  const handleCancelOrder = async (orderId: string) => {
    if (!confirm("Are you sure you want to request cancellation for this order?")) return;

    if (isDemoMode || (token && token.startsWith('mock-'))) {
      const savedOrdersStr = localStorage.getItem('demo_orders_list');
      if (savedOrdersStr) {
        const list = JSON.parse(savedOrdersStr);
        const idx = list.findIndex((o: any) => o.id === orderId);
        if (idx > -1) {
          list[idx].status = 'CANCELLED';
          localStorage.setItem('demo_orders_list', JSON.stringify(list));
          setOrders(list);
        }
      }
      return;
    }

    try {
      const res = await fetch(`/api/orders/${orderId}`, {
        method: 'DELETE',
        headers: { 'Authorization': `Bearer ${token}` }
      });
      if (res.ok) {
        await fetchOrders();
        // Refresh details if expanded
        if (selectedOrderId === orderId) {
          await loadOrderDetails(orderId);
        }
      }
    } catch (err) {
      console.error("Failed to cancel order", err);
    }
  };

  const formatPrice = (price: number) => {
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(price);
  };

  const formatDate = (dateStr: string) => {
    return new Date(dateStr).toLocaleString('vi-VN', {
      year: 'numeric', month: 'short', day: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
  };

  const getStatusStyle = (status: string) => {
    switch (status.toUpperCase()) {
      case 'PENDING':
        return 'border-yellow-500/20 bg-yellow-500/10 text-yellow-400';
      case 'CONFIRMED':
        return 'border-blue-500/20 bg-blue-500/10 text-blue-400';
      case 'SHIPPED':
      case 'PAID':
        return 'border-cyan-500/20 bg-cyan-500/10 text-cyan-400';
      case 'DELIVERED':
        return 'border-green-500/20 bg-green-500/10 text-green-400';
      case 'CANCELLED':
        return 'border-red-500/20 bg-red-500/10 text-red-400';
      default:
        return 'border-white/5 bg-white/5 text-gray-400';
    }
  };

  return (
    <div className="space-y-8">
      {/* Checkout Success Notification Banner */}
      {showSuccessBanner && (
        <div className="p-6 rounded-2xl border border-green-500/20 bg-green-500/5 shadow-lg shadow-green-500/5 text-center space-y-3 animate-fade-in relative">
          <div className="flex justify-center text-green-400">
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2.5} stroke="currentColor" className="w-8 h-8">
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75 11.25 15 15 9.75M21 12c0 1.268-.63 2.39-1.593 3.068a3.745 3.745 0 0 1-1.043 3.296 3.745 3.745 0 0 1-3.296 1.043A3.745 3.745 0 0 1 12 21c-1.268 0-2.39-.63-3.068-1.593a3.746 3.746 0 0 1-3.296-1.043 3.745 3.745 0 0 1-1.043-3.296A3.745 3.745 0 0 1 3 12c0-1.268.63-2.39 1.593-3.068a3.745 3.745 0 0 1 1.043-3.296 3.746 3.746 0 0 1 3.296-1.043A3.746 3.746 0 0 1 12 3c1.268 0 2.39.63 3.068 1.593a3.746 3.746 0 0 1 3.296 1.043 3.746 3.746 0 0 1 1.043 3.296A3.745 3.745 0 0 1 21 12Z" />
            </svg>
          </div>
          <div>
            <h2 className="text-base font-bold text-white">Simulation Order Placed Successfully!</h2>
            <p className="text-xs text-gray-400 mt-1">
              Your transaction has processed. Monitor service-level status tracking below.
            </p>
          </div>
          {recentOrderId && (
            <button
              onClick={() => loadOrderDetails(recentOrderId)}
              className="text-xs font-mono font-bold text-indigo-400 hover:text-indigo-300 transition-colors underline underline-offset-4 cursor-pointer"
            >
              Verify trace event logs for {recentOrderId.substring(0, 8)}...
            </button>
          )}
        </div>
      )}

      {/* Title */}
      <div className="border-b border-white/5 pb-4 flex items-center justify-between">
        <h1 className="text-2xl font-black text-white tracking-wide uppercase font-mono">
          Your Purchases
        </h1>
        <span className="text-sm text-gray-400 font-mono">
          {orders.length} transaction {orders.length === 1 ? 'record' : 'records'}
        </span>
      </div>

      {loading ? (
        <div className="space-y-4 py-8">
          {[1, 2].map(n => (
            <div key={n} className="h-28 rounded-2xl bg-white/5 border border-white/5 animate-pulse" />
          ))}
        </div>
      ) : orders.length === 0 ? (
        <div className="text-center py-20 rounded-2xl glass-panel border border-white/5 space-y-4">
          <p className="text-gray-400">You have no order records.</p>
          <Link href="/" className="inline-flex py-2.5 px-6 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-xs font-bold text-white shadow-lg cursor-pointer">
            Explore Catalog
          </Link>
        </div>
      ) : (
        <div className="space-y-6">
          {orders.map((order) => {
            const isExpanded = selectedOrderId === order.id;

            return (
              <div
                key={order.id}
                className={`rounded-2xl border transition-all duration-300 glass-panel overflow-hidden ${
                  isExpanded ? 'border-indigo-500/40 ring-1 ring-indigo-500/10' : 'border-white/10'
                }`}
              >
                {/* Summary Row */}
                <div
                  onClick={() => loadOrderDetails(order.id)}
                  className="p-6 flex flex-wrap items-center justify-between gap-6 cursor-pointer hover:bg-white/[0.02] transition-colors"
                >
                  <div className="space-y-1.5">
                    <div className="flex items-center gap-3">
                      <span className="text-sm font-bold font-mono text-white">
                        ID: {order.id.substring(0, 8)}...
                      </span>
                      <span className={`px-2.5 py-0.5 rounded-full border text-[10px] font-bold font-mono uppercase tracking-wider ${getStatusStyle(order.status)}`}>
                        {order.status}
                      </span>
                    </div>
                    <div className="text-xs text-gray-500 font-mono">
                      Timestamp: {formatDate(order.createdAt)}
                    </div>
                  </div>

                  <div className="flex items-center gap-6">
                    <div className="text-right">
                      <span className="block text-[10px] text-gray-500 uppercase tracking-widest font-mono">Grand Total</span>
                      <span className="text-lg font-black text-white font-mono">{formatPrice(order.totalAmount)}</span>
                    </div>
                    
                    {/* Expand Chevron */}
                    <div className={`p-1.5 rounded-lg border border-white/5 text-gray-400 transition-transform duration-300 ${isExpanded ? 'rotate-180 text-white' : ''}`}>
                      <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2.5} stroke="currentColor" className="w-4 h-4">
                        <path strokeLinecap="round" strokeLinejoin="round" d="m19.5 8.25-7.5 7.5-7.5-7.5" />
                      </svg>
                    </div>
                  </div>
                </div>

                {/* Expanded Details Section */}
                {isExpanded && (
                  <div className="border-t border-white/5 bg-black/20 p-6 space-y-8 animate-slide-down">
                    <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
                      {/* Left: Timeline logs */}
                      <div className="space-y-4">
                        <h3 className="text-xs font-bold text-gray-400 font-mono uppercase tracking-widest border-b border-white/5 pb-2">
                          Service Status Timeline (event logs)
                        </h3>

                        {detailsLoading ? (
                          <div className="space-y-3">
                            <div className="h-8 bg-white/5 animate-pulse rounded" />
                            <div className="h-8 bg-white/5 animate-pulse rounded" />
                          </div>
                        ) : orderHistory.length === 0 ? (
                          <p className="text-xs text-gray-500 font-mono">No state transitions found.</p>
                        ) : (
                          <div className="relative pl-6 space-y-4 border-l border-white/10 ml-2">
                            {orderHistory.map((hist, index) => (
                              <div key={index} className="relative">
                                {/* Dot Indicator */}
                                <div className="absolute -left-[30px] top-1.5 w-2 h-2 rounded-full border border-indigo-500 bg-[#030712]" />
                                
                                <div className="space-y-1">
                                  <div className="flex items-center gap-2 text-xs font-semibold text-white">
                                    {hist.fromStatus && (
                                      <>
                                        <span className="text-gray-500 line-through">{hist.fromStatus}</span>
                                        <span className="text-gray-600">→</span>
                                      </>
                                    )}
                                    <span className="text-indigo-400 font-mono">{hist.toStatus}</span>
                                    <span className="text-[10px] text-gray-500 font-mono">({formatDate(hist.createdAt)})</span>
                                  </div>
                                  <p className="text-[11px] text-gray-400 italic">
                                    Reason: {hist.reason}
                                  </p>
                                </div>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>

                      {/* Right: Kafka Notification Logs */}
                      <div className="space-y-4">
                        <h3 className="text-xs font-bold text-gray-400 font-mono uppercase tracking-widest border-b border-white/5 pb-2">
                          Kafka Simulated Notifications (SMS/Email)
                        </h3>

                        {detailsLoading ? (
                          <div className="space-y-3">
                            <div className="h-8 bg-white/5 animate-pulse rounded" />
                          </div>
                        ) : notifs.length === 0 ? (
                          <div className="text-xs text-gray-500 font-mono italic p-3 rounded-lg border border-white/5 bg-white/5">
                            Processing event queue... Wait up to 5s for Kafka consumers to sync.
                          </div>
                        ) : (
                          <div className="space-y-3">
                            {notifs.map((notif) => (
                              <div key={notif.id} className="p-3 rounded-xl border border-purple-500/10 bg-purple-500/5 space-y-1 text-xs">
                                <div className="flex justify-between items-center text-[10px] font-mono text-purple-400 font-semibold">
                                  <span>{notif.type} ── To: {notif.recipient}</span>
                                  <span>{formatDate(notif.createdAt)}</span>
                                </div>
                                <p className="text-gray-300 text-xs italic leading-relaxed">
                                  &quot;{notif.message}&quot;
                                </p>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Order Cancellation option */}
                    {order.status !== 'CANCELLED' && order.status !== 'DELIVERED' && (
                      <div className="border-t border-white/5 pt-4 flex justify-end">
                        <button
                          onClick={() => handleCancelOrder(order.id)}
                          className="px-4 py-2 rounded-xl border border-red-500/20 bg-red-500/5 hover:bg-red-500/10 text-xs font-semibold text-red-400 transition-all cursor-pointer"
                        >
                          Request Cancellation
                        </button>
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

export default function OrdersPage() {
  return (
    <Suspense fallback={
      <div className="flex h-[60vh] items-center justify-center">
        <div className="w-10 h-10 border-4 border-indigo-500 border-t-transparent rounded-full animate-spin" />
      </div>
    }>
      <OrdersList />
    </Suspense>
  );
}
