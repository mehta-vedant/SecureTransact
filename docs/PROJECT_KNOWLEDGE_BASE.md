# SecureTransact Project Knowledge Base

This document is derived from the current codebase. It is the working map for future refactors and feature work. If code drifts, this file should be updated in the same change.

## Product Summary

SecureTransact is a full-stack payment & fraud-simulation platform: Spring Boot 3 (Java 17) + PostgreSQL backend with a React/Vite dashboard. It implements a statistical risk engine (config-driven policy rules, blacklists, async behavioral profiling), human-in-the-loop risk-case review, a traceable audit trail, and defense-in-depth security (JWT-in-cookie, CSRF, per-IP auth rate limiting, masked errors). An optional Python Flask sidecar provides an IsolationForest anomaly scorer — wired into the risk engine as an *opt-in soft signal* (500ms timeout, silent degradation).

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
- Blacklist types: `ACCOUNT_NUMBER`, `USER_ID`, `IP_ADDRESS`, `EMAIL` (+50 for source or destination account match). Admin API (`BlacklistControllerV1` + `BlacklistService`) manages entries: list (filtered by type/active), add-or-reactivate, soft-deactivate (`active=false`), each audited. Seeder `BlacklistSeeder` adds 4 demo entries when `app.admin.email` is set.
- Behavioral profile: online rolling mean/stddev + typical-hour bounds; `hasBaseline` gates z-score use (baseline after ≥5 transactions).

## Data Model Notes

- `Account.version` (BIGINT) is the optimistic-lock column for `TransactionProcessor`.
- `transactions.status` uses the enum `CREATED, RISK_EVALUATED, APPROVED, HELD_FOR_REVIEW, SETTLED, REJECTED, FAILED`. (No REVERSED — reversal is not implemented.)
- `risk_evaluations.ml_probability` + `model_version` hold the ML blend when the sidecar contributes (85.0 + statistical-risk-v1; `ML_ANOMALY_FLAGGED` +20 for ML≥50, `ML_ANOMALY_BLOCK_INDICATED` +35 for ML≥80, capped at 100).
- V3 migration drops legacy `fraud_logs`; V1 is byte-identical to the first release (never edit — Flyway checksum). V4 renames `fraud_blacklist.value` → `blacklist_value` (H2 reserved-word fix; Postgres index auto-updates). V5 adds `transactions.is_cross_border`.

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

35 tests / 7 suites — `mvn verify`.

**Unit (`mvn test`, H2, `ddl-auto: create-drop`, Flyway disabled):**
- `AuthControllerTest` (5)
- `StatisticalRiskScoringServiceTest` (7)
- `AuditServiceTest` (5)
- `RiskCaseServiceTest` (6)
- `RiskEngineServiceTest` (5)
- `RiskScoringClientTest` (3)

**Integration (failsafe phase, Testcontainers `postgres:16-alpine`, Flyway V1–V5, `ddl-auto: validate`):**
- `SecureTransactIntegrationIT` (4) — migrations, auto-settle, double-blacklist BLOCK, HOLD→approve→SETTLED,
  idempotent dedupe; skips when Docker is unavailable. Requires `docker-java.properties`
  (`api.version=1.44`) for Docker Engine v29 connectivity.

## Known Issues / Refactor Candidates

- **H2 reserved word `value`**: fixed by V4 column rename (`value` → `blacklist_value`; H2 now builds
  `fraud_blacklist` in tests — the DDL warning is gone).
- **`UserBehaviorProfile.transactions_last_hour` / `transactions_last_24h`**: now written by
  `BehavioralProfileService` after each transaction (latest refactor keeps them current); velocity risk
  still also computed live from `TransactionRepository`.
- **ML is disabled in tests** (`application-test.yml` sets `app.ml.enabled=false`). An integration
  test could containerize `ml-service`, but the Java-side contract is already covered by
  `RiskScoringClientTest` via `MockRestServiceServer`.
- **Real-data ML benchmark**: `ml-service/analysis/fraud_real_data_analysis.ipynb` (executed,
  run against UCI Credit Card Fraud, 284,807 rows) benchmarks IsolationForest vs supervised
  baselines incl. XGBoost and why it still justifies the label-free production model — see
  `docs/ML_MODEL_JUSTIFICATION.md`. Dataset (`data/creditcard.csv`) and exported `.pkl` are git-ignored.
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