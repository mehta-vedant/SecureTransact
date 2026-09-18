package com.securetransact.model;

public enum TransactionStatus {
    CREATED,
    RISK_EVALUATED,
    APPROVED,
    HELD_FOR_REVIEW,
    SETTLED,
    REVERSED,
    REJECTED,
    FAILED
}
