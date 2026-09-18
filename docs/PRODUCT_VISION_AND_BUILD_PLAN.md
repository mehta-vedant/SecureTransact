# SecureTransact Product Vision And Build Plan

## 1. Product Decision

SecureTransact is **not** a consumer banking application and it should not claim to
be an autonomous AI fraud detector.

It is a **fraud-operations control plane for account-to-account payment platforms**.
It helps a fintech or payment operations team decide whether a payment should be
approved, held for review, or declined; investigate held payments; and leave an
auditable decision record.

This is a focused, credible portfolio product. It deliberately avoids pretending to
be a core banking system, card network, AML platform, or production fraud model.

## 2. The Problem We Solve

A payment platform must make a decision in seconds, but not every suspicious payment
should be automatically declined. False positives hurt customers and revenue; missed
fraud causes direct loss. Operations teams therefore need:

1. explainable, low-latency triage;
2. a queue for the ambiguous cases;
3. sufficient context to make a defensible decision; and
4. a tamper-evident record of what happened and why.

**Product outcome:** reduce risky payment exposure while making analyst review faster
and measurable.

## 3. Primary User And Core Story

### Primary user

Fraud analyst at a mid-market fintech that supports account-to-account transfers.

### Secondary users

- Fraud operations lead: monitors queue health, rule effectiveness, and analyst SLA.
- Payment integration/service: submits payment events and receives a decision.

### One end-to-end story

1. A payment service submits a payment authorisation event.
2. SecureTransact derives trusted context, evaluates deterministic policy and anomaly
   signals, and returns `APPROVE`, `HOLD`, or `DECLINE`.
3. A `HOLD` automatically becomes an owned investigation case.
4. An analyst sees the payment, risk reasons, history, related customer/payee signals,
   and a chronological event timeline.
5. The analyst approves, declines, or escalates the payment and can freeze an account
   or beneficiary where warranted.
6. The decision, reasoning, actor, and money movement are immutable and reportable.

If a feature does not strengthen this story, defer it.

## 3A. Automation Boundary: What The System Does Versus The Analyst

SecureTransact uses **risk-based automation**, not fully autonomous fraud decisions.
The system automates repeatable, low-ambiguity decisions and sends ambiguous or
high-impact decisions to a human. This is realistic because fraud signals are noisy:
declining a legitimate payment harms a customer, while approving a fraudulent payment
creates financial loss.

| Decision stage | Automated? | System action | Human responsibility | Why |
|---|---:|---|---|---|
| Validate request, account state, amount, idempotency | Yes | Reject invalid/duplicate requests before risk scoring | None | Deterministic correctness rule. |
| Gather payment context | Yes | Derive server IP/device, history, beneficiary age, velocity and geography | None | Repeatable data enrichment; do not trust browser claims. |
| Evaluate rules and anomaly signal | Yes | Calculate score and persist factor-level explanation | None | Fast, consistent triage must happen before payment settlement. |
| Very low-risk payment | Yes | `APPROVE` then settle automatically | Monitor aggregate outcomes | Avoid needless delay for normal customers. |
| Confirmed prohibited/known-bad payment | Yes | `DECLINE` automatically, create audit record and optional alert | Review later only if needed | A direct blacklist hit or invalid account is unambiguous policy enforcement. |
| Medium or conflicting signals | Yes, routing only | `HOLD`; reserve it from settlement and create a priority case | Investigate and decide | This is the important human-in-the-loop zone. |
| High-value/high-impact ambiguous payment | Yes, routing only | `HOLD` or escalate, never auto-settle based solely on a model | Approve, decline, or escalate with notes | Financial impact and false-positive cost justify accountable review. |
| Freeze payer/beneficiary | No, except an emergency policy option | Present recommended action and require confirmation | Freeze/unfreeze with reason | Freezing affects a customer and must be attributable. |
| Rule/threshold change | No | Show estimated impact and keep version history | Authorise change with reason/approval | Uncontrolled tuning can cause systemic false positives. |
| Model retraining/deployment | No | Record model version and score contribution | Validate, approve and deploy model version | A demo must not claim self-learning production ML. |

### Decision policy for the MVP

```text
Hard policy failure / known-bad entity       -> DECLINE automatically
Score below auto-approve threshold           -> APPROVE and settle automatically
Score in review band                         -> HOLD and create a case
Score above decline threshold, but explainable
  hard signal exists                         -> DECLINE automatically
Score above decline threshold driven mainly
  by anomaly/uncertain signals               -> HOLD with critical priority
```

The last rule is important: an anomaly score should raise urgency, not act as the sole
reason to decline a customer. The case workspace must display the rule contributions,
observed data, and anomaly contribution so the analyst can explain a final decision.

### Example routing

- **$25 everyday transfer to an established beneficiary:** low score, automatically
  approved and settled.
- **Payment to a beneficiary on the internal blocklist:** automatically declined;
  no human needs to review a direct policy match.
