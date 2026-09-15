package com.securetransact.risk;

import com.securetransact.model.RiskDecision;
import com.securetransact.model.RiskLevel;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class RiskScoringResult {
    private int totalScore;
    private RiskLevel riskLevel;
    private RiskDecision decision;
    private String modelVersion;
    private BigDecimal mlProbability;
    private List<RiskFactor> factors;
}
