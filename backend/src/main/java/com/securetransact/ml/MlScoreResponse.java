package com.securetransact.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Raw JSON shape returned by the Flask service (camelCase). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MlScoreResponse(int riskScore, String decision, String modelVersion) {
}