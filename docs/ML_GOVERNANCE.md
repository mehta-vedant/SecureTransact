# ML Governance For SecureTransact

## Current state

SecureTransact ships an optional IsolationForest sidecar trained on a synthetic benign
baseline. It returns an anomaly percentile, not fraud probability. The server disables it by
default (`APP_ML_ENABLED=false`); deterministic rules, blacklists, and analyst review remain
the authoritative controls.

## Dataset decision

The UCI Credit Card Fraud dataset is retained only to demonstrate benchmarking methodology.
It is unsuitable as SecureTransact training data because it is a two-day, anonymized card
dataset and lacks the account, beneficiary, device, geography, and analyst-disposition data
used by this product. Do not train an XGBoost, Random Forest, or IsolationForest deployment
model on it.

## Model promotion path

1. Collect confirmed-fraud and confirmed-legitimate analyst dispositions for representative
   account-to-account payments.
2. Build features strictly from information available before the payment decision.
3. Use time-ordered train/validation/test windows to avoid future leakage.
4. Compare a calibrated supervised baseline to the anomaly experiment using PR-AUC,
   precision/recall, alert volume, and estimated false-positive cost.
5. Require an approved operating threshold and retain human review for uncertain/high-value
   payments.
6. Version the feature schema, model artifact, metrics, approver, and rollback target.

No model can independently decline a payment merely because of a high score.
