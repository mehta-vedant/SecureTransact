# SecureTransact — ML-Assisted Payment Risk Platform

A full-stack transaction processing and fraud detection platform with statistical anomaly detection, behavioral profiling, configurable risk policies, and human-in-the-loop risk case management. Built to simulate how modern banking systems handle payment risk at scale.

## Architecture

```
User submits transaction
        │
        ▼
┌──────────────────────────┐
│  Validation Layer        │  ── Ownership, account status, balance, frozen checks
│  (TransactionValidator)  │
└────────┬─────────────────┘
         │
         ▼
┌──────────────────────────┐
│  Risk Engine             │  ── Config-driven policy rules + behavioral profiling
│  (StatisticalRiskEngine) │     + blacklist checks + velocity/temporal anomalies
│                          │
│  ┌────────────────────┐  │
│  │ Policy Rules       │  │     LARGE_AMOUNT, NEW_ACCOUNT_LARGE_TXN,
│  │ (DB-configurable)  │  │     HIGH_VELOCITY, RAPID_TRANSFER, ODD_HOURS
│  ├────────────────────┤  │
│  │ Behavioral Profile │  │     Rolling mean/stddev per user, z-score anomaly,
│  │ (async, cached)    │     │     typical hours analysis
│  ├────────────────────┤  │
│  │ Blacklist Checks   │  │     Account number, email, IP, user ID
│  ├────────────────────┤  │
│  │ Velocity Anomaly   │  │     Transactions/hour, rapid-transfer patterns
│  ├────────────────────┤  │
│  │ Temporal Anomaly   │  │     Odd-hours detection (1-5 AM)
│  ├────────────────────┤  │
│  │ ML Assistant       │  │     Optional IsolationForest sidecar (/score):
│  │ (opt-in, soft)     │  │     folds a 0-100 anomaly score into the total,
│  │                    │  │     graceful 500ms-timeout fallback when down
│  └────────────────────┘  │
│                          │     Score 0-100 → risk level (LOW/MEDIUM/HIGH/CRITICAL)
└────────┬─────────────────┘
         │
         ▼
┌──────────────────────────┐
│  Decision Engine         │  ── Maps risk score + level to action
│  (RiskDecisionEngine)    │     ALLOW → settle immediately
│                          │     HOLD_FOR_REVIEW → create risk case, hold funds
│                          │     BLOCK → reject transaction
└────────┬─────────────────┘
         │
         ▼
┌──────────────────────────┐
│  Transaction Processor   │  ── Optimistic locking (version field)
│  (TransactionProcessor)  │     Idempotency keys prevent duplicate processing
│                          │     @Transactional with proper isolation levels
└────────┬─────────────────┘
         │
         ▼
┌──────────────────────────┐
│  Audit Trail             │  ── AOP-based (@Auditable annotation)
│  (AuditService)          │     Every mutation logged with who/what/when/IP
└────────┬─────────────────┘
         │
         ▼
┌──────────────────────────┐
│  PostgreSQL + Flyway     │  ── ACID-compliant persistence
│                          │     Versioned migrations (V1 - V5)
└──────────────────────────┘
```

## Risk Scoring

The engine uses a **weighted scoring** approach where each policy rule contributes configurable points. Risk evaluations are persisted per-transaction for full auditability.

| Rule | Triggers When | Default Points |
|------|--------------|----------------|
| **LARGE_AMOUNT** | Transaction amount exceeds threshold | +30 |
| **NEW_ACCOUNT_LARGE_TXN** | Account < 7 days old AND amount > threshold | +20 |
| **HIGH_VELOCITY** | > 5 transactions from same account in 1 minute | +25 |
| **RAPID_TRANSFER** | > 3 transfers to same destination in 10 minutes | +20 |
| **ODD_HOURS** | Transaction between 1:00 AM — 5:00 AM | +15 |
| **BLACKLISTED_SOURCE** | Source account on the blacklist | +50 |
| **BLACKLISTED_DEST** | Destination account on the blacklist | +50 |
| **UNUSUAL_AMOUNT** | Amount z-score > 3.0 vs user's historical mean | up to +30 |
| **UNUSUAL_HOUR** | Transaction at atypical time for this user | +15 |
| **HIGH_VELOCITY_ANOMALY** | > 10 transactions in last hour | up to +25 |

