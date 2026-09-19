# AMLNet supervised-risk experiment

Run date: 2026-09-19. Dataset: AMLNet August 2025, a synthetic payment-network
benchmark. The model is an offline experiment and is not a payment-decline control.

## Data and evaluation

- 1,090,172 payment events; 1,745 `isFraud` labels (0.160%).
- Event records were sorted by their embedded timestamp before feature generation.
- The source file's `step` field is not usable as an event-time ordering key.
- Features use only information available before the payment: amount, time, prior
  payer history, velocity, recipient diversity, and new-payee state.
- Excluded as leakage: post-payment balances, generated `fraud_probability`,
  metadata risk indicators, and money-laundering typology.
- Chronological split: 60% train, 20% calibration/model selection, 20% untouched test.

## Model selection

| Candidate | Validation PR-AUC | Validation Brier |
| --- | ---: | ---: |
| Balanced logistic regression | 0.4149 | 0.00120 |
| Balanced histogram gradient boosting | **0.4952** | **0.00099** |

Histogram gradient boosting was selected and calibrated with the validation period.
On the final held-out period it achieved PR-AUC **0.4804**, ROC-AUC **0.9963**,
and Brier score **0.00084**. At a 10% precision target, its experimental threshold
was 0.0153 with 94.37% recall.

These results demonstrate a coherent offline benchmark, not deployment readiness:
AMLNet is synthetic and its target process differs from real SecureTransact analyst
outcomes. If enabled later, this model may only provide an auditable advisory review
priority; it must not independently approve, hold, or decline a customer payment.
