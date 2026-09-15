# SecureTransact — Complete Project Documentation

> A full-stack payment & fraud platform: Spring Boot 3 (Java 17) + PostgreSQL backend, React/Vite frontend, and an optional Python anomaly-detection sidecar. Core engineering focus: statistical risk scoring, config-driven policy rules, human-in-the-loop review, auditability, and defense-in-depth security.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Tech Stack](#2-tech-stack)
3. [Backend Structure](#3-backend-structure)
4. [API Reference](#4-api-reference)
5. [Data Model](#5-data-model)
6. [Risk Engine](#6-risk-engine)
7. [Transaction & Risk-Case Lifecycle](#7-transaction--risk-case-lifecycle)
8. [Security Architecture](#8-security-architecture)
9. [Frontend](#9-frontend)
10. [Configuration & Deployment](#10-configuration--deployment)
11. [Testing](#11-testing)
12. [Key Design Decisions](#12-key-design-decisions)

---

## 1. Architecture Overview

```
[ React (Vite) ]  ──REST/JSON──▶  [ Spring Boot API :8080 ]  ──▶  [ PostgreSQL ]
   (localhost:5173)        cookies   ─┬─ JWT auth (httpOnly cookie)     Flyway V1-V3
   │                                  ├─ CSRF protection
   │                                  ├─ per-IP auth rate limiting
   │                                  └─ Risk pipeline (below)

Transaction submission flow:
  Validation (owner/type/balance/status)
        → persist as CREATED
        → Risk engine (statistical rules + blacklist + behavioral profile)
        → RiskEvaluation persisted (score, level, decision, factors)
        → ALLOW        → TransactionProcessor settles funds (SETTLED)
        → HOLD_REVIEW  → HELD_FOR_REVIEW + risk case created
        → BLOCK        → REJECTED

Optional soft-signal sidecar:
  [ Python Flask ML :5001 ]  IsolationForest anomaly scorer — wired in, 500ms timeout,
                             silent-degrades to statistical-only when down
```

## 2. Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.2.5, Spring Security, Spring Data JPA, Bean Validation |
| Database | PostgreSQL, Flyway 11.x (V1–V5), `ddl-auto: validate` |
| Auth | JWT (JJWT 0.12.x), BCrypt, httpOnly cookie + Bearer fallback |
| API docs | SpringDoc OpenAPI `/swagger-ui.html` (opt-in flag) |
| Frontend | React 18, Vite, React Router, Tailwind, Framer Motion, Recharts, Lucide |
| ML sidecar | Python 3.12, Flask, scikit-learn (IsolationForest) |
| Testing | JUnit 5, Mockito, H2 (create-drop test profile) |

## 3. Backend Structure

```
com.securetransact
├── controller/            REST endpoints
│   ├── AuthController        register / login / logout
│   ├── AccountController     accounts + statements
│   ├── TransactionController submit / detail / history
│   ├── UserController        profile / change-password
│   ├── AdminController       dashboard / flagged / review / accounts
│   ├── CsrfController        XSRF token endpoint
│   └── v1/                   versioned risk-platform API
│       ├── AdminRiskCaseControllerV1    risk case list/detail/assign/decide
│       ├── AuditEventControllerV1       audit log + filters
│       ├── FraudRuleConfigControllerV1  rule list + runtime update
│       ├── AdminDashboardControllerV1   v1 metrics / accounts / audit
│       └── TransactionControllerV1      v1 transaction create/detail/list
├── service/                business workflows
│   ├── TransactionService     validate → risk → settle (orchestrator)
│   ├── TransactionValidator   ownership / type / balance / status checks
│   ├── TransactionProcessor   money movement, optimistic-lock retry (3x)
│   ├── AccountService         account create / read
│   ├── AdminService           metrics, flagged, review, audit queries
│   ├── RiskCaseService        risk case lifecycle (assign / decide)
│   └── AuditService           audit event persistence + queries
├── risk/                   risk pipeline
│   ├── RiskEngineService          entry point, ties scoring + decision + profiles
│   ├── StatisticalRiskScoringService  policy rules + blacklist + behavioral + velocity/temporal
│   ├── RiskDecisionEngine         score/level → ALLOW | HOLD_FOR_REVIEW | BLOCK
│   └── BehavioralProfileService   async rolling mean/stddev per user (Welford-style)
├── audit/                  AOP auditing
│   ├── Auditable               method-level annotation
│   └── AuditAspect             records actor / action / resource / IP per @Auditable call
├── model/                  9 JPA entities + 10 enums (see §5)
├── repository/             9 Spring Data JPA repositories (incl. custom JPQL)
├── dto/                    19 request/response types with Bean Validation
├── security/               JwtTokenProvider, JwtAuthenticationFilter, AuthRateLimitFilter,
│                           AuthCookieHelper, CustomUserDetails(+Service)
├── exception/              5 domain exceptions + GlobalExceptionHandler (masked messages)
└── config/                 SecurityConfig, AdminSeeder, FraudRuleConfigSeeder
```

## 4. API Reference

### Public / session
| Method | Endpoint | Notes |
|---|---|---|
| GET  | `/api/csrf` | Issues `XSRF-TOKEN` cookie |
| POST | `/api/auth/register` | `{ firstName, lastName, email, password }` → 201 + `AUTH_TOKEN` cookie |
| POST | `/api/auth/login` | `{ email, password }` → 200 + `AUTH_TOKEN` cookie |
| POST | `/api/auth/logout` | Clears `AUTH_TOKEN` cookie |

CSRF-exempt: `/api/auth/**`. Token is never returned in the body (XSS hardening).

### User (authenticated)
| Method | Endpoint | Notes |
|---|---|---|
| POST | `/api/accounts` | Create SAVINGS/CHECKING with optional deposit |
| GET  | `/api/accounts` | Own accounts |
| GET  | `/api/accounts/lookup?accountNumber=` | Minimal holder lookup (no balance) |
| GET  | `/api/accounts/{id}` | Own account detail |
| GET  | `/api/accounts/{id}/statement?start=&end=` | Date-range statement |
| POST | `/api/transactions` | DEPOSIT / WITHDRAWAL / TRANSFER (idempotency key optional) |
| GET  | `/api/transactions/{id}` | Owner or admin |
| GET  | `/api/transactions/history` | Paginated own history |
| GET  | `/api/user/profile` | Profile |
| PUT  | `/api/user/profile` | Update name |
| PUT  | `/api/user/change-password` | Requires current password |

### Admin (ROLE_ADMIN)
| Method | Endpoint | Notes |
|---|---|---|
| GET  | `/api/admin/dashboard` | Today metrics + active accounts |
| GET  | `/api/admin/fraud/flagged` | Paginated HELD_FOR_REVIEW transactions |
| PUT  | `/api/admin/fraud/{id}/review` | `{ decision: "APPROVE" \| "REJECT" }`; APPROVE settles funds |
| GET  | `/api/admin/accounts` | Paginated all accounts |

### v1 Risk-platform API (ROLE_ADMIN under `/api/v1/admin/**`)
| Method | Endpoint | Notes |
|---|---|---|
| GET    | `/api/v1/admin/risk-cases` | Open/in-review/escalated cases, paginated |
| GET    | `/api/v1/admin/risk-cases/{id}` | Case detail |
| PATCH  | `/api/v1/admin/risk-cases/{id}/assign` | Assign to current admin → IN_REVIEW |
| PATCH  | `/api/v1/admin/risk-cases/{id}/decision` | BLOCK / ALLOW / HOLD_FOR_REVIEW + notes |
| GET    | `/api/v1/admin/fraud-rules` | All rule configs |
| PUT    | `/api/v1/admin/fraud-rules/{id}` | Toggle/weight/threshold update at runtime |
| GET    | `/api/v1/admin/blacklist` | Blacklist entries (filter: `type`, `active`) |
| POST   | `/api/v1/admin/blacklist` | Add/re-activate a blacklist entry |
| DELETE | `/api/v1/admin/blacklist/{id}` | Deactivate a blacklist entry |
| GET    | `/api/v1/admin/audit-events` | Paginated audit log |
| GET    | `/api/v1/admin/audit-events/by-action/{action}` | Filter by action |
| GET    | `/api/v1/admin/audit-events/by-resource/{type}/{id}` | Filter by resource |
| GET    | `/api/v1/admin/dashboard` | Metrics |
| GET    | `/api/v1/admin/accounts` | Paginated accounts |
| POST   | `/api/v1/transactions` | Create transaction |
| GET    | `/api/v1/transactions`, `/api/v1/transactions/{id}` | List / detail |

## 5. Data Model

### Entities (9)
| Entity | Table | Purpose |
|---|---|---|
| User | `users` | Accounts, role USER/ADMIN, BCrypt password |
| Account | `accounts` | `account_number` unique, `@Version` optimistic lock, status |
| Transaction | `transactions` | Deposit/withdrawal/transfer, status machine, idempotency key |
| FraudRuleConfig | `fraud_rule_configs` | DB-configurable rules (name, weight, priority, thresholds, enabled) |
| UserBehaviorProfile | `user_behavior_profiles` | Rolling avg/stddev, 24h & hourly velocity, typical hours |
| FraudBlacklist | `fraud_blacklist` | ACCOUNT_NUMBER / USER_ID / IP_ADDRESS / EMAIL entries |
| RiskEvaluation | `risk_evaluations` | Per-transaction score, level, decision, model version, factors |
| RiskCase | `risk_cases` | Human review: OPEN → IN_REVIEW → APPROVED/REJECTED/ESCALATED |
| AuditEvent | `audit_events` | Immutable audit trail (`@Auditable`) |

### Enums (10)
`Role`, `AccountType`, `AccountStatus` (ACTIVE/FROZEN/CLOSED), `TransactionType`,
`TransactionStatus` (CREATED, RISK_EVALUATED, APPROVED, HELD_FOR_REVIEW, SETTLED, REJECTED, FAILED),
`RiskLevel` (LOW/MEDIUM/HIGH/CRITICAL), `RiskDecision` (ALLOW/HOLD_FOR_REVIEW/BLOCK),
`CaseStatus`, `AuditAction`, `BlacklistType`.

### Migrations
- **V1** — core schema (users, accounts, transactions, fraud_logs). *Unchanged since first release.*
- **V2** — risk platform (rules, behavior profiles, blacklist, evaluations, cases, audit).
- **V3** — drops legacy `fraud_logs` (old rule engine removed).

## 6. Risk Engine

Score is bounded to 0–100; every contribution produces a persistable `RiskFactor` (code/points/message).

| Signal | Implementation | Notes |
|---|---|---|
| Blacklist | `fraud_blacklist` lookups on source & destination account | +50 each |
| Policy rules | DB rows from `fraud_rule_configs` (enabled, weight, priority) | Switched by `ruleName` |
| Behavioral anomaly | z-score vs rolling mean/stddev (threshold 3.0), unusual-hour | Needs a baseline (`hasBaseline`) |
| Velocity anomaly | >10 txns/hour from account | up to +25 |
| Temporal anomaly | 1:00–5:00 window | +15 |

Seeded rules: `LARGE_AMOUNT` (+30), `NEW_ACCOUNT_LARGE_TXN` (+20), `HIGH_VELOCITY` (+25),
`RAPID_TRANSFER` (+20), `ODD_HOURS` (+15). All weights/thresholds tuneable via `PUT /api/v1/admin/fraud-rules/{id}`.

Decision mapping (score → level → decision):

| Score | Level | Decision |
|---|---|---|
| 0–20 | LOW | ALLOW (auto-settle) |
| 21–50 | MEDIUM | ALLOW (log evaluation) |
| 51–75 | HIGH | HOLD_FOR_REVIEW (risk case) |
| 76–100 | CRITICAL | HOLD_FOR_REVIEW / BLOCK |

Behavioral profiles update **asynchronously** (`@Async`) after each transaction using an
online (Welford-style) variance update — O(1) memory per user.

## 7. Transaction & Risk-Case Lifecycle

```
CREATED → [risk eval] → ALLOW  → SETTLED
                      → HOLD   → HELD_FOR_REVIEW  (risk case created, funds held)
                      → BLOCK  → REJECTED

HELD_FOR_REVIEW → admin review
                → APPROVE → SETTLED (funds moved) | FAILED
                → REJECT  → REJECTED

Risk case:  OPEN → (assign) → IN_REVIEW
            IN_REVIEW → ALLOW   → risk APPROVED + transaction SETTLED
                      → BLOCK   → risk REJECTED + transaction REJECTED
                      → HOLD    → risk ESCALATED (transaction stays HELD)
```

`TransactionProcessor` performs money movement with up to 3 retries on optimistic-lock conflicts;
insufficient balance at settlement time yields FAILED. All status transitions emit audit events.

## 8. Security Architecture

1. **Authentication** — `/register` & `/login` authenticate via Spring Security and issue a JWT
   signed with `APP_JWT_SECRET` (required, fail-fast). Token is set as an httpOnly `AUTH_TOKEN`
   cookie (`Secure`, `SameSite`, 24h) — never exposed in JSON bodies.
2. **Request filter** — `JwtAuthenticationFilter` reads the cookie (or `Authorization: Bearer`
   for Swagger), validates signature/expiry, loads the user, and populates the SecurityContext.
3. **CSRF** — `CookieCsrfTokenRepository` (HttpOnly=false) + `X-XSRF-TOKEN` header on mutations;
   `/api/auth/**` exempt. SPA fetches `/api/csrf` first.
4. **Rate limiting** — `AuthRateLimitFilter`: sliding window per IP on login/register (default 10/min).
5. **Authorization** — `/api/admin/**` and `/api/v1/admin/**` require ROLE_ADMIN; everything else
   requires authentication. `@EnableMethodSecurity` enabled.
6. **Error masking** — `GlobalExceptionHandler` returns generic `401/403/500` messages; no stack
   traces or internals leak to clients.
7. **Passwords** — BCrypt; server-side policy (length + uppercase/lowercase/digit).
8. **CORS** — configurable origins, credentials allowed, `XSRF-TOKEN` header permitted.
9. **Secrets** — every secret is an env var (`.env` loaded by `start.ps1`, git-ignored). Missing
   required vars fail startup rather than run degraded.

## 9. Frontend

Routes: `/` (landing), `/login`, `/register`, `/dashboard` (user), `/admin` (admin), `*` (404).

- **User dashboard**: balance overview, account cards, create-account and transaction modals,
  paginated history, statements, profile/password settings.
- **Admin dashboard** (6 tabs): Dashboard (metrics + flagged), Flagged, Risk Cases, Audit Log,
  All Accounts, Settings (placeholder).
- **API client**: `credentials: 'include'`, attaches `X-XSRF-TOKEN` on mutations, auto-logout on 401.
- **Design system**: CSS variables + dark mode, Framer Motion transitions, mobile-responsive
  layouts (hamburger sidebar, bottom-sheet modals, mobile card tables).

## 10. Configuration & Deployment

Environment variables (see `application.yml`): `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`,
`APP_JWT_SECRET` (required), `APP_JWT_EXPIRATION_MS`, `APP_COOKIE_SECURE`, `APP_COOKIE_SAME_SITE`,
`CORS_ALLOWED_ORIGINS`, `AUTH_RATE_LIMIT_PER_MINUTE`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`,
`APP_SWAGGER_ENABLED`, `PORT`. Frontend: `VITE_API_URL` (dev proxy → :8080).

Run: `psql -c "CREATE DATABASE securetransact"` → `cd backend && mvn spring-boot:run`
→ `cd frontend && npm install && npm run dev`. Optional ML sidecar:
`cd ml-service && pip install -r requirements.txt && python -m securetransact_ml.train_model`
then `python -m securetransact_ml.risk_scoring` (:5001). When it's up, the risk engine
blends its anomaly score in automatically (`app.ml.enabled=true` by default; disable or
point elsewhere via `APP_ML_ENABLED` / `APP_ML_BASE_URL` / `APP_ML_TIMEOUT_MS`).

## 11. Testing

35 tests / 7 suites — `mvn verify` (31 unit on H2 + 4 integration on Testcontainers Postgres 16):

**Unit (`mvn test`, H2, Flyway disabled):**
- `AuthControllerTest` (5) — register duplicate/login/invalid flows
- `StatisticalRiskScoringServiceTest` (7) — policy rules, blacklist, velocity, temporal
- `AuditServiceTest` (5) — audit recording + filtering
- `RiskCaseServiceTest` (6) — create/assign/approve(sets transaction to SETTLED)/block/escalate/not-found
- `RiskEngineServiceTest` (5) — ML boost blend, cap, fallback, and no-double-count (ML disabled)
- `RiskScoringClientTest` (3) — HTTP contract via `MockRestServiceServer` (200 / 5xx / disabled)

**Integration (`mvn verify` failsafe phase, Testcontainers `postgres:16-alpine`, Flyway V1–V5, `ddl-auto: validate`):**
- `SecureTransactIntegrationIT` (4) — full pipeline against real Postgres: V1–V5 migrations apply,
  auto-settle low risk, double-blacklist → BLOCK/REJECTED, HOLD → assign → approve → SETTLED,
  idempotent dedupe. Container is skipped gracefully when Docker is unavailable
  (`@Testcontainers(disabledWithoutDocker = true)`).
- Docker Engine v29 requires `docker-java.properties` (`api.version=1.44`) so Testcontainers can
  negotiate the engine API.

## 12. Key Design Decisions

- **Config-driven rules over hardcoded rules** — fraud rules live in the DB and can be tuned at
  runtime (no redeploy), matching how real Ops teams adapt to new fraud patterns.
- **Self-contained Java decision engine** — `StatisticalRiskScoringService` is the authoritative
  scoring path (no external dependency). The Python ML sidecar is an *optional soft signal*:
  wired in with a 500ms timeout, blended into the total, and silent-degraded on any failure.
- **Streaming behavior profiles** — online mean/variance per user (O(1) memory) rather than
  storing all historical transactions.
- **Human-in-the-loop risk cases** — automation handles low/medium risk; edge cases reach analysts
  who decide with full audit coverage.
- **AOP audit trail** — declarative `@Auditable`, impossible to forget; captures who/what/when/IP.
- **Optimistic locking + retry** — parallel-friendly settlement, retries transient conflicts.
- **Defense-in-depth** — httpOnly cookie JWT, per-IP rate limiting, CSRF tokens, masked errors,
  fail-fast secret validation.