**Risk Levels:**
- 0–20 → LOW → Auto-approve
- 21–50 → MEDIUM → Approve, log evaluation
- 51–75 → HIGH → Flag for admin review
- 76–100 → CRITICAL → Hold for review / block

All rules are **database-configurable** via `FraudRuleConfig` — thresholds, weights, and enabled status can be changed at runtime without redeployment.

## Transaction Lifecycle

```
CREATED → RISK_EVALUATED → APPROVED → HELD_FOR_REVIEW → SETTLED
                        ↘                    ↘
                         → REJECTED           → FAILED
```

| Status | Meaning |
|--------|---------|
| `CREATED` | Transaction persisted, awaiting risk evaluation |
| `RISK_EVALUATED` | Risk engine scored the transaction |
| `APPROVED` | Low/medium risk — funds settled immediately |
| `HELD_FOR_REVIEW` | High risk — funds held, risk case created |
| `SETTLED` | Funds transferred (final success state) |
| `REJECTED` | Blocked by risk engine or admin |
| `FAILED` | Insufficient funds or processing error |

## Risk Case Management

When a transaction is flagged (HIGH or CRITICAL risk), a **Risk Case** is automatically created:

```
OPEN → IN_REVIEW → RESOLVED_FRAUD / RESOLVED_LEGITIMATE / DISMISSED
```

Admins can:
- View all risk cases with fraud scores and status
- Assign cases to themselves
- Make BLOCK or APPROVE decisions with review notes
- Full audit trail of every case action

## Audit Trail

Every mutation is captured via AOP (`@Auditable` annotation):

```java
@Auditable(action = AuditAction.TRANSACTION_CREATED, description = "New transaction")
public Transaction createTransaction(...) { ... }
```

Audited events include: account creation, status changes, transaction lifecycle, fraud reviews, login/logout. Each event records: who, what, when, resource type/id, IP address, and arbitrary JSON details.

## Python ML Service (risk-sensing sidecar)

A Flask + scikit-learn anomaly detector that folds an independent signal into the risk engine. **Opt-in and soft:** when the sidecar is up, its 0-100 anomaly score is blended into the statistical total (`ML_ANOMALY_FLAGGED` +20, `ML_ANOMALY_BLOCK_INDICATED` +35); when it's down/disabled, transactions score statistically only. Controlled via `APP_ML_ENABLED`, `APP_ML_BASE_URL`, `APP_ML_TIMEOUT_MS`.

| Component | Description |
|-----------|--------------|
| **IsolationForest** | Trained on synthetic transaction data (11-dim feature vector) |
| **Feature extraction** | Amount, hour/day, z-score, account age, 1h/24h counts, 7d avg, unique recipients, cross-border, new payee |
| **REST API** | `POST /score` → `{ riskScore: 0-100, decision, modelVersion }` |
| **Serving** | Flask on :5001, model trained via `python -m securetransact_ml.train_model` (run at least once) |

**Integration:** `RiskEngineService` collects features, calls `/score` with a 500ms timeout, blends the result, and persists `ml_probability` + the blended `model_version` on every `RiskEvaluation`. Any failure degrades silently to statistical-only — the ML service is never a single point of failure.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17, Python 3.12 |
| Backend | Spring Boot 3.2.5 |
| Security | Spring Security + JWT (BCrypt, 24hr tokens) |
| Database | PostgreSQL + Spring Data JPA |
| Migrations | Flyway 11.20.1 |
| ML | scikit-learn (IsolationForest), Flask |
| Frontend | React 18 + Vite + Tailwind CSS + Recharts |
| Testing | JUnit 5 + Mockito + H2 |
| API Docs | Swagger / OpenAPI 3.0 |

