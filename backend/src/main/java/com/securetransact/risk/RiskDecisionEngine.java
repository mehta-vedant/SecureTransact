package com.securetransact.risk;

import com.securetransact.model.RiskDecision;
import com.securetransact.model.RiskLevel;
import org.springframework.stereotype.Component;

@Component
public class RiskDecisionEngine {

    private static final int BLOCK_THRESHOLD = 76;
    private static final int HOLD_THRESHOLD = 40;

    public RiskDecision decide(RiskLevel riskLevel, int totalScore) {
        if (riskLevel == RiskLevel.CRITICAL || totalScore >= BLOCK_THRESHOLD) {
            return RiskDecision.BLOCK;
        }
        if (riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.MEDIUM || totalScore >= HOLD_THRESHOLD) {
            return RiskDecision.HOLD_FOR_REVIEW;
        }
        return RiskDecision.ALLOW;
    }

    public static RiskDecision fromScore(int totalScore) {
        RiskLevel level = StatisticalRiskScoringService.determineRiskLevel(totalScore);
        if (level == RiskLevel.CRITICAL || totalScore >= BLOCK_THRESHOLD) {
            return RiskDecision.BLOCK;
        }
        if (level == RiskLevel.HIGH || level == RiskLevel.MEDIUM || totalScore >= HOLD_THRESHOLD) {
            return RiskDecision.HOLD_FOR_REVIEW;
        }
        return RiskDecision.ALLOW;
    }
}
