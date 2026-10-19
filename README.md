# Digital Wallet

## 1. What it is

A simplified PayPal. Users sign in with one-time magic links (email or phone, no passwords), see their balance, pay
other users and merchants instantly, and move money to and from bank accounts. Behind the scenes a double-entry
ledger records every cent, bank transfers go out in scheduled batches with retries, extra cash is swept to a partner
bank at the end of the day, and a reconciliation checks that the books match the bank: a 1-cent difference freezes
outgoing money and sends an alert.

Java 21, Spring Boot 3.4, Spring Cloud Gateway Server MVC, PostgreSQL, Angular. Four small services talking over
REST.

## 2. Quick start

```bash
git clone https://github.com/drini26/digital-wallet-app.git && cd digital-wallet-app
docker compose up -d --build
# open http://localhost:4200
```

The first build takes a few minutes (Maven and npm download their dependencies). Every setting has a default, so no
`.env` is needed. The database passwords, the JWT secret and the internal API key in `docker-compose.yml`,
`.env.example` and the `application.properties` files are **dev-only values**; production reads them from a secrets
vault. If a port is already taken on your machine (for example a local PostgreSQL on 5432 or another app
on 8080), copy `.env.example` to `.env` and change `POSTGRES_HOST_PORT`, `GATEWAY_HOST_PORT`, ... there.

| What | URL |
|---|---|
| Web app | http://localhost:4200 |
| Mailpit (sign-in links, demo SMS, alert emails) | http://localhost:8025 |
| API gateway | http://localhost:8080 (or `GATEWAY_HOST_PORT`) |
| Prometheus (metrics and alert rules) | http://localhost:9090 |

**Demo users** (sign in with the email, then click the link in Mailpit):

| Name | Email | Role | Opening balance |
|---|---|---|---|
| Elira Manager | elira@wallet.local | MANAGER | - |
| Ana Customer | ana@wallet.local | CUSTOMER, personal | 1,000.00 EUR |
| Besi Customer | besi@wallet.local | CUSTOMER, personal | 500.00 EUR |
| Kafe Tirana | kafe@wallet.local | CUSTOMER, merchant | 0.00 EUR |

New users use the **Create account** tab (name, email or phone like `+355 69 123 4567`, personal or merchant).
Phone users get their "SMS" in Mailpit as an email to `+355691234567@sms.wallet.local`.

## 3. Architecture

