import http from 'k6/http';
import { check } from 'k6';

// Config options: 1,000 iterations shared by 100 concurrent Virtual Users (VUs)
export const options = {
    scenarios: {
        concurrent_checkout: {
            executor: 'shared-iterations',
            vus: 100,
            iterations: 1000,
            maxDuration: '30s',
        },
    },
};

// Setup runs ONCE before the test starts:
// It looks up a product ID dynamically and resets its stock to 10.
export function setup() {
    const catalogRes = http.get('http://localhost:8080/catalog');
    const products = catalogRes.json().content;
    if (!products || products.length === 0) {
        throw new Error('No products found in database! Make sure the backend is seeded and running.');
    }
    const productId = products[0].id;

    // Reset stock to 1000 to enable full write contention
    const resetRes = http.post(
        'http://localhost:8080/api/benchmark/reset',
        JSON.stringify({ productId: productId, quantity: 1000 }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    if (resetRes.status !== 200) {
        throw new Error(`Failed to reset stock: ${resetRes.body}`);
    }

    console.log(`[k6 setup] Database stock initialized to 1000 for Product UUID: ${productId}`);
    return { productId: productId };
}

// Default run block executed by VUs:
export default function (data) {
    // Read the strategy from the environment variable (defaults to 'atomic')
    const strategy = __ENV.STRATEGY || 'atomic';
    const url = 'http://localhost:8080/api/benchmark/checkout';
    const payload = JSON.stringify({
        productId: data.productId,
        quantity: 1,
        strategy: strategy
    });
    const params = {
        headers: { 'Content-Type': 'application/json' },
    };

    const res = http.post(url, payload, params);

    // Track response codes
    if (strategy === 'atomic') {
        check(res, {
            'HTTP status is 200 or 400': (r) => r.status === 200 || r.status === 400,
            'HTTP 200 (SUCCESS)': (r) => r.status === 200,
            'HTTP 400 (OUT OF STOCK)': (r) => r.status === 400,
        });
    } else {
        check(res, {
            'HTTP status is 200 or 400 or 409': (r) => r.status === 200 || r.status === 400 || r.status === 409,
            'HTTP 200 (SUCCESS)': (r) => r.status === 200,
            'HTTP 400 (OUT OF STOCK)': (r) => r.status === 400,
            'HTTP 409 (OPTIMISTIC LOCK FAILURE)': (r) => r.status === 409,
        });
    }
}
