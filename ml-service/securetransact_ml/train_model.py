"""Model training script.

Trains an IsolationForest on synthetic transaction data and saves
the model to disk. In production this would be replaced with real
labeled fraud data.

Usage:
    python -m securetransact_ml.train_model
"""
from __future__ import annotations

import os
import numpy as np
import joblib
from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import StandardScaler

MODEL_DIR = os.path.join(os.path.dirname(__file__), "..", "models")
MODEL_PATH = os.path.join(MODEL_DIR, "risk_model.pkl")
SCALER_PATH = os.path.join(MODEL_DIR, "scaler.pkl")
CALIBRATION_PATH = os.path.join(MODEL_DIR, "anomaly_calibration.pkl")
MODEL_VERSION = "isolation-forest-anomaly-v2"


def generate_synthetic_data(n_samples: int = 10_000) -> np.ndarray:
    """Generate synthetic transaction feature vectors.

    Features (11-dim, matching TransactionFeatures):
      amount, hour_of_day, day_of_week, amount_zscore,
      account_age_days, txn_count_1h, txn_count_24h,
      avg_amount_7d, unique_recipients_24h, is_cross_border, is_new_payee
    """
    rng = np.random.default_rng(42)

    amounts = rng.lognormal(mean=6.0, sigma=1.5, size=n_samples)  # ~\$400 median
    hours = rng.integers(0, 24, size=n_samples)
    days = rng.integers(0, 7, size=n_samples)
    zscores = rng.standard_normal(n_samples)
    account_ages = rng.integers(1, 3650, size=n_samples)
    txn_1h = rng.poisson(lam=1.5, size=n_samples)
    txn_24h = rng.poisson(lam=8, size=n_samples)
    avg_7d = rng.lognormal(mean=5.5, sigma=1.0, size=n_samples)
    recipients_24h = rng.poisson(lam=2, size=n_samples)
    cross_border = rng.binomial(1, 0.05, size=n_samples)
    new_payee = rng.binomial(1, 0.15, size=n_samples)

    X = np.column_stack([
        amounts, hours, days, zscores, account_ages,
        txn_1h, txn_24h, avg_7d, recipients_24h,
        cross_border, new_payee,
    ])

    return X


def train() -> None:
    """Train IsolationForest and persist model + scaler."""
    print("Generating synthetic training data...")
    X = generate_synthetic_data(10_000)

    print("Fitting StandardScaler...")
    scaler = StandardScaler()
    X_scaled = scaler.fit_transform(X)

    print("Training IsolationForest on the synthetic benign baseline...")
    model = IsolationForest(
        n_estimators=200,
        contamination="auto",
        max_samples="auto",
        random_state=42,
        n_jobs=-1,
    )
    model.fit(X_scaled)

    # IsolationForest output ranks observations but is not a fraud probability. Persist
    # benign score quantiles so serving returns an empirical anomaly percentile.
    calibration = {
        "model_version": MODEL_VERSION,
        "feature_count": X.shape[1],
        "benign_anomaly_scores": np.sort(-model.score_samples(X_scaled)),
    }

    os.makedirs(MODEL_DIR, exist_ok=True)
    joblib.dump(model, MODEL_PATH)
    joblib.dump(scaler, SCALER_PATH)
    joblib.dump(calibration, CALIBRATION_PATH)
    print(f"Model saved to {MODEL_PATH}")
    print(f"Scaler saved to {SCALER_PATH}")


if __name__ == "__main__":
    train()