- **First $8,000 transfer to a new beneficiary at 02:00 after unusual device activity:**
  held, marked high priority, and assigned to an analyst. The system should not pretend
  it knows the customer is committing fraud.
- **$50,000 payment with only an anomalous amount signal:** held for analyst review;
  a high amount alone is not proof of fraud.
- **Analyst confirms account takeover using the timeline:** declines payment, freezes
  payer/beneficiary as appropriate, and records the disposition and notes.

## 4. What To Keep, Change, And Stop

| Current capability | Decision | Reason |
|---|---|---|
| Configurable rules, velocity, behavioural baseline | Keep | These are explainable fraud signals. |
| Risk scores and `ALLOW/HOLD/BLOCK` routing | Keep, rename decisions | This is the core control loop. Use `APPROVE/HOLD/DECLINE` in product language. |
| Risk cases, assignment, notes, audit events | Expand | This is the strongest differentiator. |
| Demo simulator and deterministic scenarios | Keep, reshape | It is the best way to demonstrate a realistic workflow. |
| Consumer registration and account dashboard | De-emphasise | It distracts from the B2B fraud-operations product. Retain only as a sandbox actor setup. |
| User-created opening balance/deposit flow | Remove from main story | It creates money without a funding source and weakens credibility. |
| Generic transaction form | Replace | Payment events need recipient, rail, currency, geography, channel, and trusted device/network context. |
| IsolationForest as the headline feature | Reposition | Treat it as an optional anomaly signal, not a fraud verdict. |
| Duplicate legacy and v1 APIs | Consolidate | One versioned public API removes ambiguity. |

## 5. Deliberately Lean Product Scope (MVP)

### In scope

- One payment rail: domestic account-to-account transfer.
- Payment authorisation decision in a synchronous API call.
- Explainable rules plus an optional anomaly score.
- `APPROVED`, `HELD`, `DECLINED`, `SETTLED`, `REVERSED` lifecycle.
- Human investigation queue, assignment, notes, decision and escalation.
- Account/beneficiary freeze action.
- Append-only ledger entries for settled/reversed movement.
- Operational metrics and a seeded replay demo.

### Explicitly out of scope

- Real cards, PCI, KYC verification, real money movement, bank integrations.
- AML/SAR filing, chargebacks, sanctions screening, and multi-currency FX.
- Autonomous model retraining or claims of production accuracy.
- Microservices, Kafka, Redis, or event sourcing solely for appearance.

## 6. Product Language And State Model

Use language that an operations team would recognise.

```text
Payment submitted
  -> RISK_EVALUATED
       -> APPROVED -> SETTLED
       -> HELD -> UNDER_REVIEW -> APPROVED -> SETTLED
                              -> DECLINED
                              -> ESCALATED
       -> DECLINED

SETTLED -> REVERSED (only by an explicit reversal operation)
```

Separate a **risk decision** (`APPROVE`, `HOLD`, `DECLINE`) from a **payment status**.
This prevents a reviewer decision from being mistaken for settlement.

## 7. Lean Domain Model

Do not rebuild the whole application. Evolve these existing entities.

### Payment

Rename `Transaction` in product/API language to `Payment` when practical. Add:

- immutable `paymentReference` and idempotency key scoped to tenant/client;
- `currency`, `rail`, `channel`, `merchantReference` (optional);
- payer account and beneficiary/payee;
- server-captured IP, device ID/fingerprint, and country metadata;
- decision/status timestamps and an immutable amount.

### Beneficiary

A first-class beneficiary/payee has account number, display name, country, creation
time, risk/freeze status, and optional tags. This makes `new payee`, beneficiary
velocity, and blacklisting real product concepts rather than form fields.

### LedgerEntry

Add an append-only `ledger_entries` table:

- `payment_id`, `account_id`, `direction` (`DEBIT`/`CREDIT`), `amount`, `currency`,
  `entry_type` (`SETTLEMENT`/`REVERSAL`), `created_at`.
- A settled payment creates balanced debit/credit rows in one database transaction.
- A reversal adds compensating entries; it never edits previous entries.

`Account.balance` may remain a cached read model, but ledger entries are the source
of truth for financial movement in this demo.

### RiskCase

Add priority, queue/assigned timestamps, disposition reason, escalation reason, and
an optimistic-lock version. Keep review notes, but make a separate append-only
`case_events` timeline for assignment, notes, decisions, and freezes.

## 8. Risk Design: Explainable First

### Trusted inputs

The server, not the browser, must capture IP/user agent and derive as much context as
possible. `crossBorder` must be derived from payer and beneficiary country values.

### Initial rules

- known blocked payer/beneficiary/device/IP;
- new beneficiary plus high amount;
- amount materially outside payer baseline;
- rapid repeated transfers to one beneficiary;
- sudden increase in distinct beneficiaries;
- high transfer velocity;
- unusual hour for the payer;
- country or channel inconsistency.