```
Browser (Angular, nginx :4200)
   |  /api/** (nginx proxies to the gateway)
API GATEWAY :8080        routes requests, checks the JWT (HS256), /admin/** needs MANAGER, rate limit on sign-in
   |-- /api/v1/auth/**, /users/**                        -> auth-service    :8081  (auth_db)
   |-- /api/v1/wallet/**, /transactions/**,
   |   /admin/ledger/**, /admin/banking/sweep,
   |   /admin/metrics, /admin/system/**,
   |   /admin/transactions/**                            -> ledger-service  :8082  (ledger_db)   ALL money
   `-- /api/v1/bank/**, /admin/banking/batches/**,
       /admin/mock-bank/**                               -> banking-service :8083  (banking_db)  + mock bank

Service to service (REST, X-Internal-Api-Key, never through the gateway):
   auth    -> ledger   POST /internal/wallets                         (create the wallet at sign-up)
   banking -> ledger   GET /internal/wallets/{id}, POST /internal/holds,
                       POST /internal/transfers/{id}/incoming|settled|failed, GET /internal/system-status
   ledger  -> banking  GET /internal/bank-balances, POST /internal/sweeps
```

Containers: postgres (three databases, one user each), mailpit, the four services, the frontend and Prometheus.

**Why these three services.** They follow the three jobs of the assignment: who you are (auth), where the money is
(ledger), how money gets to and from banks (banking). Each owns its database, so one cannot corrupt another's data.

**Why all money lives in ledger-service.** A payment touches two wallets. If both are in one database, the payment
is one local ACID transaction: it either fully happens or not at all, with no network call in the middle, which is
also why it answers well within 200 ms. Splitting wallets across services would need a distributed transaction.

## 4. How money moves (double entry)

Every movement is a transaction with entries whose debits equal their credits. Assets (cash at banks) grow with a
DEBIT, liabilities (what we owe users) grow with a CREDIT. The books are right when assets = liabilities.

| Step | OPERATING_BANK (asset) | Ana (liability) | Besi (liability) | OUTGOING_PENDING (liability) | Check |
|---|---|---|---|---|---|
| Deposit 100 to Ana | 100 | 100 | 0 | 0 | 100 = 100 |
| Ana pays Besi 30 | 100 | 70 | 30 | 0 | 100 = 100 |
| Ana withdraws 20 (hold) | 100 | 50 | 30 | 20 | 100 = 100 |
| The bank pays it out | 80 | 50 | 30 | 0 | 80 = 80 |
| (or: the bank rejects it) | 100 | 70 | 30 | 0 | 100 = 100 |
| Sweep 50 to the partner bank | 50 (+50 PARTNER_RESERVE) | ... | ... | ... | 100 = 100 |

The database enforces it too: entries are append-only (trigger), a transaction whose debits and credits differ
cannot commit (deferred trigger), and a wallet balance can never go below zero (CHECK constraint).

## 5. The main flows

**Payment** (`POST /api/v1/transactions/pay`, `PaymentService`), one database transaction:
1. `SET LOCAL lock_timeout = '2s'` (a busy wallet answers 503 "retry" instead of hanging).
2. Find both wallets (ids only).
3. Lock both rows (`SELECT ... FOR UPDATE`) in id order: no deadlock when Ana pays Besi while Besi pays Ana.
4. Idempotency-Key already used? Return the stored result (200 + `Idempotent-Replay: true`). Checked **after** the
   lock, so two identical requests at the same moment cannot both pass.
5. Rules: EUR, max amount, not yourself, P2P to personal / MERCHANT to merchant, enough money.
6. Post DEBIT payer, CREDIT payee (`LedgerPostingService`, the only class that writes entries).
7. Store the payer's new balance and the processing time; answer 201.

Payments are instant, so a stored payment always has status SUCCESS. A refused payment is not stored: it comes back
as an error with a code (for example 422 `INSUFFICIENT_FUNDS`).

**Withdrawal** (`POST /api/v1/bank/transfer`):
1. banking checks the routing number and that the wallet belongs to the user, saves the transfer (REQUESTED).
2. ledger holds the money: DEBIT wallet, CREDIT OUTGOING_PENDING. Transfer QUEUED, answer 202 + arrival date.
3. The batch (10:00 and 16:00, or "Run bank batch now") sends it with `bank_reference` = transfer id: SENT.
   Bank unreachable: PENDING_RETRY, resent after 30 s, 60 s, 120 s ... with the **same** reference, so the bank
   recognises a repeat and never pays twice.
4. The bank settles: SETTLED, then the ledger books DEBIT OUTGOING_PENDING, CREDIT OPERATING_BANK.
   The bank rejects: REJECTED, the ledger gives the money back (DEBIT OUTGOING_PENDING, CREDIT wallet).
5. If the ledger is down when banking tells it the result, `ledger_notified` stays false and a job retries every
   15 s (a simple outbox: remember in our own database what still has to be said).

**Reconciliation** (`POST /api/v1/admin/ledger/reconcile`, nightly at 23:30). An optional body
`{"targetDate": "YYYY-MM-DD"}` (default today, never in the future) names the business day of the run; the
comparison always uses the current balances, because the ledger keeps running balances, not daily snapshots.
The answer has `totalSystemBalance` (wallets + OUTGOING_PENDING) and `actualBankBalance` (bank operating + partner).
1. Ask banking for the bank balances (and how many results the ledger has not heard yet).
2. Internal check: all wallets + OUTGOING_PENDING = OPERATING_BANK + PARTNER_RESERVE.
3. Bank check: ledger OPERATING_BANK = bank operating, ledger PARTNER_RESERVE = bank partner.
4. Any difference, even 0.01: MISMATCHED, outgoing money frozen (withdrawals and sweeps stop), alert email, ERROR log.
   A manager investigates and unfreezes.

While frozen, outgoing money stops (new withdrawals, the withdrawals in the batch, sweeps). Payments between users
continue, because that money never leaves the books.

## 6. Requirements coverage

| Assignment | Requirement | Where |
|---|---|---|
| 1.3 | Sign up | `POST /api/v1/auth/register`, `SignUpService`, "Create account" tab |
| 1.3 | Profile update | `PUT /api/v1/users/me` (display name), `UserService.updateDisplayName` |
| 1.3 | Transaction history | `GET /api/v1/wallet/transactions`, `TransactionHistoryService`, wallet page |
| 2.1 | Magic links by email or phone, no passwords | `MagicLinkService`, `LoginService`, `LinkSender`, `MailpitSmsGateway` |
| 2.1 | Roles CUSTOMER and MANAGER | JWT `role` claim; gateway + each service's `SecurityConfig` |
| 2.1 | Manager: error logs | `GET /api/v1/admin/errors`, `ErrorEventService` (saved by `ApiErrorHandler`), "Error log" on the Operations page |
| 2.1 | Manager: transaction reversals | `POST /api/v1/admin/transactions/{id}/reverse`, `ReversalService`, Transactions page |
| 2.2 | Payment result within 200 ms | one local transaction, `processing_ms`, Micrometer timer `wallet.payment.duration`; measured in section 9 |
| 2.2 | UI refreshes every 5 s | `frontend/src/app/core/polling.ts` |
| 2.2 | Connection drop: pending + retry, no duplicates | `BatchService` (PENDING_RETRY, same bank reference), mock bank deduplication, Idempotency-Key |
| 2.2 | Coverage > 80% | JaCoCo check in the parent POM, per module |
| 2.3 | ACH batching at scheduled times | `BankingJobs` cron 10:00 / 16:00, `BatchService` |
| 2.3 | End-of-day liquidity sweep | `SweepService`, `LedgerJobs` 23:00 |
| 2.3 | Double-entry ledger | `V1__ledger.sql` triggers, `LedgerPostingService` |
| 3.1 | `POST /auth/magic-link`, `POST /auth/login` | `AuthController` |
| 3.2 | `GET /wallet/balance` | `WalletController` |
| 3.3 | `POST /transactions/pay` | `PaymentController`, `PaymentService` |
| 3.4 | `POST /bank/transfer` | `TransferController`, `TransferService` |
| 3.4 | Deposits (INFLOW) as well as withdrawals (OUTFLOW) | `TransferService`, `TransferSettlementService`; test `depositHappyPath` |
| 3.5 | `POST /admin/banking/sweep` | `AdminController`, `SweepService` |
| 3.6 | `POST /admin/ledger/reconcile` | `AdminController`, `ReconciliationService` |
| 3.7 | `GET /admin/metrics` | `AdminController`, `AdminMetricsService` |
| 4 | Insufficient funds, no overdraft | `PaymentRules` after the lock + DB CHECK `balance >= 0` |
| 4 | Sequential updates for one account | `LedgerPostingService.lockInIdOrder` (`SELECT ... FOR UPDATE`) |
| 4 | Zero-variance rule: freeze + alert | `ReconciliationService`, `SystemControlService`, `AlertService` |
| 4 | Consistency across regions | explained in section 8 (not implemented) |
| 5.1 / 5.2 | Scaling, encryption, alerting, 5% rollout | section 8; alert rules in `infra/prometheus/alert-rules.yml` |
| 5 | Alert when more than 1% of requests fail over 3 minutes | `ErrorRateHigh` in `infra/prometheus/alert-rules.yml` |

All endpoints are under `/api/v1`. Errors always have the same JSON shape:
`{"status": 422, "title": "Insufficient funds", "detail": "...", "code": "INSUFFICIENT_FUNDS"}`.

## 7. Design decisions in short

- **Locking in id order.** Payments lock both wallet rows with `SELECT ... FOR UPDATE`, always smallest id first:
  parallel payments from one wallet wait in line (no double spending), opposite payments cannot deadlock.
- **Idempotency keys.** The client sends an `Idempotency-Key` with every payment and transfer; a repeat returns the
  first result. A unique index on `(user, key)` is the final guarantee.
- **The database guards the books.** Append-only and balanced-transaction triggers, a CHECK on wallet balances, and
  unique `(type, external_reference)` so a repeated bank notice can never post twice.
- **REST + retry instead of a message broker.** Every service call is idempotent; the caller retries (200, 400,
  800 ms). Results that must not be lost are remembered in the caller's own database (`ledger_notified`) and retried
  by a job.
- **Wallet creation.** auth-service asks the ledger to create the wallet at sign-up (idempotent by owner id). If the
  ledger was down, the wallet is created lazily on the user's first wallet call, from the JWT claims.
- **Servlet gateway on virtual threads.** The gateway is Spring Cloud Gateway Server MVC: normal servlet code, a
  normal `SecurityFilterChain`, the Bucket4j rate limit is a plain `OncePerRequestFilter`. With
  `spring.threads.virtual.enabled=true` a request waiting for a service does not block a real thread, so we get good
  throughput without reactive code. Timeouts: 3 s connect, 10 s read; a service that is down or too slow gives 503.
- **Freeze on mismatch.** A wallet that cannot explain one cent stops sending money out until a human checks.

## 8. Production / next steps

What this version leaves out on purpose, and what a production system would add:
- **Kafka + transactional outbox** for events between services (instead of REST calls + the `ledger_notified` retry),
  with deduplicating consumers.
- **Hot accounts.** One very popular merchant serialises on its row lock; at scale, credits to hot accounts become
  delta rows that a job adds up every second.
- **Encryption.** Email and phone encrypted at rest (AES-GCM) with a blind index for lookups; TLS everywhere;
  secrets in a vault, not environment variables.
- **RS256 + JWKS** instead of one shared HS256 secret: only auth-service can sign, everyone else only verifies.
  Mutual TLS or OAuth2 client credentials instead of the internal API key.
- **Scaling.** Services are stateless: run several replicas behind a Kubernetes HPA. The database scales with read
  replicas for history and metrics, and later by sharding wallets by owner id. The scheduled jobs then need a lock
  (ShedLock) or `SELECT ... FOR UPDATE SKIP LOCKED` so two instances never send the same transfer.
- **Transfers that take 1-3 business days.** Every transfer has its own status (QUEUED, SENT, PENDING_RETRY,
  SETTLED, REJECTED) and an estimated arrival date; the Transfers page polls every 5 seconds, so a settlement shows
  up by itself. In production the bank would call us back with webhooks (or files) instead of a settlement job.
- **5% rollout.** A canary deployment (Argo Rollouts or weighted gateway routes) sends 5% of traffic to the new
  version, watches error rate and payment latency, then continues or rolls back automatically.
- **Observability.** The demo already runs Prometheus (http://localhost:9090): it scrapes `/actuator/prometheus`
  of the three services and evaluates `infra/prometheus/alert-rules.yml` (frozen ledger, payment p95 > 200 ms,
  5xx error rate > 1% over 3 minutes, rejected payments, service down). Production adds Alertmanager to page on-call, Grafana dashboards and
  distributed tracing (Zipkin/Tempo).
- **Multi-region.** Every wallet has one home region with the only writable copy of its rows; other regions forward
  writes there and serve reads from replicas. A payment between wallets in two regions becomes a two-step transfer
  through clearing accounts, reconciled like the bank.

## 9. Testing

```bash
./mvnw verify                          # all tests + JaCoCo 80% check per module (needs Docker for Testcontainers)
./mvnw -pl ledger-service -am verify   # one module
cd frontend && npm ci && npm run build
bash scripts/smoke-test.sh             # the whole demo against the running Docker stack (OK / FAIL per step)
bash scripts/measure-payments.sh       # 200 payments through the gateway: p50 / p95 / p99
```

Payment latency, measured on a laptop with the Docker stack (`scripts/measure-payments.sh`: 200 payments one after
another through the gateway, after 20 warm-up calls): **p50 21.0 ms, p95 39.7 ms, p99 70.8 ms**, max 117.3 ms.
This is the time the client sees, including the gateway hop, so every payment stayed within the 200 ms target.

Integration tests run against a real PostgreSQL (Testcontainers); calls between services are faked with
`MockRestServiceServer` or a mock client bean.

What the key tests prove:
- `ConcurrencyTest`: 120 payments of 10.00 at the same moment from a wallet with 1,000.00 -> exactly 100 succeed,
  20 get INSUFFICIENT_FUNDS, the balance ends at 0.00 and the ledger is balanced. 100 opposite payments -> no deadlock.
- `LedgerDatabaseRulesTest`: UPDATE/DELETE on entries fails, an unbalanced transaction fails at COMMIT, a wallet
  cannot go below zero, even with plain SQL.
- `PaymentFlowTest`: happy path, insufficient funds, self payment, merchant type mismatch, idempotent replay.
- `BankTransferFlowTest`: bank offline -> retry with the same reference; ACK_LOST -> the bank pays once; rejection ->
  money returned; ledger down at settlement -> retried; frozen -> withdrawals wait.
- `AdminFlowTest`: 0.01 difference -> MISMATCHED, frozen, alert email, withdrawals blocked; unfreeze; sweep; reversals.
- `AuthFlowTest`: sign up (personal, merchant, existing address gets the same answer), single-use link, expired link,
  phone round trip. `GatewayTest`: routing, customer cannot call admin, internal paths blocked, rate limit, a slow
  service gives 503 after the timeout.

Line coverage (JaCoCo): wallet-common 98.4%, auth-service 98.2%, api-gateway 98.8%, ledger-service 96.6%,
banking-service 91%.

## 10. Assumptions

- One currency (EUR). Amounts have at most 2 decimals; more are refused, never rounded.
- The partner bank is simulated inside banking-service (`MockPartnerBank`, its own tables). Settlement takes a few
  seconds instead of a business day for simplicity
- Estimated arrival is the next business day (weekends skipped, public holidays not handled).
- Payments between users are instant and final; a manager can reverse a payment if the recipient still has the money.
- The routing number check is the US ABA checksum (the assignment talks about ACH).
- Sweep reserve limit: 1,000.00 EUR stays in the operating account (`wallet.ledger.sweep-reserve-limit`).
- Single instance of each service in the demo (jobs use plain `@Scheduled`).
- One partner bank (`wallet.ledger.sweep-partner-id=MOCK_PARTNER_BANK`): a sweep to any other `destinationPartnerId`
  is refused with 400.
