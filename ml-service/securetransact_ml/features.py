"""Feature extraction for ML risk scoring.

Defines the feature vector consumed by the Flask /score endpoint.
The Java backend's decision engine is self-contained; these features are a
standalone replica used by the ML experiment (see ml-service/README).
"""
from __future__ import annotations

from dataclasses import dataclass, asdict
from typing import Optional


@dataclass
class TransactionFeatures:
    amount: float
    hour_of_day: int
    day_of_week: int
    amount_zscore: float          # z-score vs user's historical mean
    account_age_days: int
    txn_count_1h: int
    txn_count_24h: int
    avg_amount_7d: float
    unique_recipients_24h: int
    is_cross_border: bool
    is_new_payee: bool

    def to_dict(self) -> dict:
        return asdict(self)


def extract_features(
    *,
    amount: float,
    timestamp_iso: str,
    user_mean_amount: float,
    user_std_amount: float,
    account_created_at: str,
    txn_count_1h: int,
    txn_count_24h: int,
    avg_amount_7d: float,
    unique_recipients_24h: int,
    is_cross_border: bool = False,
    is_new_payee: bool = False,
) -> TransactionFeatures:
    """Build a feature vector from raw transaction + profile data."""
    from datetime import datetime

    ts = datetime.fromisoformat(timestamp_iso)
    created = datetime.fromisoformat(account_created_at)

    amount_zscore = (
        (amount - user_mean_amount) / user_std_amount
        if user_std_amount > 0
        else 0.0
    )

    return TransactionFeatures(
        amount=amount,
        hour_of_day=ts.hour,
        day_of_week=ts.weekday(),
        amount_zscore=round(amount_zscore, 4),
        account_age_days=(ts - created).days,
        txn_count_1h=txn_count_1h,
        txn_count_24h=txn_count_24h,
        avg_amount_7d=avg_amount_7d,
        unique_recipients_24h=unique_recipients_24h,
        is_cross_border=is_cross_border,
        is_new_payee=is_new_payee,
    )
