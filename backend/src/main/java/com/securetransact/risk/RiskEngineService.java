package com.securetransact.risk;

import com.securetransact.ml.MlFeatureCollector;
import com.securetransact.ml.MlScore;
import com.securetransact.ml.RiskScoringClient;
import com.securetransact.model.Account;
import com.securetransact.model.Transaction;
import com.securetransact.model.RiskLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiskEngineService {

    private final StatisticalRiskScoringService statisticalScoringService;
    private final RiskDecisionEngine decisionEngine;
    private final BehavioralProfileService behavioralProfileService;
    private final RiskScoringClient riskScoringClient;
    private final MlFeatureCollector featureCollector;

    public RiskEngineResult evaluateTransaction(Transaction transaction, Account sourceAccount) {
        RiskScoringResult scoringResult = statisticalScoringService.scoreTransaction(transaction, sourceAccount);

        MlScore mlScore = riskScoringClient.score(featureCollector.collect(transaction, sourceAccount)).orElse(null);
        if (mlScore != null) {
            applyMlBoost(scoringResult, mlScore);
        }

        var decision = decisionEngine.decide(scoringResult.getRiskLevel(), scoringResult.getTotalScore());
        scoringResult.setDecision(decision);

        behavioralProfileService.updateProfileAfterTransaction(sourceAccount, transaction.getAmount());

        log.info("Risk evaluation for txn {}: score={}, level={}, decision={}, factors={}, mlProbability={}",
                transaction.getId(), scoringResult.getTotalScore(), scoringResult.getRiskLevel(),
                decision, scoringResult.getFactors() == null ? 0 : scoringResult.getFactors().size(),
                scoringResult.getMlProbability());

        return RiskEngineResult.builder()
                .scoringResult(scoringResult)
                .build();
    }

    /**
     * Blends the ML anomaly score (0-100) into the statistical score. ML is a soft signal:
     * high anomaly scores add points (capped at 100) and are recorded as a persistable
     * factor plus mlProbability for auditability.
     */
    private void applyMlBoost(RiskScoringResult scoringResult, MlScore mlScore) {
        scoringResult.setMlProbability(BigDecimal.valueOf(mlScore.riskScore())
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));

        if (mlScore.riskScore() >= 80) {
            addFactor(scoringResult, "ML_ANOMALY_BLOCK_INDICATED", 35,
                    "ML model indicates high anomaly (score " + mlScore.riskScore() + ")");
        } else if (mlScore.riskScore() >= 50) {
            addFactor(scoringResult, "ML_ANOMALY_FLAGGED", 20,
                    "ML model flagged anomaly (score " + mlScore.riskScore() + ")");
        } else {
            return;
        }

        scoringResult.setModelVersion("statistical-risk-v1+isolation-forest-v1");
        scoringResult.setTotalScore(Math.min(scoringResult.getTotalScore() + pointsAdded(scoringResult), 100));
        scoringResult.setRiskLevel(StatisticalRiskScoringService.determineRiskLevel(scoringResult.getTotalScore()));
    }

    private void addFactor(RiskScoringResult scoringResult, String code, int points, String message) {
        scoringResult.getFactors().add(RiskFactor.builder()
                .code(code)
                .points(points)
                .message(message)
                .build());
    }

    private int pointsAdded(RiskScoringResult scoringResult) {
        return scoringResult.getFactors().stream()
                .filter(f -> f.getCode().startsWith("ML_ANOMALY"))
                .mapToInt(RiskFactor::getPoints)
                .sum();
    }
}