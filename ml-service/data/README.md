# Training Data Policy

## Offline benchmark included in this project

The recommended benchmark is [**AMLNet (August 2025)**](https://zenodo.org/records/16736515): a synthetic Australian
payment-network dataset with payer and beneficiary identifiers, payment rails,
timestamps, and `isFraud` labels. Download it from its Zenodo record and place
the CSV under `raw/` (that directory is intentionally ignored by Git). Run:

```bash
python -m securetransact_ml.train_amlnet --input data/raw/AMLNet_August_2025.csv
```

The experiment uses only pre-decision fields and creates causal account-history
features. It excludes post-payment balances, generated `fraud_probability`,
metadata risk indicators, and typology fields because they would leak the label.
It uses a chronological 60/20/20 train/validation/test split, calibrates on the
validation window, and saves a model plus `metrics.json` under
`analysis/amlnet-experiment/`. Those artifacts are offline-only.

AMLNet is licensed CC BY-NC 4.0. Attribute it and use it only consistently with
that licence. It is a useful payment-shaped benchmark, but it is still synthetic
and cannot justify automatic fraud decisions in SecureTransact.

`creditcard.csv` (the UCI / Worldline card-fraud dataset) is permitted here only for
offline benchmarking. It must **not** be used to train or deploy a SecureTransact model:
it represents anonymized card transactions, not account-to-account payment events.

The checked-in IsolationForest artifact is a synthetic benign-baseline demo model. It is
useful to exercise the anomaly-signal interface, but it is not evidence of fraud-model
accuracy.

## Dataset required before a real model

A trainable SecureTransact dataset must consist of completed payment events plus a delayed,
human-validated disposition. One row must contain the exact online feature schema:

```text
payment_reference, occurred_at, amount, currency, channel, payer_country,
beneficiary_country, account_age_days, txn_count_1h, txn_count_24h,
avg_amount_7d, unique_recipients_24h, is_new_payee, device_id_hash,
analyst_disposition, disposition_at, label
```

`label` is `1` only for confirmed fraud and `0` only for confirmed legitimate payments.
Unresolved, escalated, and reversed-for-non-fraud events must be excluded from supervised
training. Split train/validation/test by time—not randomly—and fit feature transforms only
on the training window.

## Promotion gate

Before any supervised model is enabled, it needs a held-out temporal evaluation, calibration,
threshold approval based on false-positive cost, versioning, and a rollback plan. Until then,
the system must keep the ML sidecar optional and use it only to prioritize analyst review.
