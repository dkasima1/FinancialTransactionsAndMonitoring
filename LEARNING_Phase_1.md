# Learning Guide — Financial Transactions & Monitoring

## Phase 1: Postgres + Spring Boot (ACID, Locking, Relational Modeling)

### How to Run

```bash
# Start Postgres
docker compose up -d

# Set Java environment
source .env.sh

# Run the application
mvn spring-boot:run
```

The app runs on `http://localhost:8080`.

### Swagger UI

Once the app is running, open your browser to:

- **Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) — interactive API explorer where you can call all endpoints directly from the browser
- **OpenAPI spec:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs) — raw JSON schema of the API

You can use Swagger UI instead of curl for all the "Try it" examples below. Expand an endpoint, click **"Try it out"**, fill in the JSON body, and hit **"Execute"**.

**Where to look:** The Swagger dependency is in `pom.xml` (`springdoc-openapi-starter-webmvc-ui`). SpringDoc scans your `@RestController` classes automatically — no additional configuration needed.

---

### Concept 1: Relational Data Modeling

**Where to look:** `src/main/java/com/ftm/app/model/Account.java` and `Transaction.java`

Accounts and Transactions are separate tables linked by foreign keys (`from_account_id`, `to_account_id`). This enforces **referential integrity** — you can't create a transaction pointing to an account that doesn't exist. Postgres enforces this at the database level, not just in application code.

**Try it:**

```bash
# Create two accounts
curl -s -X POST http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" \
  -d '{"ownerName":"Alice","initialBalance":1000,"currency":"USD"}' | jq .

curl -s -X POST http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" \
  -d '{"ownerName":"Bob","initialBalance":500,"currency":"USD"}' | jq .
```

Note the `accountNumber` values returned — you'll need them for transfers.

**Tradeoff to think about:** Relational models give you joins and constraints, but every table must conform to a fixed schema. Later, when we add MongoDB, notice how easy it is to store irregular/nested data — but at the cost of losing joins and foreign key enforcement.

---

### Concept 2: ACID Transactions

**Where to look:** `src/main/java/com/ftm/app/service/TransactionService.java` — the `transfer()` method

The `@Transactional` annotation wraps the entire transfer (debit one account, credit another, insert transaction record) in a single database transaction. If anything fails midway, **everything rolls back** — you'll never see money disappear from one account without appearing in the other.

**Try it:**

```bash
# Transfer $200 from Alice to Bob (use your actual account numbers)
curl -s -X POST http://localhost:8080/api/transactions/transfer \
  -H "Content-Type: application/json" \
  -d '{"fromAccountNumber":"ACC-XXXXXXXX","toAccountNumber":"ACC-YYYYYYYY","amount":200,"description":"Lunch money"}' | jq .

# Check both balances — they should reflect the transfer atomically
curl -s http://localhost:8080/api/accounts/ACC-XXXXXXXX | jq .balance
curl -s http://localhost:8080/api/accounts/ACC-YYYYYYYY | jq .balance
```

**Tradeoff to think about:** ACID guarantees correctness but requires coordination (locks, write-ahead logs). This limits throughput. Later, when we add Cassandra (which is "eventually consistent"), you'll see how giving up ACID allows much higher write throughput — but at the cost of potential temporary inconsistencies.

---

### Concept 3: Pessimistic Locking (SELECT ... FOR UPDATE)

**Where to look:** `src/main/java/com/ftm/app/repository/AccountRepository.java` — the `findByAccountNumberForUpdate()` method, and `TransactionService.java` lines 40–50

When two transfers hit the same account simultaneously, we need to prevent them from reading the same balance and both deducting from it. `@Lock(LockModeType.PESSIMISTIC_WRITE)` translates to `SELECT ... FOR UPDATE` in SQL — it tells Postgres "lock this row; anyone else trying to read it for update must wait."

**Try it — see the lock in action:**

Open a terminal and send two rapid transfers from the same account:

