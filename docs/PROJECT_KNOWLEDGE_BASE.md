# SecureTransact Project Knowledge Base

This document is derived from the current codebase. It is the working map for future refactors and feature work. If code drifts, this file should be updated in the same change.

## Product Summary

SecureTransact is a full-stack payment & fraud-simulation platform: Spring Boot 3 (Java 17) + PostgreSQL backend with a React/Vite dashboard. It implements a statistical risk engine (config-driven policy rules, blacklists, async behavioral profiling), human-in-the-loop risk-case review, a traceable audit trail, and defense-in-depth security (JWT-in-cookie, CSRF, per-IP auth rate limiting, masked errors). An optional Python Flask sidecar provides an IsolationForest anomaly scorer — kept intentionally out of the live decision path.

## Roles

- `USER`: register, login, profile/password, create/read accounts, submit transactions, view own history and statements.
- `ADMIN`: dashboard metrics, flagged transactions, approve/reject, risk-case management, audit log, rule config, all accounts.

Admin is seeded at startup if `ADMIN_EMAIL` + `ADMIN_PASSWORD` are set and the email doesn't exist.

## Key Workflows (current implementation)

### Transaction submission — `TransactionService.submitTransaction`
1. Idempotency check (unique `transactions.idempotency_key`).
2. `TransactionValidator`: per-type account checks, ownership, different-account, sufficient balance.
3. Persist as `CREATED`.
4. `RiskEngineService.evaluateTransaction`:
   - `StatisticalRiskScoringService` → score 0-100 (blacklist + policy rules + behavioral + velocity + temporal) with per-contribution `RiskFactor`s.
   - `RiskDecisionEngine` → `ALLOW` | `HOLD_FOR_REVIEW` | `BLOCK`.
   - `BehavioralProfileService.updateProfileAfterTransaction` (async) updates rolling stats.
5. Persist `RiskEvaluation` (score, level, decision, modelVersion, reasons, mlProbability).
6. Decision routing:
   - ALLOW → `TransactionProcessor.processMoneyMovement` (optimistic-lock retry x3) → SETTLED/FAILED
   - HOLD_FOR_REVIEW → HELD_FOR_REVIEW + `RiskCaseService.createRiskCase`
   - BLOCK → REJECTED
7. Audit events at each transition.

### Risk cases — `RiskCaseService`
- `OPEN` → `IN_REVIEW` on assign.
- Decision via `decideCase`:
  - ALLOW → case APPROVED, transaction **SETTLED** (funds actually move via `TransactionProcessor`)
  - BLOCK → case REJECTED, transaction REJECTED
  - HOLD_FOR_REVIEW → case ESCALATED, transaction stays HELD_FOR_REVIEW

### Admin review — `AdminService.reviewTransaction` (legacy flagged flow)
- Guards `HELD_FOR_REVIEW` only. `APPROVE` → `TransactionProcessor` → SETTLED/FAILED; `REJECT` → REJECTED. Audits the decision.

### Rule config — `FraudRuleConfigControllerV1`
- `GET` list (priority order) and `PUT /{id}` runtime updates (enabled, weight, priority, numeric/integer threshold, description). **No POST/DELETE** — rules are seeded (`FraudRuleConfigSeeder`) and edited, not created/destroyed via API.

## Scoring internals

- Bounded to 100. Levels: 0-20 LOW, 21-50 MEDIUM, 51-75 HIGH, 76+ CRITICAL.
- Seeded rules: LARGE_AMOUNT +30, NEW_ACCOUNT_LARGE_TXN +20, HIGH_VELOCITY +25, RAPID_TRANSFER +20, ODD_HOURS +15. Evaluation is a `switch` on `ruleName` against `FraudRuleConfig` columns — extending a rule touches both the switch and the seeder/documentation.
- Blacklist types: `ACCOUNT_NUMBER`, `USER_ID`, `IP_ADDRESS`, `EMAIL` (+50 for source or destination account match). There is **no API or seeder to add blacklist entries today** — wiring exists but governance is missing.
- Behavioral profile: online rolling mean/stddev + typical-hour bounds; `hasBaseline` gates z-score use (baseline after ≥5 transactions).

## Data Model Notes

