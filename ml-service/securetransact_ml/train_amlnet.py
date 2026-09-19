"""Train a time-safe supervised payment-risk experiment on AMLNet.

This is deliberately an *offline experiment*. AMLNet is a synthetic bank-payment
benchmark, not SecureTransact production data. Its artifact is never loaded by
the Flask scoring endpoint; promotion requires docs/ML_GOVERNANCE.md.

The feature builder only uses facts observable before the current payment. It
excludes generated risk indicators, post-transaction balances, fraud probability,
and money-laundering typology.
"""
from __future__ import annotations

import argparse
import json
import re
from collections import deque
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Deque

import joblib
import numpy as np
import pandas as pd
from sklearn.calibration import CalibratedClassifierCV
from sklearn.ensemble import HistGradientBoostingClassifier
from sklearn.metrics import average_precision_score, brier_score_loss, precision_recall_curve, roc_auc_score
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler

FEATURE_NAMES = [
    "amount", "hour_of_day", "day_of_week", "amount_zscore", "account_age_days",
    "txn_count_1h", "txn_count_24h", "avg_amount_7d", "unique_recipients_24h",
    "is_cross_border", "is_new_payee",
]
MODEL_VERSION = "amlnet-payment-risk-experiment-v1"
USE_COLUMNS = ["step", "amount", "nameOrig", "nameDest", "isFraud", "hour", "day_of_week", "metadata"]
TIMESTAMP_PATTERN = re.compile(
    r"timestamp': datetime\.datetime\((\d+),\s*(\d+),\s*(\d+),\s*(\d+),\s*(\d+),\s*(\d+)"
)


@dataclass
class AccountHistory:
    first_hour: int
    events: Deque[tuple[int, float, str]]
    recipients_seen: set[str]


