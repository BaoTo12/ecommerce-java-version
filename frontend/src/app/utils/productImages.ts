export const PRODUCT_IMAGES: Record<string, string> = {
  // Original / Demo SKUs
  'PHONE-IP15': 'https://images.unsplash.com/photo-1695048133142-1a20484d2569?q=80&w=800&auto=format&fit=crop', // iPhone 15 Pro Max
  'PHONE-SS24': 'https://images.unsplash.com/photo-1610945265064-0e34e5519bbf?q=80&w=800&auto=format&fit=crop', // Samsung Galaxy S24 Ultra
  'LAPTOP-MBP': 'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?q=80&w=800&auto=format&fit=crop', // MacBook Pro
  'HEADPHONE-APM': 'https://images.unsplash.com/photo-1613040809024-b4ef7ba99bc3?q=80&w=800&auto=format&fit=crop', // AirPods Max
  'TABLET-IPD': 'https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?q=80&w=800&auto=format&fit=crop', // iPad Pro

  // Seeder / Real SKUs
  'MAC-PRO-16': 'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?q=80&w=800&auto=format&fit=crop', // MacBook Pro 16
  'DELL-XPS-15': 'https://images.unsplash.com/photo-1588872657578-7efd1f1555ed?q=80&w=800&auto=format&fit=crop', // Dell XPS 15
  'KBD-MECH-87': 'https://images.unsplash.com/photo-1618384887929-16ec33fab9ef?q=80&w=800&auto=format&fit=crop', // Mechanical Keyboard
  'MSE-WRLS-ERG': 'https://images.unsplash.com/photo-1615663245857-ac93bb7c39e7?q=80&w=800&auto=format&fit=crop', // Wireless Mouse
  'MON-UW-34': 'https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?q=80&w=800&auto=format&fit=crop', // Ultrawide Monitor
  'HD-ANC-900': 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?q=80&w=800&auto=format&fit=crop', // Noise Cancelling Headphones
  'ADP-USBC-8IN1': 'https://images.unsplash.com/photo-1468495244123-6c6c332eeece?q=80&w=800&auto=format&fit=crop', // USB-C Adapter
  
  // New High-Demand Gadget SKUs
  'WATCH-ULTRA': 'https://images.unsplash.com/photo-1434494878577-86c23bcb06b9?q=80&w=800&auto=format&fit=crop', // Apple Watch Ultra
  'GAME-SWITCH': 'https://images.unsplash.com/photo-1578301978693-85fa9c0320b9?q=80&w=800&auto=format&fit=crop', // Nintendo Switch
  'GAME-PS5': 'https://images.unsplash.com/photo-1606813907291-d86efa9b94db?q=80&w=800&auto=format&fit=crop', // PlayStation 5
  'CAM-GOPRO': 'https://images.unsplash.com/photo-1502982720700-bfff97f2ecac?q=80&w=800&auto=format&fit=crop', // GoPro Action Cam
  'DRONE-DJI': 'https://images.unsplash.com/photo-1527977966376-1c8408f9f108?q=80&w=800&auto=format&fit=crop', // DJI Drone
  'KINDLE-PW': 'https://images.unsplash.com/photo-1544816155-12df9643f363?q=80&w=800&auto=format&fit=crop', // Kindle Paperwhite
  'CAM-CANON': 'https://images.unsplash.com/photo-1516035069371-29a1b244cc32?q=80&w=800&auto=format&fit=crop', // Canon EOS Camera

  // Newly Added Category Fillers
  'ASUS-ROG-14': 'https://images.unsplash.com/photo-1603302576837-37561b2e2302?q=80&w=800&auto=format&fit=crop', // Asus ROG Zephyrus G14
  'THINKPAD-X1': 'https://images.unsplash.com/photo-1588872657578-7efd1f1555ed?q=80&w=800&auto=format&fit=crop', // Lenovo ThinkPad X1 Carbon
  'PHONE-PIX8': 'https://images.unsplash.com/photo-1598327105666-5b89351aff97?q=80&w=800&auto=format&fit=crop', // Google Pixel 8 Pro
  'PHONE-OP12': 'https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?q=80&w=800&auto=format&fit=crop', // OnePlus 12
  'BOSE-QC-ULTRA': 'https://images.unsplash.com/photo-1546435770-a3e426bf472b?q=80&w=800&auto=format&fit=crop', // Bose QuietComfort Ultra
  'SONY-WF5': 'https://images.unsplash.com/photo-1590658268037-6bf12165a8df?q=80&w=800&auto=format&fit=crop', // Sony WF-1000XM5
  'CAM-SONYA7': 'https://images.unsplash.com/photo-1516035069371-29a1b244cc32?q=80&w=800&auto=format&fit=crop', // Sony Alpha 7 IV
  'CAM-OSMO3': 'https://images.unsplash.com/photo-1502920917128-1aa500764cbd?q=80&w=800&auto=format&fit=crop', // DJI Osmo Pocket 3
  'GAME-SDECK': 'https://images.unsplash.com/photo-1605901309584-818e25960a8f?q=80&w=800&auto=format&fit=crop', // Steam Deck OLED
  'VR-QUEST3': 'https://images.unsplash.com/photo-1622979135225-d2ba269cf1ac?q=80&w=800&auto=format&fit=crop', // Meta Quest 3
};

export const getProductImage = (sku: string): string => {
  return PRODUCT_IMAGES[sku] || 'https://images.unsplash.com/photo-1526738549149-8e07eca6c147?q=80&w=800&auto=format&fit=crop';
};