- `Account.version` (BIGINT) is the optimistic-lock column for `TransactionProcessor`.
- `transactions.status` uses the enum `CREATED, RISK_EVALUATED, APPROVED, HELD_FOR_REVIEW, SETTLED, REJECTED, FAILED`. (No REVERSED — reversal is not implemented.)
- `risk_evaluations.ml_probability` is always null/absent today (ML sidecar not wired).
- V3 migration drops legacy `fraud_logs`; V1 is byte-identical to the first release (never edit — Flyway checksum).

## Repositories (9) & custom queries

`UserRepository`, `AccountRepository`, `TransactionRepository` (idempotency lookup, `findByUserId`,
recent-count/transfer-count windows, daily metrics), `FraudRuleConfigRepository`
(`findByEnabledTrueOrderByPriorityAsc`, `findAllByOrderByPriorityAsc`), `UserBehaviorProfileRepository`,
`FraudBlacklistRepository` (`existsByTypeAndValueAndActiveTrue`), `RiskEvaluationRepository`,
`RiskCaseRepository` (status filters, counts), `AuditEventRepository` (action/resource filters).

## Frontend

- Routes: `/`, `/login`, `/register`, `/dashboard` (user), `/admin` (admin), `*` 404.
- Admin tabs: Dashboard, Flagged, Risk Cases, Audit Log, All Accounts, Settings (placeholder).
- `services/api.js`: `credentials:'include'`, CSRF header on mutations, auto-logout on 401.
- Session hydration: `/api/csrf` then `/api/user/profile`.
- Dev proxy: `vite.config.js` → `http://localhost:8080`.

## Runtime Config

Backend env vars: `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`, `APP_JWT_SECRET` (required),
`APP_JWT_EXPIRATION_MS`, `APP_COOKIE_SECURE`, `APP_COOKIE_SAME_SITE`, `CORS_ALLOWED_ORIGINS`,
`AUTH_RATE_LIMIT_PER_MINUTE`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `APP_SWAGGER_ENABLED`, `PORT`.

Frontend: `VITE_API_URL` (optional in dev; proxied).

`start.ps1` loads `.env` before `spring-boot:run`. `.env` is git-ignored; `.env.example` shows the shape.

## Testing

22 tests / 4 suites, `mvn test` against H2 (`ddl-auto: create-drop`, Flyway disabled):

- `AuthControllerTest` (5)
- `StatisticalRiskScoringServiceTest` (6)
- `AuditServiceTest` (5)
- `RiskCaseServiceTest` (6)

## Known Issues / Refactor Candidates

- **H2 reserved word `value`**: `fraud_blacklist.value` fails DDL in the H2 test profile (logged as a
  non-fatal warning; the table is absent in tests). Tests pass only because none exercise
  `FraudBlacklistRepository` against H2. Fix options: rename column via a migration, or add a
  `@Column(name=...)` mapping and drop/`create` in test only. Notably `value` is fine on Postgres.
- **No blacklist mutation API**: `FraudBlacklistRepository` is wired into scoring but there is no
  add/remove endpoint, seeder, or seed data — an admin can't actually populate it.
- **`UserBehaviorProfile.transactions_last_hour` and `transactions_last_24h` are never written** by
  `BehavioralProfileService` (always 0) — velocity is computed live from `TransactionRepository`
  instead. Either compute-and-store or drop the columns (then add a V4 migration).
- **`mlProbability` is inert** — no caller ever computes the ML prediction. Keep the field for schema
  compatibility or remove it (cleanup candidate).
- **`UserController` returns raw `Map`s** instead of typed DTOs (minor contract smell).
- **Rate limiting is in-memory** — fine single-instance; distributed deployments need Redis/etc.
- **v1 controllers lack integration tests** — only services are unit-tested.
- **Time-sensitive rules** call `LocalDateTime.now()`/`LocalTime.now()` directly inside scoring,
  which impedes deterministic tests for temporal/velocity rules.
- **Two review surfaces coexist**: legacy `/api/admin/fraud/{id}/review` AND
  `/api/v1/admin/risk-cases/{id}/decision` both resolve HELD_FOR_REVIEW transactions. Functionally
  consistent now (both settle on approval) but could be unified.
- **Frontend admin pages** (e.g., `AdminDashboardPage.jsx`) are large single components with inline
  styles; extracting layout primitives would ease maintenance.