def _as_hour(metadata: str, step: float, hour: int, day_of_week: int) -> int:
    """Use the event timestamp, not AMLNet's constant placeholder ``step``."""
    match = TIMESTAMP_PATTERN.search(str(metadata))
    if match:
        return int(datetime(*map(int, match.groups())).timestamp() // 3600)
    return int(float(step)) if pd.notna(step) else int(day_of_week) * 24 + int(hour)


def _trim(events: Deque[tuple[int, float, str]], event_hour: int) -> None:
    while events and events[0][0] < event_hour - 24 * 7:
        events.popleft()


def build_feature_frame(csv_path: Path, chunksize: int = 50_000) -> pd.DataFrame:
    """Stream AMLNet and construct causal features in source-time order."""
    histories: dict[str, AccountHistory] = {}
    rows: list[list[float]] = []
    labels: list[int] = []
    last_hour = -1
    reader = pd.read_csv(csv_path, usecols=USE_COLUMNS, chunksize=chunksize, on_bad_lines="skip")
    for chunk_number, chunk in enumerate(reader, start=1):
        for event in chunk.itertuples(index=False):
            event_hour = _as_hour(event.metadata, event.step, event.hour, event.day_of_week)
            if event_hour < last_hour:
                raise ValueError("AMLNet input is not time ordered; sort it before feature generation.")
            last_hour = event_hour
            origin, destination, amount = str(event.nameOrig), str(event.nameDest), float(event.amount)
            history = histories.get(origin)
            if history is None:
                history = AccountHistory(event_hour, deque(), set())
                histories[origin] = history
            _trim(history.events, event_hour)
            prior_amounts = [entry[1] for entry in history.events]
            mean = float(np.mean(prior_amounts)) if prior_amounts else amount
            std = float(np.std(prior_amounts)) if len(prior_amounts) > 1 else 0.0
            count_1h = sum(entry[0] >= event_hour - 1 for entry in history.events)
            count_24h = sum(entry[0] >= event_hour - 24 for entry in history.events)
            amounts_7d = [entry[1] for entry in history.events if entry[0] >= event_hour - 24 * 7]
            recipients_24h = {entry[2] for entry in history.events if entry[0] >= event_hour - 24}
            rows.append([
                amount, int(event.hour), int(event.day_of_week),
                (amount - mean) / std if std > 0 else 0.0,
                max(0, (event_hour - history.first_hour) // 24), count_1h, count_24h,
                float(np.mean(amounts_7d)) if amounts_7d else amount,
                len(recipients_24h), 0, int(destination not in history.recipients_seen),
            ])
            labels.append(int(event.isFraud))
            history.events.append((event_hour, amount, destination))
            history.recipients_seen.add(destination)
        print(f"Processed {chunk_number * chunksize:,} rows", flush=True)
    if not rows:
        raise ValueError("No valid AMLNet rows were read.")
    frame = pd.DataFrame(rows, columns=FEATURE_NAMES)
    frame["label"] = labels
    return frame


def _threshold_at_precision(y_true: np.ndarray, probabilities: np.ndarray, target: float = 0.10) -> dict:
    precision, recall, thresholds = precision_recall_curve(y_true, probabilities)
    eligible = np.flatnonzero(precision[:-1] >= target)
    if not len(eligible):
        return {"target_precision": target, "threshold": None, "recall": None}
    index = eligible[np.argmax(recall[eligible])]
    return {"target_precision": target, "threshold": float(thresholds[index]), "recall": float(recall[index])}


def train(csv_path: Path, output_dir: Path) -> dict:
    frame = build_feature_frame(csv_path)
    X = frame[FEATURE_NAMES].to_numpy(dtype=float)
    y = frame["label"].to_numpy(dtype=int)
    if len(np.unique(y)) < 2:
        raise ValueError("The input does not contain both fraud and non-fraud labels.")
    validation_start, test_start = int(len(frame) * 0.60), int(len(frame) * 0.80)
    X_train, X_validation, X_test = X[:validation_start], X[validation_start:test_start], X[test_start:]
    y_train, y_validation, y_test = y[:validation_start], y[validation_start:test_start], y[test_start:]
    if min(y_train.sum(), y_validation.sum(), y_test.sum()) == 0:
        raise ValueError("A temporal split has a period without fraud labels; do not use a random split to hide this.")
    scaler = StandardScaler()
    X_train_scaled = scaler.fit_transform(X_train)
    X_validation_scaled = scaler.transform(X_validation)
    X_test_scaled = scaler.transform(X_test)
    candidates = {
        "logistic_regression": LogisticRegression(class_weight="balanced", max_iter=1_000, random_state=42),
        "hist_gradient_boosting": HistGradientBoostingClassifier(learning_rate=0.08, max_leaf_nodes=15, l2_regularization=1.0, class_weight="balanced", random_state=42),
    }
    calibrated_candidates = {}
    validation_metrics = {}
    for name, base_model in candidates.items():
        base_model.fit(X_train_scaled, y_train)
        calibrated = CalibratedClassifierCV(base_model, method="isotonic", cv="prefit")
        calibrated.fit(X_validation_scaled, y_validation)
        validation_probabilities = calibrated.predict_proba(X_validation_scaled)[:, 1]
        calibrated_candidates[name] = calibrated
        validation_metrics[name] = {
            "pr_auc": float(average_precision_score(y_validation, validation_probabilities)),
            "brier_score": float(brier_score_loss(y_validation, validation_probabilities)),
        }
    selected_model_name = max(validation_metrics, key=lambda name: validation_metrics[name]["pr_auc"])
    calibrated = calibrated_candidates[selected_model_name]
    probabilities = calibrated.predict_proba(X_test_scaled)[:, 1]
    metrics = {
        "model_version": MODEL_VERSION, "dataset": "AMLNet August 2025 (synthetic benchmark)",
        "label": "isFraud", "feature_schema": FEATURE_NAMES, "selected_model": selected_model_name,
        "validation_model_comparison": validation_metrics,
        "rows": int(len(frame)), "fraud_rows": int(y.sum()),
        "splits": {"train": int(len(y_train)), "validation": int(len(y_validation)), "test": int(len(y_test))},
        "test": {"positive_rate": float(y_test.mean()), "pr_auc": float(average_precision_score(y_test, probabilities)), "roc_auc": float(roc_auc_score(y_test, probabilities)), "brier_score": float(brier_score_loss(y_test, probabilities)), "operating_point": _threshold_at_precision(y_test, probabilities)},
        "limitations": ["Synthetic benchmark; not representative production evidence.", "No automatic payment decision may use this model.", "Artifacts are intentionally not served by risk_scoring.py."],
    }
    output_dir.mkdir(parents=True, exist_ok=True)
    joblib.dump({"model": calibrated, "scaler": scaler, "feature_names": FEATURE_NAMES}, output_dir / "model.pkl")
    (output_dir / "metrics.json").write_text(json.dumps(metrics, indent=2), encoding="utf-8")
    return metrics


def main() -> None:
    parser = argparse.ArgumentParser(description="Train the offline AMLNet SecureTransact experiment.")
    parser.add_argument("--input", type=Path, required=True, help="Path to AMLNet CSV")
    parser.add_argument("--output", type=Path, default=Path("analysis/amlnet-experiment"))
    args = parser.parse_args()
    print(json.dumps(train(args.input, args.output)["test"], indent=2))


if __name__ == "__main__":
    main()