```bash
# Send these two at the same time (both tabs, hit enter quickly)
# Terminal 1:
curl -s -X POST http://localhost:8080/api/transactions/transfer \
  -H "Content-Type: application/json" \
  -d '{"fromAccountNumber":"ACC-XXXXXXXX","toAccountNumber":"ACC-YYYYYYYY","amount":400}' | jq .

# Terminal 2:
curl -s -X POST http://localhost:8080/api/transactions/transfer \
  -H "Content-Type: application/json" \
  -d '{"fromAccountNumber":"ACC-XXXXXXXX","toAccountNumber":"ACC-YYYYYYYY","amount":400}' | jq .
```

If Alice started with $1000 and already transferred $200, she has $800. Both requests try to take $400. With locking, the first succeeds (balance becomes $400), and the second also succeeds (balance becomes $0). Without locking, both could read $800 and both deduct, corrupting the balance.

Try transferring more than the remaining balance — you'll get a `FAILED` transaction with "Insufficient funds."

**Tradeoff to think about:** Pessimistic locking is safe but slow — the second request **waits** until the first finishes. Optimistic locking (using version numbers) is an alternative that lets both proceed but rejects one on conflict. We chose pessimistic here because financial balances cannot tolerate incorrect intermediate reads.

---

### Concept 4: Deadlock Prevention

**Where to look:** `src/main/java/com/ftm/app/service/TransactionService.java` lines 37–47

Imagine two simultaneous transfers: Alice→Bob and Bob→Alice. Without ordering:
- Transfer 1 locks Alice's account, then tries to lock Bob's
- Transfer 2 locks Bob's account, then tries to lock Alice's
- **Deadlock** — both are waiting for each other forever

The fix: always lock accounts in alphabetical order by account number. Both transfers will lock Alice first, then Bob. One waits, but neither deadlocks.

**Tradeoff to think about:** This is a simple solution that works within a single database. In distributed systems (multiple databases, microservices), deadlock prevention becomes much harder — you'd need distributed locks (Redis/Zookeeper) or saga patterns. We'll explore this in later phases.

---

### Concept 5: Validation at the Boundary

**Where to look:** `src/main/java/com/ftm/app/dto/CreateAccountRequest.java` and `TransferRequest.java`

Request DTOs use Jakarta Bean Validation (`@NotBlank`, `@Positive`, `@PositiveOrZero`) to reject bad input before it reaches the service layer. This is "validate at the boundary" — trust internal code, but never trust external input.

**Try it:**

```bash
# Missing owner name — should get a 400 error
curl -s -X POST http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" \
  -d '{"ownerName":"","initialBalance":1000,"currency":"USD"}' | jq .

# Negative transfer amount — should get a 400 error
curl -s -X POST http://localhost:8080/api/transactions/transfer \
  -H "Content-Type: application/json" \
  -d '{"fromAccountNumber":"ACC-XXXXXXXX","toAccountNumber":"ACC-YYYYYYYY","amount":-50}' | jq .
```

---

### SQL Logging (See What Hibernate Generates)

**Where to look:** `src/main/resources/application.yml` — `show-sql: true` and `format_sql: true`

Watch your terminal while making API calls. You'll see the actual SQL that Hibernate generates, including the `SELECT ... FOR UPDATE` statement. This is invaluable for understanding what the ORM is doing under the hood.

---

## What's Next

| Phase | Technology | What You'll Learn |
|-------|-----------|-------------------|
| 2 | Redis | Cache-aside pattern, idempotency keys, TTL, cache vs DB consistency |
| 3 | Kafka | Event-driven architecture, topics, partitions, ordering guarantees |
| 4 | Cassandra | AP vs CP, partition keys, write-optimized storage, comparing queries with Postgres |
| 5 | Flink | Stream processing, windowed aggregation, real-time fraud detection |
| 6 | Elasticsearch | Full-text search, inverted indexes, near-real-time indexing |
| 7 | MongoDB | Document model, flexible schemas, comparing with relational approach |
| 8 | API Gateway | Rate limiting, auth, routing, centralized cross-cutting concerns |
