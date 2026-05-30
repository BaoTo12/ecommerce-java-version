export const PRODUCT_IMAGES: Record<string, string> = {
  'PHONE-IP15': 'https://images.unsplash.com/photo-1695048133142-1a20484d2569?q=80&w=800&auto=format&fit=crop', // iPhone 15 Pro Max
  'PHONE-SS24': 'https://images.unsplash.com/photo-1610945265064-0e34e5519bbf?q=80&w=800&auto=format&fit=crop', // Samsung Galaxy S24 Ultra
  'LAPTOP-MBP': 'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?q=80&w=800&auto=format&fit=crop', // MacBook Pro
  'HEADPHONE-APM': 'https://images.unsplash.com/photo-1613040809024-b4ef7ba99bc3?q=80&w=800&auto=format&fit=crop', // AirPods Max
  'TABLET-IPD': 'https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?q=80&w=800&auto=format&fit=crop', // iPad Pro
};

export const getProductImage = (sku: string): string => {
  return PRODUCT_IMAGES[sku] || 'https://images.unsplash.com/photo-1526738549149-8e07eca6c147?q=80&w=800&auto=format&fit=crop'; // Default tech abstract
};
