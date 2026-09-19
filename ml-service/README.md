# SecureTransact ML Risk Scoring Service

Python-based statistical anomaly detection service using scikit-learn's IsolationForest.

## Architecture

```
Standalone Flask API  (POST /score)   ← called directly by clients / for experimentation
    ↓
IsolationForest + StandardScaler
    ↓
{ riskScore: 0-100, decision: ALLOW|HOLD_FOR_REVIEW|BLOCK }
```

> The Spring Boot risk engine calls this service as an optional, time-bounded secondary
> signal. It must never be the sole reason to decline a payment: if unavailable, the
> backend continues with explainable rules and its in-process heuristic.

## Running Locally

```bash
cd ml-service
pip install -r requirements.txt
python -m securetransact_ml.train_model   # trains and saves model to models/
python -m securetransact_ml.risk_scoring  # starts Flask on :5001
```

## API

### POST /score

Request:
```json
{
  "amount": 5000.00,
  "timestamp": "2026-08-26T03:15:00",
  "userMeanAmount": 250.00,
  "userStdAmount": 100.00,
  "accountCreatedAt": "2026-08-20T10:00:00",
  "txnCount1h": 2,
  "txnCount24h": 8,
  "avgAmount7d": 200.00,
  "uniqueRecipients24h": 3,
  "isCrossBorder": false,
  "isNewPayee": true
}
```

Response:
```json
{
  "riskScore": 98,
  "decision": "ANOMALY_ELEVATED",
  "scoreType": "benign_baseline_anomaly_percentile",
  "modelVersion": "isolation-forest-anomaly-v2",
  "features": { ... }
}
```

### GET /health

```json
{ "status": "UP", "service": "securetransact-ml" }
```

## Features (11-dim)

| # | Feature | Description |
|---|---------|-------------|
| 0 | amount | Transaction amount in USD |
| 1 | hour_of_day | 0-23 |
| 2 | day_of_week | 0=Monday … 6=Sunday |
| 3 | amount_zscore | Z-score vs user historical mean |
| 4 | account_age_days | Days since account creation |
| 5 | txn_count_1h | Transactions in last hour |
| 6 | txn_count_24h | Transactions in last 24 hours |
| 7 | avg_amount_7d | Average amount over 7 days |
| 8 | unique_recipients_24h | Distinct recipients in 24h |
| 9 | is_cross_border | 0 or 1 |
| 10 | is_new_payee | 0 or 1 |

## Docker

```bash
docker build -t securetransact-ml .
docker run -p 5001:5001 securetransact-ml
```

## Model

- **Algorithm**: IsolationForest (scikit-learn 1.6)
- **Training**: Synthetic benign baseline only; this is a demo artifact, not a fraud model.
- **Scoring**: Empirical percentile versus the benign training score distribution, not fraud probability.
- **Swap-ready**: Real deployment requires representative payment data, calibration, and model governance.

## Supervised benchmark experiment

`securetransact_ml.train_amlnet` is the reproducible, offline experiment for
AMLNet, a synthetic payment-network dataset. It derives the same 11 online-safe
features used by the service from prior transaction history, then uses a temporal
split, compares a class-balanced logistic baseline with gradient boosting, selects
by validation PR-AUC, and calibrates on the validation window.

```bash
python -m securetransact_ml.train_amlnet --input data/raw/AMLNet_August_2025.csv
```

The trained artifact is deliberately written to `analysis/amlnet-experiment/`,
not `models/`, and is never used by `POST /score`. That boundary makes the demo
safe: the current live signal remains optional, while the supervised result is
measured and reviewed before any deployment decision.
