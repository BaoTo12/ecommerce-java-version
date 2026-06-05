# Developer Guide: How to Benchmark & Quantify E-Commerce System Performance

This guide shows you how to run local load tests and gather exact metrics on the concurrency, security, and query optimization mechanisms built into this codebase. You can use these steps to generate real data for your resume and explain your system design decisions in technical interviews.

---

## 1. Concurrency Benchmarking (Atomic SQL vs ORM)

### Goal
Prove that native atomic SQL updates prevent thundering herd retry storms during high-demand events (like flash sales) compared to standard ORM read-then-write transactions.

### How to Run the Benchmark Local Test

#### Step 1: Install a load testing tool
You can use **Apache Benchmark (`ab`)**, which is lightweight and easy to run on Windows/macOS/Linux.
- **Windows:** Download from [Apache Lounge](https://www.apachelounge.com/) or install via chocolatey: `choco install apache-httpd`.
- **macOS:** Install via homebrew: `brew install httpd`.

#### Step 2: Set up mock inventory
Verify your inventory table has 1 product with exactly `10` items in stock:
```sql
UPDATE inventory SET quantity = 10 WHERE product_id = 'your-product-uuid';
```

#### Step 3: Run the Concurrent Load Test
Simulate `1,000` concurrent checkout execution requests trying to buy that product:
```bash
ab -n 1000 -c 100 -p post_body.json -T "application/json" http://localhost:8080/orders/checkout/execute
```
*(Make sure `post_body.json` contains valid payload structure with your product UUID).*

#### Step 4: Compare results
- **Naïve ORM (Optimistic Version Lock):** Under high concurrency, 95%+ of requests will fail with `ObjectOptimisticLockingFailureException`. The database CPU will spike to 100% due to transaction rollbacks and retries.
- **Atomic SQL (`atomicDecrement`):** Database CPU remains stable (<15%). Exactly 10 requests will return `HTTP 200 (SUCCESS)`, and exactly 990 will immediately return `HTTP 400 (OUT_OF_STOCK)` with 0 retries.

---

## 2. Idempotency & Duplicate Prevention Verification

### Goal
Prove that the API prevents duplicate order creation and double-charging when clients send concurrent/identical requests due to network lag.

### How to Run the Test

#### Step 1: Prepare a concurrent curl script
Create a shell script or bash script that triggers 5 requests at the exact same millisecond using the same `Idempotency-Key` header:

```bash
# Run this in Git Bash or WSL
for i in {1..5}
do
   curl -X POST http://localhost:8080/orders/checkout/execute \
        -H "Idempotency-Key: d3b07384-d113-4886-a579-3dc462cf8b22" \
        -H "Content-Type: application/json" \
        -d '{"strategy":"mock"}' &
done
wait
```

#### Step 2: Inspect Output & Database
- **Response behavior:** Only one request will execute the payment mock gateway and register order creation. The other 4 concurrent requests will return the cached success response body instantly.
- **Verify in Database:** Run this query to verify only 1 payment and 1 order row exist for the idempotency key:
  ```sql
  SELECT count(*) FROM orders WHERE idempotency_key = 'd3b07384-d113-4886-a579-3dc462cf8b22';
  SELECT count(*) FROM payments WHERE order_id = (SELECT id FROM orders WHERE idempotency_key = 'd3b07384-d113-4886-a579-3dc462cf8b22');
  ```
  Both counts must be exactly `1`.

---

## 3. Database N+1 Query Prevention Metrics

### Goal
Quantify database query reduction on paginated lists.

### How to Gather Metrics

#### Step 1: Enable SQL Query Logging
In `src/main/resources/application.yml` (or `application.properties`), temporarily enable SQL formatting:
```yaml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true
```

#### Step 2: Seed Dummy Orders
Ensure your database has at least 50 orders (each containing 3–5 order items) for a test user.

#### Step 3: Trigger the Endpoint
Perform a request to retrieve a page of 20 orders:
```bash
curl -H "Authorization: Bearer <token>" http://localhost:8080/orders?size=20
```

#### Step 4: Count the Logged SQL Statements
- **Naïve Fetch (N+1):** Look at your console log. You will see 1 SQL query to load the orders, followed by 20 separate SQL queries loading item details for each order row. **Total = 21 queries**.
- **Pagination-Safe Fetch Join:** You will see exactly **2 queries** in the log.
  - Query 1: Fetch paginated IDs.
  - Query 2: Fetch joined items using the `IN` clause with those IDs.
- **Formula:** 
  $$\text{Query Reduction} = \frac{(N + 1) - 2}{N + 1} \times 100\% = \frac{21 - 2}{21} \times 100\% \approx 90.5\%$$
  *(For a page size of 50, query reduction is over 96%).*

---

## 4. Token-Versioning Revocation Delay Test

### Goal
Prove that access sessions can be revoked instantly without waiting for access token expiration (standard JWT weakness).

### How to Verify

#### Step 1: Obtain an Access Token
Perform a successful login request. Note the `token_version` embedded in the access JWT token payload (typically `0`).

#### Step 2: Make an Authorized Request
Trigger a request to `/users/me` using the access token. It returns `HTTP 200`.

#### Step 3: Trigger Logout-All or Change Password
Execute a password change or logout-all request. This increments the `token_version` in the database.
```bash
curl -X POST http://localhost:8080/users/me/logout-all -H "Authorization: Bearer <token>"
```

#### Step 4: Test Immediate Revocation
Immediately repeat Step 2 with the same access token.
- **Expected Outcome:** You will receive `HTTP 401 Unauthorized` instantly, even if the access token has 15+ minutes remaining before expiration.
- **Revocation Delay:** **0 milliseconds**.
