package com.securetransact.risk;

import com.securetransact.ml.MlFeaturesRequest;
import com.securetransact.ml.MlScore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InProcessAnomalyScorerTest {

    private final InProcessAnomalyScorer scorer = new InProcessAnomalyScorer();

    private MlFeaturesRequest request(BigDecimal amount, String ts, String createdAt,
                                      long txn1h, long txn24h) {
        return new MlFeaturesRequest(
                amount, ts, new BigDecimal("200"), new BigDecimal("50"), createdAt,
                txn1h, txn24h, new BigDecimal("180"), 2, false, false);
    }

    @Test
    void shouldScoreLowForNormalTransaction() {
        MlScore score = scorer.score(request(
                new BigDecimal("150"), "2026-09-15T12:00:00", "2026-03-01T00:00:00", 0, 0));
        assertEquals(0, score.riskScore());
        assertEquals("ALLOW", score.decision());
    }

    @Test
    void shouldFlagBurstyVelocityOnFreshAccount() {
        MlScore score = scorer.score(new MlFeaturesRequest(
                new BigDecimal("250"), "2026-09-15T12:00:00",
                new BigDecimal("200"), new BigDecimal("50"), "2026-09-10T00:00:00",
                25, 40, new BigDecimal("180"), 12, true, true));

        assertTrue(score.riskScore() >= 50, "expected HOLD-level anomaly, was " + score.riskScore());
        assertEquals("HOLD_FOR_REVIEW", score.decision());
    }

    @Test
    void shouldBlockForExtremeZScore() {
        MlFeaturesRequest req = new MlFeaturesRequest(
                new BigDecimal("50000"), "2026-09-15T12:00:00",
                new BigDecimal("200"), new BigDecimal("3"), "2026-03-01T00:00:00",
                25, 40, new BigDecimal("180"), 12, true, true);

        MlScore score = scorer.score(req);

        assertTrue(score.riskScore() >= 80, "expected BLOCK-level anomaly, was " + score.riskScore());
        assertEquals("BLOCK", score.decision());
        assertEquals(InProcessAnomalyScorer.MODEL_VERSION, score.modelVersion());
    }

    @Test
    void shouldRewardCrossBorderNewPayee() {
        MlScore score = scorer.score(new MlFeaturesRequest(
                new BigDecimal("150"), "2026-09-15T12:00:00",
                new BigDecimal("200"), new BigDecimal("50"), "2026-03-01T00:00:00",
                0, 0, new BigDecimal("180"), 0, true, true));

        assertTrue(score.riskScore() >= 15);
    }

    @Test
    void shouldFlagOddHours() {
        MlScore score = scorer.score(request(
                new BigDecimal("150"), "2026-09-15T03:00:00", "2026-03-01T00:00:00", 0, 0));

        assertEquals(10, score.riskScore());
    }
}