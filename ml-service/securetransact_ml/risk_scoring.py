"""Risk scoring Flask API.

Exposes POST /score that accepts a transaction feature vector and
returns a risk score (0-100) with a decision (ALLOW / HOLD_FOR_REVIEW / BLOCK).

Standalone endpoint — not currently called by the Java backend.
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


def _load_model():
    global _model, _scaler
    if _model is None:
        model_path = os.path.join(MODEL_DIR, "risk_model.pkl")
        scaler_path = os.path.join(MODEL_DIR, "scaler.pkl")
        if not os.path.exists(model_path):
            raise FileNotFoundError(
                f"Model not found at {model_path}. "
                "Run `python -m securetransact_ml.train_model` first."
            )
        _model = joblib.load(model_path)
        _scaler = joblib.load(scaler_path)


def _anomaly_score_to_risk(score: float) -> tuple[int, str]:
    """Convert IsolationForest decision_function to 0-100 risk score.

    decision_function returns values where negative = anomaly.
    We normalise to 0-100 where 100 = highest risk.
    """
    # decision_function typically ranges from -0.5 to 0.5
    # Clamp and normalise: -0.5 → 100, 0.5 → 0
    normalised = np.clip(-score, -0.5, 0.5)  # negate so anomaly = positive
    risk = int((normalised / 0.5) * 100)
    risk = max(0, min(100, risk))

    if risk >= 80:
        decision = "BLOCK"
    elif risk >= 50:
        decision = "HOLD_FOR_REVIEW"
    else:
        decision = "ALLOW"

    return risk, decision


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

    data = request.get_json(force=True)

    features = extract_features(
        amount=float(data.get("amount", 0)),
        timestamp_iso=data.get("timestamp", "2026-01-01T00:00:00"),
        user_mean_amount=float(data.get("userMeanAmount", 0)),
        user_std_amount=float(data.get("userStdAmount", 1)),
        account_created_at=data.get("accountCreatedAt", "2026-01-01T00:00:00"),
        txn_count_1h=int(data.get("txnCount1h", 0)),
        txn_count_24h=int(data.get("txnCount24h", 0)),
        avg_amount_7d=float(data.get("avgAmount7d", 0)),
        unique_recipients_24h=int(data.get("uniqueRecipients24h", 0)),
        is_cross_border=bool(data.get("isCrossBorder", False)),
        is_new_payee=bool(data.get("isNewPayee", False)),
    )

    vec = np.array([[
        features.amount, features.hour_of_day, features.day_of_week,
        features.amount_zscore, features.account_age_days,
        features.txn_count_1h, features.txn_count_24h,
        features.avg_amount_7d, features.unique_recipients_24h,
        int(features.is_cross_border), int(features.is_new_payee),
    ]])

    vec_scaled = _scaler.transform(vec)
    raw_score = _model.decision_function(vec_scaled)[0]
    risk_score, decision = _anomaly_score_to_risk(raw_score)

    return jsonify({
        "riskScore": risk_score,
        "decision": decision,
        "modelVersion": "isolation-forest-v1",
        "features": features.to_dict(),
    })


def create_app() -> Flask:
    """Factory for gunicorn / testing."""
    return app


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5001, debug=True)