## API Reference

### Public
```
POST /api/auth/register    { firstName, lastName, email, password }  →  { token, email, role }
POST /api/auth/login       { email, password }                      →  { token, email, role }
```

### User (requires Bearer token)
```
POST   /api/accounts                     Create savings/checking account
GET    /api/accounts                     List my accounts
GET    /api/accounts/{id}                Account details + balance
GET    /api/accounts/{id}/statement      Transaction statement (date range filter)

POST   /api/transactions                 Submit deposit/withdrawal/transfer
GET    /api/transactions/{id}            Transaction status + risk score
GET    /api/transactions/history         Paginated transaction history
```

### Admin (requires ADMIN role)
```
GET    /api/admin/dashboard              Real-time metrics
GET    /api/admin/fraud/flagged          Flagged transactions (paginated)
PUT    /api/admin/fraud/{id}/review      Approve or reject flagged transaction
GET    /api/admin/accounts               All accounts (paginated)
```

### V1 Risk Platform API
```
GET    /api/v1/audit-events              Paginated audit log
GET    /api/v1/audit-events/by-action/{action}    Filter by action type
GET    /api/v1/audit-events/by-resource/{type}/{id}   Filter by resource

GET    /api/v1/admin/risk-cases          Paginated risk cases
GET    /api/v1/admin/risk-cases/{id}     Risk case detail
PATCH  /api/v1/admin/risk-cases/{id}/assign     Assign case to current admin
PATCH  /api/v1/admin/risk-cases/{id}/decision   Make BLOCK/APPROVE decision

GET    /api/v1/admin/fraud-rules         List all fraud rule configs
PUT    /api/v1/admin/fraud-rules/{id}    Update rule config (weight/threshold/enabled)

GET    /api/v1/admin/blacklist           List blacklist entries (filter: type, active)
POST   /api/v1/admin/blacklist           Add a blacklist entry (reactivates if inactive)
DELETE /api/v1/admin/blacklist/{id}      Deactivate a blacklist entry (soft delete)
```

### ML Service (Python, port 5001)
```
GET    /health                           Service health check
POST   /score                            Score a transaction for fraud risk
```

## Project Structure

