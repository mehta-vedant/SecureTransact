package com.securetransact.risk;

import com.securetransact.ml.MlFeaturesRequest;
import com.securetransact.ml.MlScore;
import com.securetransact.ml.RiskScoringClient;
import com.securetransact.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskEngineServiceTest {

    @Mock private StatisticalRiskScoringService statisticalScoringService;
    @Mock private RiskDecisionEngine decisionEngine;
    @Mock private BehavioralProfileService behavioralProfileService;
    @Mock private InProcessAnomalyScorer inProcessAnomalyScorer;
    @Mock private RiskScoringClient riskScoringClient;
    @Mock private com.securetransact.ml.MlFeatureCollector featureCollector;

    @InjectMocks
    private RiskEngineService riskEngineService;

    private Transaction transaction;
    private Account account;

    @BeforeEach
    void setUp() {
        transaction = Transaction.builder()
                .amount(new BigDecimal("100"))
                .type(TransactionType.WITHDRAWAL)
                .build();
        account = Account.builder()
                .id(1L)
                .accountNumber("ACC-001")
                .build();
        when(featureCollector.collect(any(), any())).thenReturn(
                new MlFeaturesRequest(new BigDecimal("100"), "2026-09-15T10:00:00",
                        BigDecimal.ZERO, BigDecimal.ONE, "2026-03-01T00:00:00",
                        0, 0, BigDecimal.ZERO, 0, false, false));
    }

    private RiskScoringResult statisticalResult(int score, RiskLevel level) {
        return RiskScoringResult.builder()
                .totalScore(score)
                .riskLevel(level)
                .factors(new ArrayList<>())
                .modelVersion("statistical-risk-v1")
                .build();
    }

    private void stubAnomaly(int riskScore) {
        when(inProcessAnomalyScorer.score(any()))
                .thenReturn(new MlScore(riskScore, "ALLOW", InProcessAnomalyScorer.MODEL_VERSION));
    }

    @Test
    void shouldApplyBlockBoostForHighAnomalyScore() {
        when(statisticalScoringService.scoreTransaction(any(), any()))
                .thenReturn(statisticalResult(20, RiskLevel.LOW));
        when(riskScoringClient.score(any())).thenReturn(Optional.empty());
        when(decisionEngine.decide(any(), anyInt())).thenReturn(RiskDecision.ALLOW);
        stubAnomaly(85);

        RiskEngineResult result = riskEngineService.evaluateTransaction(transaction, account);

        assertEquals(55, result.getScoringResult().getTotalScore());
        assertEquals(RiskLevel.HIGH, result.getScoringResult().getRiskLevel());
        assertNotNull(result.getScoringResult().getMlProbability());
        assertTrue(result.getScoringResult().getFactors().stream()
                .anyMatch(f -> f.getCode().equals("ANOMALY_BLOCK_INDICATED")));
    }

    @Test
    void shouldApplyFlagBoostForMediumAnomalyScore() {
        when(statisticalScoringService.scoreTransaction(any(), any()))
                .thenReturn(statisticalResult(10, RiskLevel.LOW));
        when(riskScoringClient.score(any())).thenReturn(Optional.empty());
        when(decisionEngine.decide(any(), anyInt())).thenReturn(RiskDecision.ALLOW);
        stubAnomaly(60);

        RiskEngineResult result = riskEngineService.evaluateTransaction(transaction, account);

        assertEquals(30, result.getScoringResult().getTotalScore());
        assertTrue(result.getScoringResult().getFactors().stream()
                .anyMatch(f -> f.getCode().equals("ANOMALY_FLAGGED")));
    }

    @Test
    void shouldCapsCoreAt100AndUpgradeLevel() {
        when(statisticalScoringService.scoreTransaction(any(), any()))
                .thenReturn(statisticalResult(90, RiskLevel.CRITICAL));
        when(riskScoringClient.score(any())).thenReturn(Optional.empty());
        when(decisionEngine.decide(any(), anyInt())).thenReturn(RiskDecision.ALLOW);
        stubAnomaly(95);

        RiskEngineResult result = riskEngineService.evaluateTransaction(transaction, account);

        assertEquals(100, result.getScoringResult().getTotalScore());
        assertEquals(RiskLevel.CRITICAL, result.getScoringResult().getRiskLevel());
    }

    @Test
    void shouldIgnoreLowAnomalyScore() {
        when(statisticalScoringService.scoreTransaction(any(), any()))
                .thenReturn(statisticalResult(10, RiskLevel.LOW));
        when(riskScoringClient.score(any())).thenReturn(Optional.empty());
        when(decisionEngine.decide(any(), anyInt())).thenReturn(RiskDecision.ALLOW);
        stubAnomaly(20);

        RiskEngineResult result = riskEngineService.evaluateTransaction(transaction, account);

        assertEquals(10, result.getScoringResult().getTotalScore());
        assertTrue(result.getScoringResult().getFactors().isEmpty());
        assertNull(result.getScoringResult().getMlProbability());
    }

    @Test
    void shouldAddRemoteMlBoostOnTopOfAnomalyBoost() {
        when(statisticalScoringService.scoreTransaction(any(), any()))
                .thenReturn(statisticalResult(20, RiskLevel.LOW));
        when(riskScoringClient.score(any()))
                .thenReturn(Optional.of(new MlScore(85, "BLOCK", "isolation-forest-v1")));
        when(decisionEngine.decide(any(), anyInt())).thenReturn(RiskDecision.ALLOW);
        stubAnomaly(85);

        RiskEngineResult result = riskEngineService.evaluateTransaction(transaction, account);

        assertEquals(70, result.getScoringResult().getTotalScore());
        assertTrue(result.getScoringResult().getFactors().stream()
                .anyMatch(f -> f.getCode().equals("ANOMALY_BLOCK_INDICATED")));
        assertTrue(result.getScoringResult().getFactors().stream()
                .anyMatch(f -> f.getCode().equals("ML_ANOMALY_BLOCK_INDICATED")));
        assertTrue(result.getScoringResult().getModelVersion()
                .contains("in-process-anomaly-v1+isolation-forest-v1"));
    }

    @Test
    void shouldRunAnomalyBoostEvenWhenMlUnavailable() {
        when(statisticalScoringService.scoreTransaction(any(), any()))
                .thenReturn(statisticalResult(10, RiskLevel.LOW));
        when(riskScoringClient.score(any())).thenReturn(Optional.empty());
        when(decisionEngine.decide(any(), anyInt())).thenReturn(RiskDecision.ALLOW);
        stubAnomaly(60);

        RiskEngineResult result = riskEngineService.evaluateTransaction(transaction, account);

        assertEquals(30, result.getScoringResult().getTotalScore());
        assertNotNull(result.getScoringResult().getMlProbability());
        assertTrue(result.getScoringResult().getModelVersion()
                .contains(InProcessAnomalyScorer.MODEL_VERSION));
    }
}