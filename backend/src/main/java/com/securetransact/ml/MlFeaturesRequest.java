package com.securetransact.ml;

import java.math.BigDecimal;

/** Feature vector sent to the ML service, matching ml-service risk_scoring.py. */
public record MlFeaturesRequest(
        BigDecimal amount,
        String timestamp,
        BigDecimal userMeanAmount,
        BigDecimal userStdAmount,
        String accountCreatedAt,
        long txnCount1h,
        long txnCount24h,
        BigDecimal avgAmount7d,
        long uniqueRecipients24h,
        boolean isCrossBorder,
        boolean isNewPayee) {
}