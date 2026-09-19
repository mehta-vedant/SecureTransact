package com.securetransact.risk;

import com.securetransact.ml.MlFeatureCollector;
import com.securetransact.ml.MlFeaturesRequest;
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
    private final InProcessAnomalyScorer inProcessAnomalyScorer;
    private final RiskScoringClient riskScoringClient;
    private final MlFeatureCollector featureCollector;

    public RiskEngineResult evaluateTransaction(Transaction transaction, Account sourceAccount) {
        RiskScoringResult scoringResult = statisticalScoringService.scoreTransaction(transaction, sourceAccount);

        MlFeaturesRequest features = featureCollector.collect(transaction, sourceAccount);

        MlScore anomalyScore = inProcessAnomalyScorer.score(features);
        applyAnomalyBoost(scoringResult, anomalyScore);

        riskScoringClient.score(features).ifPresent(mlScore -> applyMlBoost(scoringResult, mlScore));

        var decision = decisionEngine.decide(scoringResult.getRiskLevel(), scoringResult.getTotalScore());
        scoringResult.setDecision(decision);

        log.info("Risk evaluation for txn {}: score={}, level={}, decision={}, factors={}, anomaly={}, ml={}",
                transaction.getId(), scoringResult.getTotalScore(), scoringResult.getRiskLevel(),
                decision, scoringResult.getFactors() == null ? 0 : scoringResult.getFactors().size(),
                anomalyScore.riskScore(),
                scoringResult.getMlProbability());

        return RiskEngineResult.builder()
                .scoringResult(scoringResult)
                .build();
    }

    /**
     * Blends the in-process anomaly score (0-100) into the statistical score. This is the
     * primary anomaly signal: deterministic, always runs, and is recorded as a persistable
     * factor plus mlProbability for auditability.
     */
    private void applyAnomalyBoost(RiskScoringResult scoringResult, MlScore anomalyScore) {
        int points;
        String code;
        String message;

        if (anomalyScore.riskScore() >= 80) {
            points = 35;
            code = "ANOMALY_BLOCK_INDICATED";
            message = "In-process anomaly model indicates high anomaly (score " + anomalyScore.riskScore() + ")";
        } else if (anomalyScore.riskScore() >= 50) {
            points = 20;
            code = "ANOMALY_FLAGGED";
            message = "In-process anomaly model flagged anomalous behavior (score " + anomalyScore.riskScore() + ")";
        } else {
            return;
        }

        scoringResult.setMlProbability(BigDecimal.valueOf(anomalyScore.riskScore())
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        scoringResult.setModelVersion("statistical-risk-v1+" + InProcessAnomalyScorer.MODEL_VERSION);
        scoringResult.setTotalScore(Math.min(scoringResult.getTotalScore() + points, 100));
        scoringResult.setRiskLevel(StatisticalRiskScoringService.determineRiskLevel(scoringResult.getTotalScore()));
        scoringResult.getFactors().add(RiskFactor.builder()
                .code(code)
                .points(points)
                .message(message)
                .build());
    }

    /**
     * Blends a remote ML score as an optional secondary signal. Only called when the ML
     * client is enabled and reachable; it appends a small overlay but never replaces the
     * in-process anomaly boost.
     */
    private void applyMlBoost(RiskScoringResult scoringResult, MlScore mlScore) {
        int points;
        String code;
        String message;

        if (mlScore.riskScore() >= 80) {
            points = 15;
            code = "ML_ANOMALY_BLOCK_INDICATED";
            message = "Experimental ML anomaly signal is at percentile " + mlScore.riskScore()
                    + ", model " + mlScore.modelVersion() + ")";
        } else if (mlScore.riskScore() >= 50) {
            points = 10;
            code = "ML_ANOMALY_FLAGGED";
            message = "Experimental ML anomaly signal is elevated at percentile " + mlScore.riskScore()
                    + ", model " + mlScore.modelVersion() + ")";
        } else {
            return;
        }

        if (scoringResult.getMlProbability() == null) {
            scoringResult.setMlProbability(BigDecimal.valueOf(mlScore.riskScore())
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        }
        scoringResult.setModelVersion(scoringResult.getModelVersion() + "+" + mlScore.modelVersion());
        scoringResult.setTotalScore(Math.min(scoringResult.getTotalScore() + points, 100));
        scoringResult.setRiskLevel(StatisticalRiskScoringService.determineRiskLevel(scoringResult.getTotalScore()));
        scoringResult.getFactors().add(RiskFactor.builder()
                .code(code)
                .points(points)
                .message(message)
                .build());
    }
}
