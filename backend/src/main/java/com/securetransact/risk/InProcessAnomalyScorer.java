package com.securetransact.risk;

import com.securetransact.ml.MlFeaturesRequest;
import com.securetransact.ml.MlScore;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Deterministic, in-process anomaly scorer over the same 11-dim feature vector the
 * remote ML service would consume. Always runs on the transaction path, so scoring is
 * self-contained and testable. A remote ML scorer can still be attached as a secondary
 * signal (see RiskScoringClient) — this scorer is the primary anomaly signal.
 */
@Service
public class InProcessAnomalyScorer {

    static final String MODEL_VERSION = "in-process-anomaly-v1";

    private static final BigDecimal Z_THRESHOLD = new BigDecimal("2.5");
    private static final BigDecimal THREE = new BigDecimal("3");
    private static final BigDecimal FIVE = new BigDecimal("5");

    public MlScore score(MlFeaturesRequest features) {
        int score = 0;
        score += amountDeviation(features);
        score += deviationFromSevenDayAvg(features);
        score += velocity(features);
        score += recipientConcentration(features);
        score += newAccount(features);
        score += payeeNovelty(features);
        score += oddHours(features);
        score = Math.min(score, 100);

        String decision = score >= 80 ? "BLOCK" : score >= 50 ? "HOLD_FOR_REVIEW" : "ALLOW";
        return new MlScore(score, decision, MODEL_VERSION);
    }

    private int amountDeviation(MlFeaturesRequest features) {
        BigDecimal std = features.userStdAmount();
        if (std == null || std.compareTo(BigDecimal.ZERO) <= 0) return 0;

        BigDecimal deviation = features.amount().subtract(features.userMeanAmount()).abs();
        BigDecimal z = deviation.divide(std, 4, RoundingMode.HALF_UP);
        if (z.compareTo(Z_THRESHOLD) <= 0) return 0;

        BigDecimal points = z.subtract(Z_THRESHOLD)
                .multiply(BigDecimal.TEN)
                .setScale(0, RoundingMode.HALF_UP);
        return Math.min(points.intValue(), 30);
    }

    private int deviationFromSevenDayAvg(MlFeaturesRequest features) {
        BigDecimal avg7d = features.avgAmount7d();
        if (avg7d == null || avg7d.compareTo(BigDecimal.ZERO) <= 0) return 0;

        BigDecimal ratio = features.amount().divide(avg7d, 4, RoundingMode.HALF_UP);
        if (ratio.compareTo(THREE) <= 0) return 0;
        if (ratio.compareTo(FIVE) <= 0) return 10;
        return 15;
    }

    private int velocity(MlFeaturesRequest features) {
        int points = 0;
        if (features.txnCount1h() >= 20) {
            points += 20;
        } else if (features.txnCount1h() >= 10) {
            points += 10;
        }
        if (features.txnCount24h() >= 30) {
            points += 5;
        }
        return points;
    }

    private int recipientConcentration(MlFeaturesRequest features) {
        if (features.uniqueRecipients24h() >= 10) return 10;
        if (features.uniqueRecipients24h() >= 6) return 5;
        return 0;
    }

    private int newAccount(MlFeaturesRequest features) {
        if (features.accountCreatedAt() == null) return 0;
        LocalDateTime created = LocalDateTime.parse(features.accountCreatedAt());
        LocalDateTime now = LocalDateTime.parse(features.timestamp());
        long ageDays = ChronoUnit.DAYS.between(created, now);
        boolean isFresh = ageDays < 7;
        boolean isLarge = features.amount().compareTo(new BigDecimal("10000")) > 0;
        return isFresh && isLarge ? 15 : 0;
    }

    private int payeeNovelty(MlFeaturesRequest features) {
        if (features.isNewPayee() && features.isCrossBorder()) return 15;
        if (features.isNewPayee()) return 8;
        return 0;
    }

    private int oddHours(MlFeaturesRequest features) {
        int hour = LocalDateTime.parse(features.timestamp()).getHour();
        return (hour >= 1 && hour < 5) ? 10 : 0;
    }
}