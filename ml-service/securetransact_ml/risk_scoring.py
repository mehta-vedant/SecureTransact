"""Risk scoring Flask API.

Exposes POST /score that accepts a transaction feature vector and
returns a risk score (0-100) with a decision (ALLOW / HOLD_FOR_REVIEW / BLOCK).

Called by the Java backend via MlClientConfig bean (RestClient) with a 500ms timeout.
"""
from __future__ import annotations

import os
import joblib
import numpy as np
from flask import Flask, request, jsonify

from .features import extract_features, TransactionFeatures

app = Flask(__name__)

MODEL_DIR = os.path.join(os.path.dirname(__file__), "..", "models")
_model = None
_scaler = None
_calibration = None

REQUIRED_FIELDS = {
    "amount", "timestamp", "userMeanAmount", "userStdAmount", "accountCreatedAt",
    "txnCount1h", "txnCount24h", "avgAmount7d", "uniqueRecipients24h",
    "isCrossBorder", "isNewPayee",
}


def _load_model():
    global _model, _scaler, _calibration
    if _model is None:
        model_path = os.path.join(MODEL_DIR, "risk_model.pkl")
        scaler_path = os.path.join(MODEL_DIR, "scaler.pkl")
        calibration_path = os.path.join(MODEL_DIR, "anomaly_calibration.pkl")
        if not all(os.path.exists(path) for path in (model_path, scaler_path, calibration_path)):
            raise FileNotFoundError(
                "Model or calibration artifact not found. Run `python -m securetransact_ml.train_model` first."
            )
        _model = joblib.load(model_path)
        _scaler = joblib.load(scaler_path)
        _calibration = joblib.load(calibration_path)


def _anomaly_score_to_percentile(anomaly_score: float) -> tuple[int, str]:
    baseline = _calibration["benign_anomaly_scores"]
    percentile = int(np.searchsorted(baseline, anomaly_score, side="right") / len(baseline) * 100)
    label = "ANOMALY_HIGH" if percentile >= 99 else "ANOMALY_ELEVATED" if percentile >= 95 else "NORMAL"
    return min(percentile, 100), label


def _validated_features(data: dict) -> TransactionFeatures:
    if not isinstance(data, dict):
        raise ValueError("Request body must be a JSON object")
    missing = REQUIRED_FIELDS.difference(data)
    if missing:
        raise ValueError(f"Missing required fields: {', '.join(sorted(missing))}")
    if not isinstance(data["isCrossBorder"], bool) or not isinstance(data["isNewPayee"], bool):
        raise ValueError("isCrossBorder and isNewPayee must be booleans")
    return extract_features(
        amount=float(data["amount"]), timestamp_iso=str(data["timestamp"]),
        user_mean_amount=float(data["userMeanAmount"]), user_std_amount=float(data["userStdAmount"]),
        account_created_at=str(data["accountCreatedAt"]), txn_count_1h=int(data["txnCount1h"]),
        txn_count_24h=int(data["txnCount24h"]), avg_amount_7d=float(data["avgAmount7d"]),
        unique_recipients_24h=int(data["uniqueRecipients24h"]),
        is_cross_border=data["isCrossBorder"], is_new_payee=data["isNewPayee"],
    )


@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "UP", "service": "securetransact-ml"})


@app.route("/score", methods=["POST"])
def score():
    """Score a transaction for fraud risk.

    Request body (JSON):
        amount, timestamp, userMeanAmount, userStdAmount,
        accountCreatedAt, txnCount1h, txnCount24h, avgAmount7d,
        uniqueRecipients24h, isCrossBorder, isNewPayee
    """
    try:
        _load_model()
    except FileNotFoundError as e:
        return jsonify({"error": str(e)}), 503

    try:
        features = _validated_features(request.get_json(force=True))
    except (TypeError, ValueError) as error:
        return jsonify({"error": str(error)}), 400

    vec = np.array([[
        features.amount, features.hour_of_day, features.day_of_week,
        features.amount_zscore, features.account_age_days,
        features.txn_count_1h, features.txn_count_24h,
        features.avg_amount_7d, features.unique_recipients_24h,
        int(features.is_cross_border), int(features.is_new_payee),
    ]])

    vec_scaled = _scaler.transform(vec)
    anomaly_score = float(-_model.score_samples(vec_scaled)[0])
    risk_score, decision = _anomaly_score_to_percentile(anomaly_score)

    return jsonify({
        "riskScore": risk_score,
        "decision": decision,
        "scoreType": "benign_baseline_anomaly_percentile",
        "modelVersion": _calibration["model_version"],
        "features": features.to_dict(),
    })


def create_app() -> Flask:
    """Factory for gunicorn / testing."""
    return app


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5001, debug=True)