```
SecureTransact/
├── backend/
│   └── src/main/java/com/securetransact/
│       ├── controller/v1/          Versioned REST endpoints
│       │   ├── TransactionControllerV1.java
│       │   ├── AdminRiskCaseControllerV1.java
│       │   ├── AuditEventControllerV1.java
│       │   ├── FraudRuleConfigControllerV1.java
│       │   └── AdminDashboardControllerV1.java
│       ├── service/
│       │   ├── TransactionService.java       Orchestrates validate→risk→settle
│       │   ├── TransactionValidator.java     Ownership, balance, status checks
│       │   ├── TransactionProcessor.java     Money movement with optimistic lock
│       │   ├── RiskCaseService.java          Case CRUD + assign/decide
│       │   ├── AuditService.java             Audit event recording + queries
│       │   └── AdminService.java             Dashboard metrics
│       ├── risk/
│       │   ├── RiskEngineService.java        Orchestrates all risk checks
│       │   ├── StatisticalRiskScoringService.java  Policy rules + behavioral + blacklist
│       │   ├── RiskDecisionEngine.java       Score → decision mapping
│       │   └── BehavioralProfileService.java Rolling stats per user (async) ───┐
│       ├── audit/
│       │   ├── Auditable.java                Method-level annotation
│       │   └── AuditAspect.java              AOP aspect, records every @Auditable call
│       ├── model/
│       │   ├── Transaction.java, Account.java, User.java
│       │   ├── FraudRuleConfig.java          DB-configurable risk rules
│       │   ├── UserBehaviorProfile.java      Per-user behavioral baseline
│       │   ├── FraudBlacklist.java           Blacklist entries
│       │   ├── RiskCase.java                 Case management entity
│       │   ├── RiskEvaluation.java           Per-transaction risk scores
│       │   └── AuditEvent.java               Immutable audit log entry
│       ├── repository/          Spring Data JPA repositories (10)
│       ├── dto/                 Request/response DTOs with validation
│       ├── security/            JWT auth, BCrypt, CustomUserDetails
│       ├── exception/           Global exception handler
│       └── config/              Security config, FraudRuleConfigSeeder
│
├── frontend/
│   └── src/
│       ├── pages/
│       │   ├── AdminDashboardPage.jsx      Admin panel with 6 tabs
│       │   └── ...
│       ├── components/admin/
│       │   ├── MetricsGrid.jsx             Dashboard metrics cards
│       │   ├── FlaggedTable.jsx            Flagged transactions table
│       │   ├── AllAccountsTable.jsx        All accounts view
│       │   ├── RiskCasesTable.jsx          Risk cases with assign/decide modals
│       │   └── AuditLogViewer.jsx          Audit timeline with filtering
│       └── services/api.js                API client (REST)
│
├── ml-service/
│   ├── securetransact_ml/
│   │   ├── risk_scoring.py        Flask API (POST /score, GET /health)
│   │   ├── train_model.py         IsolationForest training on synthetic data
│   │   └── features.py            11-dim feature extraction
│   ├── models/                    Saved model + scaler (joblib)
│   ├── Dockerfile
│   └── requirements.txt
│
└── docs/
    └── PROJECT_DOCUMENTATION.md
```

## Running Locally

**Prerequisites:** Java 17+, PostgreSQL, Maven, Node.js 18+, Python 3.12+

```bash
# 1. Create the database
psql -U postgres -c "CREATE DATABASE securetransact;"

# 2. Start the backend (includes Flyway migrations + seeders)
cd backend
mvn spring-boot:run
# → http://localhost:8080
# → Swagger UI: http://localhost:8080/swagger-ui.html

# 3. Start the frontend
cd frontend
npm install
npm run dev
# → http://localhost:5173

# 4. (Optional) Start the ML service
cd ml-service
pip install -r requirements.txt
python -m securetransact_ml.train_model    # one-time model training
python -m securetransact_ml.risk_scoring
# → http://localhost:5001

# 5. Run tests
cd backend
mvn test
```

## Design Decisions

**Config-driven risk rules over hardcoded rules.**
Rules are stored in `fraud_rule_configs` and evaluated at runtime. Thresholds and weights can be tuned via API without redeployment — essential for adapting to new fraud patterns.

**Java-first, single decision engine.**
The Java `StatisticalRiskScoringService` is the authoritative scoring path — fully testable, zero external runtime dependencies. The Python ML sidecar (`ml-service`) is an *optional soft signal*: wired into `RiskEngineService` with a 500ms timeout and silent degradation, so model experiments can be swapped without risking the transaction path.

**Behavioral profiling via streaming statistics.**
Instead of storing every transaction, `BehavioralProfileService` maintains a rolling mean and standard deviation using an online (Welford-style) update, asynchronously after each transaction. This gives z-score anomaly detection with O(1) memory per user.

**Risk cases for human-in-the-loop review.**
Automated scoring handles 90%+ of transactions. The remaining edge cases go to `RiskCaseService` where trained analysts make the final call — matching how real fraud operations teams work.

**AOP-based audit trail.**
`@Auditable` on service methods means audit logging is declarative, consistent, and impossible to forget. The aspect captures method arguments, return values, and Spring Security context automatically.

**Optimistic locking for concurrent safety.**
Pessimistic locking (SELECT FOR UPDATE) holds row locks and reduces throughput. Optimistic locking via `@Version` allows parallel reads and only fails on write conflicts — better for systems where most transactions don't touch the same account simultaneously.

## License

MIT
