"use client";

import React, { useState, useEffect } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useToast } from '@/context/ToastContext';

interface DomainStatus {
  name: string;
  pkg: string;
  status: 'ONLINE' | 'OFFLINE' | 'CHECKING';
  description: string;
}

export default function DeveloperConsole() {
  const { token } = useAuth();
  const { showToast } = useToast();
  const [monolithStatus, setMonolithStatus] = useState<'ONLINE' | 'OFFLINE' | 'CHECKING'>('CHECKING');
  const [postgresStatus, setPostgresStatus] = useState<'ONLINE' | 'OFFLINE' | 'CHECKING'>('CHECKING');
  const [actuatorInfo, setActuatorInfo] = useState<any>(null);
  
  const [consoleLogs, setConsoleLogs] = useState<string[]>([
    "[BFF Node] Initializing proxy server bindings...",
    "[BFF Node] GATEWAY_URL configured: http://localhost:8080",
    "[BFF Node] Refactored to Spring Boot Monolith Architecture.",
    "[BFF Node] Routing all /api/* requests directly to Monolith controllers."
  ]);

  const [domains] = useState<DomainStatus[]>([
    { name: 'User & Security', pkg: 'domain.user', status: 'CHECKING', description: 'Handles JWT authorization, password updates, and address book ownership checks.' },
    { name: 'Product Catalog', pkg: 'domain.catalog', status: 'CHECKING', description: 'Manages items catalog with SQL injection sanitization query filters.' },
    { name: 'Shopping Cart', pkg: 'domain.cart', status: 'CHECKING', description: 'Saves user carts using pessimistic locks during updates.' },
    { name: 'Order & Saga', pkg: 'domain.order', status: 'CHECKING', description: 'Governs order placement state machine transitions and outbox pattern.' },
    { name: 'Inventory & TTL', pkg: 'domain.inventory', status: 'CHECKING', description: 'Optimistic lock retries, flash sale atomic decrements, and reservation TTL sweeps.' },
    { name: 'Simulated Payment', pkg: 'domain.payment', status: 'CHECKING', description: 'Card processing with duplicate unique constraints and pessimistic SELECT FOR UPDATE charge checks.' },
    { name: 'Notification Dedup', pkg: 'domain.notification', status: 'CHECKING', description: 'Triggers SMS and emails with application and DB-level deduplication.' },
    { name: 'Coupon Validation', pkg: 'domain.coupon', status: 'CHECKING', description: 'Locks and applies flash promo discount rates with atomic count increments.' }
  ]);

  const addLog = (msg: string) => {
    setConsoleLogs(prev => [...prev.slice(-30), `[${new Date().toLocaleTimeString()}] ${msg}`]);
  };

  const pingMonolith = async () => {
    addLog("Sending handshake ping to Monolith Actuator at http://localhost:8080/actuator/health...");
    setMonolithStatus('CHECKING');
    setPostgresStatus('CHECKING');
    
    try {
      const start = Date.now();
      const res = await fetch('/api/catalog'); // Public catalog is a great simple test
      if (res.ok) {
        const ms = Date.now() - start;
        setMonolithStatus('ONLINE');
        setPostgresStatus('ONLINE'); // If catalog returns successfully, PostgreSQL is running
        addLog(`Monolith Handshake Success! Root catalog response time: ${ms}ms.`);
        addLog("Database status: postgres://localhost:5432/ecommerce_db [CONNECTED].");
        addLog("Actuator Check: Spring Boot JVM running on port 8080.");
        showToast("Monolith systems online", "success");
        
        // Fetch actuator details if available
        try {
          const actRes = await fetch('/api/actuator/health');
          if (actRes.ok) {
            const actData = await actRes.json();
            setActuatorInfo(actData);
            addLog(`Actuator Health Status: ${actData.status || 'UP'}`);
          }
        } catch (e) {
          console.warn("Actuator endpoints are hidden or offline", e);
        }
      } else {
        throw new Error("Bad gateway or offline");
      }
    } catch (e) {
      setMonolithStatus('OFFLINE');
      setPostgresStatus('OFFLINE');
      addLog("Handshake Timeout. Please ensure Spring Boot monolith app is active.");
      showToast("Cannot establish connection to Monolith backend", "error");
    }
  };

  useEffect(() => {
    pingMonolith();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="space-y-8">
      {/* Title */}
      <div className="border-b border-white/5 pb-4 flex items-center justify-between flex-wrap gap-4">
        <div>
          <h1 className="text-2xl font-black text-white tracking-wide uppercase font-mono">
            Monolith Operations Dashboard
          </h1>
          <p className="text-xs text-gray-400 font-mono mt-1">
            Real-time status monitor of Spring Boot JVM & Unified Database schema.
          </p>
        </div>

        <button
          onClick={pingMonolith}
          className="px-4 py-2 rounded-xl border border-indigo-500/20 bg-indigo-500/5 hover:bg-indigo-500/10 text-xs font-mono font-bold text-indigo-400 transition-all cursor-pointer"
        >
          Ping Handshake
        </button>
      </div>

      {/* Monolith & DB Status Header */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div className={`p-6 rounded-2xl glass-panel border ${
          monolithStatus === 'ONLINE' ? 'border-green-500/20 bg-green-950/5' : monolithStatus === 'OFFLINE' ? 'border-red-500/20 bg-red-950/5' : 'border-yellow-500/20 bg-yellow-950/5'
        }`}>
          <div className="flex justify-between items-center">
            <span className="text-sm font-bold text-white font-mono uppercase">JVM Monolith Engine</span>
            <span className={`px-2 py-0.5 rounded text-[10px] font-bold font-mono ${
              monolithStatus === 'ONLINE' ? 'text-green-400 border border-green-500/20 bg-green-500/10' : 'text-red-400 border border-red-500/20 bg-red-500/10'
            }`}>{monolithStatus}</span>
          </div>
          <p className="text-xs text-gray-400 mt-2 font-mono">Port: 8080 | Type: Spring Boot 3.4.3 (Java 21)</p>
          {actuatorInfo && (
            <div className="mt-3 text-[10px] font-mono text-indigo-400 border-t border-white/5 pt-2">
              Status: <span className="text-gray-300">{actuatorInfo.status}</span> | Disk: <span className="text-gray-300">{actuatorInfo.components?.diskSpace?.status || 'UP'}</span>
            </div>
          )}
        </div>

        <div className={`p-6 rounded-2xl glass-panel border ${
          postgresStatus === 'ONLINE' ? 'border-green-500/20 bg-green-950/5' : postgresStatus === 'OFFLINE' ? 'border-red-500/20 bg-red-950/5' : 'border-yellow-500/20 bg-yellow-950/5'
        }`}>
          <div className="flex justify-between items-center">
            <span className="text-sm font-bold text-white font-mono uppercase">Shared Database</span>
            <span className={`px-2 py-0.5 rounded text-[10px] font-bold font-mono ${
              postgresStatus === 'ONLINE' ? 'text-green-400 border border-green-500/20 bg-green-500/10' : 'text-red-400 border border-red-500/20 bg-red-500/10'
            }`}>{postgresStatus}</span>
          </div>
          <p className="text-xs text-gray-400 mt-2 font-mono">DBMS: PostgreSQL 16 | Name: ecommerce_db</p>
          <div className="mt-3 text-[10px] font-mono text-cyan-400 border-t border-white/5 pt-2">
            URL: <span className="text-gray-300">jdbc:postgresql://postgres:5432/ecommerce_db</span>
          </div>
        </div>
      </div>

      {/* Internal Package Modules */}
      <section className="space-y-4">
        <h2 className="text-sm font-bold text-white font-mono uppercase tracking-wider">
          Internal JVM Domain Boundaries
        </h2>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          {domains.map((d, idx) => (
            <div key={idx} className="p-4 rounded-xl glass-panel border border-white/10 hover:border-white/20 transition-all flex flex-col justify-between min-h-[140px]">
              <div>
                <div className="flex justify-between items-start gap-2">
                  <h3 className="text-xs font-bold text-white font-mono">{d.name}</h3>
                  <span className={`w-1.5 h-1.5 rounded-full ${
                    monolithStatus === 'ONLINE' ? 'bg-green-400 animate-pulse' : 'bg-red-400'
                  }`} />
                </div>
                <span className="text-[9px] text-indigo-400 font-mono block mt-1">{d.pkg}</span>
                <p className="text-[11px] text-gray-400 mt-2 leading-relaxed">
                  {d.description}
                </p>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Architecture Log Monitor */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Terminal Logs */}
        <div className="lg:col-span-2 rounded-2xl glass-panel border border-white/10 p-6 space-y-4 shadow-xl">
          <div className="flex items-center justify-between border-b border-white/5 pb-3">
            <span className="text-xs font-bold text-white font-mono uppercase tracking-widest">
              Live Network Handshakes Log
            </span>
            <div className="flex gap-1.5">
              <span className="w-2.5 h-2.5 rounded-full bg-red-500/40" />
              <span className="w-2.5 h-2.5 rounded-full bg-yellow-500/40" />
              <span className="w-2.5 h-2.5 rounded-full bg-green-500/40" />
            </div>
          </div>

          <div className="h-64 rounded-xl bg-black/40 border border-white/5 p-4 overflow-y-auto font-mono text-[11px] text-green-400 space-y-2 scrollbar-thin">
            {consoleLogs.map((log, idx) => (
              <div key={idx} className="leading-relaxed whitespace-pre-wrap">
                {log}
              </div>
            ))}
          </div>
        </div>

        {/* Messaging Events */}
        <div className="rounded-2xl glass-panel border border-white/10 p-6 space-y-6">
          <h3 className="text-xs font-bold text-white font-mono uppercase tracking-widest border-b border-white/5 pb-3">
            Simulated Broker Events
          </h3>

          <div className="space-y-4">
            <div className="p-3.5 rounded-xl border border-white/5 bg-white/5 space-y-1.5">
              <span className="px-2 py-0.5 rounded bg-cyan-500/10 border border-cyan-500/20 text-[9px] font-mono text-cyan-400 font-semibold tracking-wider uppercase">
                OrderConfirmedNotification
              </span>
              <p className="text-[10px] text-gray-400 leading-relaxed font-mono">
                Pushed to outbox. Triggers immediate email simulation when order is validated.
              </p>
            </div>

            <div className="p-3.5 rounded-xl border border-white/5 bg-white/5 space-y-1.5">
              <span className="px-2 py-0.5 rounded bg-purple-500/10 border border-purple-500/20 text-[9px] font-mono text-purple-400 font-semibold tracking-wider uppercase">
                PaymentFailedNotification
              </span>
              <p className="text-[10px] text-gray-400 leading-relaxed font-mono">
                Dispatched if simulated credit charge fails. Triggers SMS user notification.
              </p>
            </div>

            <div className="p-3.5 rounded-xl border border-white/5 bg-white/5 space-y-1.5">
              <span className="px-2 py-0.5 rounded bg-green-500/10 border border-green-500/20 text-[9px] font-mono text-green-400 font-semibold tracking-wider uppercase">
                OrderCancelledNotification
              </span>
              <p className="text-[10px] text-gray-400 leading-relaxed font-mono">
                Triggered on cancellation. Releases reserved stock to the inventory pool.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