Every rule result must persist `{ruleCode, points, explanation, observedValue,
threshold}` structurally, rather than as a concatenated string.

### Model policy

Keep the in-process anomaly scorer as a bounded optional signal. Label it
"anomaly signal" in the UI. Show its version and contribution. Never present
synthetic-data IsolationForest output as a measured fraud probability.

## 9. User Experience: Three Screens Only

1. **Operations overview** — queue count by priority, aged cases, held/declined value,
   decision rates, and rule hit-rate trend.
2. **Review queue** — filters for status, priority, score, age, analyst, rule, and
   amount; bulk assignment is optional later.
3. **Case workspace** — payment summary, decision explanation, payer/beneficiary
   history, event timeline, notes, assign/escalate/approve/decline/freeze actions.

The existing customer dashboard becomes a minimal sandbox/payment-submission view.

## 10. API Shape

Use `/api/v1` only for new work. Preserve legacy endpoints temporarily, then remove
them after frontend migration.

```text
POST  /api/v1/payments                         submit authorisation request
GET   /api/v1/payments/{paymentReference}      read payment + decision
POST  /api/v1/payments/{id}/reverse            reverse a settled payment

GET   /api/v1/admin/cases                      filterable work queue
GET   /api/v1/admin/cases/{id}                 full evidence/workspace data
PATCH /api/v1/admin/cases/{id}/assign
POST  /api/v1/admin/cases/{id}/notes
POST  /api/v1/admin/cases/{id}/decision
POST  /api/v1/admin/accounts/{id}/freeze
POST  /api/v1/admin/beneficiaries/{id}/freeze

GET   /api/v1/admin/metrics/operations
```

Require `Idempotency-Key` for payment submission. Scope it by tenant/client and
request route; return the original response for a duplicate.

## 11. Delivery Plan

### Phase 0 — Align and clean (short, mandatory)

- Adopt this product statement in README/project docs.
- Fix the documented frontend unmount bug and CSRF/documentation header mismatch.
- Make transaction request validation class-level and enforce receiver status.
- Make idempotency mandatory and scope it correctly.
- Consolidate the frontend onto `/api/v1`; mark legacy endpoints deprecated.

**Exit criterion:** the existing flow is correct, documented, and has one API path.

### Phase 1 — Credible payment core

- Introduce payment lifecycle/state policy.
- Add `paymentReference`, beneficiary, currency/rail/channel, and trusted context.
- Add balanced, append-only ledger entries and reversal operation.
- Rework settlement to be atomic and concurrency-safe; test concurrent debit attempts.

**Exit criterion:** every settled or reversed payment has balanced immutable entries;
no balance is changed outside the ledger posting service.

### Phase 2 — Analyst workspace

- Add case priority, event timeline, ownership and optimistic locking.
- Build a full case-detail endpoint with evidence/rule contributions.
- Replace the current modal with a dedicated case workspace.
- Add decision reason, mandatory notes for decline/escalation, and freeze actions.

**Exit criterion:** an analyst can resolve a held payment without leaving the case page,
and a reviewer can reconstruct why any decision occurred.

### Phase 3 — Operate and demonstrate

- Add dashboard metrics: held value, decline rate, queue age, SLA breaches, decision
  turnaround, rule-hit rate, and analyst dispositions.
- Seed four narrative demo scenarios: mule beneficiary, account takeover, legitimate
  high-value payment, and repeat known-bad beneficiary.
- Add a replay control that shows each decision and routes to its case.
- Include screenshots, architecture diagram, and a 3-minute demo script.

**Exit criterion:** a reviewer can understand the product, trigger a scenario, resolve
a case, and see the operational impact in one short demonstration.

### Phase 4 — Quality, not feature sprawl

- Controller/API integration tests for every payment and case state transition.
- Testcontainers PostgreSQL suite in CI.
- Structured logging, correlation IDs, health checks, and audit export.
- Security hardening appropriate for a demo: MFA/step-up can be modelled, but never
claim production compliance.

## 12. Build Order for the Next Sprint

1. Write state-transition tests before changing the settlement code.
2. Add `LedgerEntry` migration/entity/repository and a single posting service.
3. Modify payment settlement and approval to call that one service.
4. Add beneficiary and payment context fields.
5. Expose structured risk-factor details in a case-detail DTO.
6. Replace the risk-case modal with a dedicated case workspace.

Do not begin Phase 3 UI metrics until steps 1-5 are complete. They depend on reliable
lifecycle and case data.

## 13. Success Criteria

The project is successful when it can truthfully demonstrate all of the following:

- A payment is safely deduplicated, evaluated, held/approved/declined, and auditable.
- A held payment reaches an analyst with specific, comprehensible reasons.
- An analyst can take an accountable action, including escalation or freeze.
- Every financial state change is balanced, immutable, and reversible by compensation.
- The dashboard reports operational effectiveness rather than decorative metrics.
- The ML component is transparent about its role and limitations.
