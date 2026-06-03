import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";
import { AuthProvider } from "@/context/AuthContext";
import { CartProvider } from "@/context/CartContext";
import { ToastProvider } from "@/context/ToastContext";
import Link from "next/link";
import NavigationWrapper from "./components/NavigationWrapper";

const inter = Inter({
  subsets: ["latin"],
  variable: "--font-sans",
});

export const metadata: Metadata = {
  title: "AETHER | Premium Tech Marketplace",
  description: "Experience modern monolith-driven e-commerce with glassmorphic aesthetics.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className={`${inter.variable} h-full`}>
      <body className="min-h-full flex flex-col relative bg-[#030712] overflow-x-hidden">
        {/* Futuristic Ambient Glowing Orbs */}
        <div className="absolute top-[-10%] left-[-10%] w-[50vw] h-[50vw] rounded-full bg-indigo-500/10 blur-[120px] pointer-events-none z-0" />
        <div className="absolute bottom-[20%] right-[-10%] w-[60vw] h-[60vw] rounded-full bg-cyan-500/10 blur-[150px] pointer-events-none z-0" />
        <div className="absolute top-[40%] left-[30%] w-[40vw] h-[40vw] rounded-full bg-purple-500/5 blur-[100px] pointer-events-none z-0" />

        <AuthProvider>
          <ToastProvider>
            <CartProvider>
              <NavigationWrapper />
              <main className="flex-grow z-10 w-full max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
                {children}
              </main>
              {/* Minimalist Premium Footer */}
              <footer className="z-10 border-t border-white/5 py-8 text-center text-sm text-gray-500 glass-panel">
                <p>© {new Date().getFullYear()} AETHER Store. Powered by Spring Boot Monolith & Next.js.</p>
              </footer>
            </CartProvider>
          </ToastProvider>
        </AuthProvider>
      </body>
    </html>
  );
}
