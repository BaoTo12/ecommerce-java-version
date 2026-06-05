export interface MockProduct {
  id: string;
  sku: string;
  name: string;
  description: string;
  price: number;
  category: string;
}

const BASE_PRODUCTS: MockProduct[] = [
  // Laptops & Computers
  {
    id: 'c3d4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e7f',
    sku: 'MAC-PRO-16',
    name: 'MacBook Pro 16-inch',
    description: 'Apple M3 Max chip, 36GB RAM, 1TB SSD',
    price: 64990000.00,
    category: 'Laptops & Computers'
  },
  {
    id: 'f6a7b8c9-d0e1-4f2a-3b4c-5d6e7f8a9b0c',
    sku: 'DELL-XPS-15',
    name: 'Dell XPS 15',
    description: 'Intel Core i9, 32GB RAM, 1TB SSD, RTX 4060',
    price: 48990000.00,
    category: 'Laptops & Computers'
  },
  {
    id: '2c3d4e5f-6a7b-8c9d-0e1f-2a3b4c5d6e7f',
    sku: 'MON-UW-34',
    name: 'UltraWide Monitor 34-inch',
    description: '34-inch curved WQHD monitor, 144Hz refresh rate',
    price: 11990000.00,
    category: 'Laptops & Computers'
  },
  {
    id: 'ab1c2d3e-4f5a-6b7c-8d9e-0f1a2b3c4d5e',
    sku: 'ASUS-ROG-14',
    name: 'Asus ROG Zephyrus G14',
    description: 'ROG Nebula Display, AMD Ryzen 9, 32GB RAM, 1TB SSD, RTX 4070',
    price: 54990000.00,
    category: 'Laptops & Computers'
  },
  {
    id: 'bc2c3d4e-5f6a-7b8c-9d0e-1f2a3b4c5d6e',
    sku: 'THINKPAD-X1',
    name: 'Lenovo ThinkPad X1 Carbon',
    description: 'Ultralight business laptop, Intel Core i7, 16GB RAM, 512GB SSD',
    price: 42990000.00,
    category: 'Laptops & Computers'
  },

  // Phones & Tablets
  {
    id: 'a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d',
    sku: 'PHONE-IP15',
    name: 'iPhone 15 Pro Max',
    description: 'Apple iPhone 15 Pro Max 256GB - Premium titanium design',
    price: 34990000.00,
    category: 'Phones & Tablets'
  },
  {
    id: 'b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e',
    sku: 'PHONE-SS24',
    name: 'Samsung Galaxy S24 Ultra',
    description: 'Samsung Galaxy S24 Ultra 512GB - AI Camera integration',
    price: 31990000.00,
    category: 'Phones & Tablets'
  },
  {
    id: 'e5f6a7b8-c9d0-4e1f-2a3b-4c5d6e7f8a9b',
    sku: 'TABLET-IPD',
    name: 'iPad Pro 12.9"',
    description: 'Apple iPad Pro 12.9-inch Liquid Retina XDR M2 chip',
    price: 28990000.00,
    category: 'Phones & Tablets'
  },
  {
    id: 'cd3d4e5f-6a7b-8c9d-0e1f-2a3b4c5d6e7f',
    sku: 'PHONE-PIX8',
    name: 'Google Pixel 8 Pro',
    description: 'Google Pixel 8 Pro 128GB - Obsidian, advanced AI Google camera',
    price: 22490000.00,
    category: 'Phones & Tablets'
  },
  {
    id: 'de4e5f6a-7b8c-9d0e-1f2a-3b4c5d6e7f8a',
    sku: 'PHONE-OP12',
    name: 'OnePlus 12',
    description: 'OnePlus 12 256GB Silky Black, 16GB RAM, Snapdragon 8 Gen 3',
    price: 18990000.00,
    category: 'Phones & Tablets'
  },

  // Audio & Accessories
  {
    id: '0a1b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d',
    sku: 'KBD-MECH-87',
    name: 'Mechanical Keyboard',
    description: 'Tenkeyless mechanical keyboard with brown switches',
    price: 2990000.00,
    category: 'Audio & Accessories'
  },
  {
    id: '1b2c3d4e-5f6a-7b8c-9d0e-1f2a3b4c5d6e',
    sku: 'MSE-WRLS-ERG',
    name: 'Wireless Ergonomic Mouse',
    description: 'Ergonomic multi-device wireless mouse',
    price: 1890000.00,
    category: 'Audio & Accessories'
  },
  {
    id: '3d4e5f6a-7b8c-9d0e-1f2a-3b4c5d6e7f8a',
    sku: 'HD-ANC-900',
    name: 'Noise Cancelling Headphones',
    description: 'Active noise cancelling wireless over-ear headphones',
    price: 6990000.00,
    category: 'Audio & Accessories'
  },
  {
    id: '4e5f6a7b-8c9d-0e1f-2a3b-4c5d6e7f8a9b',
    sku: 'ADP-USBC-8IN1',
    name: 'USB-C Multi-port Adapter',
    description: '8-in-1 USB-C hub with HDMI, Ethernet, USB 3.0',
    price: 990000.00,
    category: 'Audio & Accessories'
  },
  {
    id: 'd4e5f6a7-b8c9-4d0e-1f2a-3b4c5d6e7f8a',
    sku: 'HEADPHONE-APM',
    name: 'AirPods Max',
    description: 'Apple AirPods Max - Space Gray high-fidelity over-ear audio',
    price: 13490000.00,
    category: 'Audio & Accessories'
  },
  {
    id: '5f6a7b8c-9d0e-1f2a-3b4c-5d6e7f8a9b0c',
    sku: 'WATCH-ULTRA',
    name: 'Apple Watch Ultra 2',
    description: 'Rugged adventure watch with bright Always-On Retina display',
    price: 22990000.00,
    category: 'Audio & Accessories'
  },
  {
    id: 'ef5f6a7b-8c9d-0e1f-2a3b-4c5d6e7f8a9b',
    sku: 'BOSE-QC-ULTRA',
    name: 'Bose QuietComfort Ultra',
    description: 'Spatial audio wireless noise cancelling over-ear headphones',
    price: 9490000.00,
    category: 'Audio & Accessories'
  },
  {
    id: 'fa6a7b8c-9d0e-1f2a-3b4c-5d6e7f8a9b0c',
    sku: 'SONY-WF5',
    name: 'Sony WF-1000XM5',
    description: 'Premium noise cancelling wireless earbuds with high-res audio',
    price: 5990000.00,
    category: 'Audio & Accessories'
  },

  // Cameras & Drones
  {
    id: '8c9d0e1f-2a3b-4c5d-6e7f-8a9b0c1d2e3f',
    sku: 'CAM-GOPRO',
    name: 'GoPro Hero 12 Black',
    description: 'Ultra-versatile action camera with HyperSmooth stabilization',
    price: 10490000.00,
    category: 'Cameras & Drones'
  },
  {
    id: '9d0e1f2a-3b4c-5d6e-7f8a-9b0c1d2e3f4a',
    sku: 'DRONE-DJI',
    name: 'DJI Mini 4 Pro Drone',
    description: 'Lightweight folding drone with 4K HDR camera & omnidirectional sensing',
    price: 19990000.00,
    category: 'Cameras & Drones'
  },
  {
    id: '1f2a3b4c-5d6e-7f8a-9b0c-1d2e3f4a5b6c',
    sku: 'CAM-CANON',
    name: 'Canon EOS R5 Camera',
    description: 'Full-frame mirrorless camera featuring 45MP sensor and 8K video capture',
    price: 89990000.00,
    category: 'Cameras & Drones'
  },
  {
    id: 'ab2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d',
    sku: 'CAM-SONYA7',
    name: 'Sony Alpha 7 IV',
    description: 'Full-frame mirrorless camera, 33MP hybrid photo & video shooter',
    price: 57990000.00,
    category: 'Cameras & Drones'
  },
  {
    id: 'bc3c4d5e-6f7a-8b9c-0d1e-2f3a4b5c6d7e',
    sku: 'CAM-OSMO3',
    name: 'DJI Osmo Pocket 3',
    description: '3-axis gimbal stabilizer camera with 1-inch CMOS sensor',
    price: 12990000.00,
    category: 'Cameras & Drones'
  },

  // Gaming & Entertainment
  {
    id: '6a7b8c9d-0e1f-2a3b-4c5d-6e7f8a9b0c1d',
    sku: 'GAME-SWITCH',
    name: 'Nintendo Switch OLED',
    description: 'Vibrant 7-inch OLED screen, local & online co-op console',
    price: 8990000.00,
    category: 'Gaming & Entertainment'
  },
  {
    id: '7b8c9d0e-1f2a-3b4c-5d6e-7f8a9b0c1d2e',
    sku: 'GAME-PS5',
    name: 'Sony PlayStation 5',
    description: 'Lightning fast SSD loading, immersive 3D audio, and 4K gaming',
    price: 14490000.00,
    category: 'Gaming & Entertainment'
  },
  {
    id: '0e1f2a3b-4c5d-6e7f-8a9b-0c1d2e3f4a5b',
    sku: 'KINDLE-PW',
    name: 'Kindle Paperwhite',
    description: '6.8-inch display, adjustable warm light, and up to 10 weeks battery',
    price: 3990000.00,
    category: 'Gaming & Entertainment'
  },
  {
    id: 'cd4d5e6f-7a8b-9c0d-1e2f-3a4b5c6d7e8f',
    sku: 'GAME-SDECK',
    name: 'Steam Deck OLED',
    description: 'Handheld gaming console with 512GB NVMe SSD, HDR OLED screen',
    price: 16490000.00,
    category: 'Gaming & Entertainment'
  },
  {
    id: 'de5d6e7f-8a9b-0c1d-2e3f-4a5b6c7d8e9f',
    sku: 'VR-QUEST3',
    name: 'Meta Quest 3',
    description: '128GB virtual reality mixed reality headset with high-res display',
    price: 13990000.00,
    category: 'Gaming & Entertainment'
  }
];

