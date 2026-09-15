package com.securetransact.ml;

/** Parsed response from the ML service POST /score endpoint. */
public record MlScore(int riskScore, String decision, String modelVersion) {
}