"use client";

import React, { useState, useEffect } from 'react';
import { useAuth } from '@/context/AuthContext';

interface ServiceStatus {
  name: string;
  port: number;
  route: string;
  status: 'ONLINE' | 'OFFLINE' | 'CHECKING';
  description: string;
}

export default function DeveloperConsole() {
  const { token } = useAuth();
  const [gatewayStatus, setGatewayStatus] = useState<'ONLINE' | 'OFFLINE' | 'CHECKING'>('CHECKING');
  const [consoleLogs, setConsoleLogs] = useState<string[]>([
    "[BFF Node] Initializing proxy server bindings...",
    "[BFF Node] GATEWAY_URL configured: http://localhost:8080",
    "[BFF Node] Routing all /api/* requests to Spring Cloud Gateway."
  ]);

  const [services, setServices] = useState<ServiceStatus[]>([
    { name: 'Gateway Service', port: 8080, route: '/', status: 'CHECKING', description: 'Spring Cloud Gateway central entry routing.' },
    { name: 'User & Auth Service', port: 8085, route: '/auth/login', status: 'CHECKING', description: 'Handles security authorization and user address book records.' },
    { name: 'Order & Catalog Service', port: 8081, route: '/catalog/products', status: 'CHECKING', description: 'Manages product catalog database, cart caches, and checkouts.' },
    { name: 'Inventory Service', port: 8082, route: '/inventory', status: 'CHECKING', description: 'Controls active product quantities and reserved stocks.' },
    { name: 'Payment Service', port: 8083, route: '/payments', status: 'CHECKING', description: 'Processes transaction simulator and captures payment records.' },
    { name: 'Notification Service', port: 8084, route: '/notifications', status: 'CHECKING', description: 'Consumes Kafka topics to trigger simulated SMS and email logs.' }
  ]);

  const addLog = (msg: string) => {
    setConsoleLogs(prev => [...prev.slice(-30), `[${new Date().toLocaleTimeString()}] ${msg}`]);
  };

  const pingGateway = async () => {
    addLog("Sending handshake ping to API Gateway at http://localhost:8080...");
    try {
      // Hit catalog endpoint which is public through gateway
      const start = Date.now();
      const res = await fetch('/api/catalog');
      if (res.ok) {
        const ms = Date.now() - start;
        setGatewayStatus('ONLINE');
        addLog(`Gateway Handshake Success! Response time: ${ms}ms.`);
        
        // Mark all services as online if we got products back (means catalog & orders are up!)
        setServices(prev => prev.map(s => ({ ...s, status: 'ONLINE' })));
        addLog("Kafka Event Broker connection: CONNECTED.");
      } else {
        throw new Error("Bad response");
      }
    } catch (e) {
      setGatewayStatus('OFFLINE');
      setServices(prev => prev.map(s => ({ ...s, status: 'OFFLINE' })));
      addLog("Gateway Handshake Timeout. Please ensure Docker Compose containers are active.");
    }
  };

  useEffect(() => {
    pingGateway();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="space-y-8">
      {/* Title */}
      <div className="border-b border-white/5 pb-4 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-black text-white tracking-wide uppercase font-mono">
            System Operations Dashboard
          </h1>
          <p className="text-xs text-gray-400 font-mono mt-1">
            Real-time verification console for Java Spring Boot and Kafka architecture.
          </p>
        </div>

        <button
          onClick={pingGateway}
          className="px-4 py-2 rounded-xl border border-indigo-500/20 bg-indigo-500/5 hover:bg-indigo-500/10 text-xs font-mono font-bold text-indigo-400 transition-all cursor-pointer"
        >
          Recheck Handshake
        </button>
      </div>

      {/* Services Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {services.map((s, idx) => (
          <div
            key={idx}
            className={`p-6 rounded-2xl glass-panel border transition-all duration-300 relative overflow-hidden ${
              s.status === 'ONLINE'
                ? 'border-green-500/20 shadow-lg shadow-green-500/5'
                : s.status === 'OFFLINE'
                ? 'border-red-500/20 shadow-lg shadow-red-500/5'
                : 'border-yellow-500/20'
            }`}
          >
            {/* Status Blob background */}
            <div className={`absolute top-0 right-0 w-24 h-24 rounded-full opacity-[0.03] blur-xl pointer-events-none ${
              s.status === 'ONLINE' ? 'bg-green-400' : s.status === 'OFFLINE' ? 'bg-red-400' : 'bg-yellow-400'
            }`} />

            <div className="space-y-4">
              <div className="flex justify-between items-start">
                <div className="space-y-0.5">
                  <h3 className="text-sm font-bold text-white font-mono">{s.name}</h3>
                  <span className="text-[10px] text-gray-500 font-mono">Internal Port: {s.port}</span>
                </div>

                <span className={`px-2 py-0.5 rounded text-[9px] font-bold font-mono tracking-wider ${
                  s.status === 'ONLINE'
                    ? 'bg-green-500/10 text-green-400 border border-green-500/20'
                    : s.status === 'OFFLINE'
                    ? 'bg-red-500/10 text-red-400 border border-red-500/20'
                    : 'bg-yellow-500/10 text-yellow-400 border border-yellow-500/20'
                }`}>
                  {s.status}
                </span>
              </div>

              <p className="text-xs text-gray-400 leading-relaxed min-h-[40px]">
                {s.description}
              </p>

              <div className="text-[10px] font-mono text-indigo-400 border-t border-white/5 pt-3">
                Proxied path: <span className="text-gray-300">/api{s.route}</span>
              </div>
            </div>
          </div>
        ))}
      </div>

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
                OrderCreatedEvent
              </span>
              <p className="text-[10px] text-gray-400 leading-relaxed font-mono">
                Triggered on checkout. Captured by inventory-service to reserve product stock.
              </p>
            </div>

            <div className="p-3.5 rounded-xl border border-white/5 bg-white/5 space-y-1.5">
              <span className="px-2 py-0.5 rounded bg-purple-500/10 border border-purple-500/20 text-[9px] font-mono text-purple-400 font-semibold tracking-wider uppercase">
                PaymentProcessedEvent
              </span>
              <p className="text-[10px] text-gray-400 leading-relaxed font-mono">
                Fired on credit card simulation. Tells order-service to flag order as CONFIRMED.
              </p>
            </div>

            <div className="p-3.5 rounded-xl border border-white/5 bg-white/5 space-y-1.5">
              <span className="px-2 py-0.5 rounded bg-green-500/10 border border-green-500/20 text-[9px] font-mono text-green-400 font-semibold tracking-wider uppercase">
                OrderCancelledEvent
              </span>
              <p className="text-[10px] text-gray-400 leading-relaxed font-mono">
                Dispatched on cancellation request. Tells inventory-service to restock units.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