const CATEGORIES = [
  'Laptops & Computers',
  'Phones & Tablets',
  'Audio & Accessories',
  'Cameras & Drones',
  'Gaming & Entertainment'
];

export const MOCK_PRODUCTS: MockProduct[] = [...BASE_PRODUCTS];

CATEGORIES.forEach(cat => {
  const currentCount = BASE_PRODUCTS.filter(p => p.category === cat).length;
  const needed = 30 - currentCount;
  const prefix = cat.split(' ')[0].toUpperCase();
  for (let i = 1; i <= needed; i++) {
    const skuSuffix = `${prefix}-GEN-${i}`;
    MOCK_PRODUCTS.push({
      id: `dynamic-uuid-${cat.replace(/[^a-zA-Z]/g, '')}-${i}`,
      sku: skuSuffix,
      name: `Premium ${cat.replace(/ &.*/, '')} Model ${i}`,
      description: `High-performance dynamic device in the ${cat} category, model version ${i}.`,
      price: cat === 'Laptops & Computers' ? 25000000 + i * 1000000
           : cat === 'Phones & Tablets' ? 12000000 + i * 800000
           : cat === 'Audio & Accessories' ? 2000000 + i * 200000
           : cat === 'Cameras & Drones' ? 15000000 + i * 1500000
           : 5000000 + i * 500000,
      category: cat
    });
  }
});
