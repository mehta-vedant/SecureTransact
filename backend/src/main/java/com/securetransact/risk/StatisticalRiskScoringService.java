package com.securetransact.risk;

import com.securetransact.model.*;
import com.securetransact.repository.FraudBlacklistRepository;
import com.securetransact.repository.FraudRuleConfigRepository;
import com.securetransact.repository.UserBehaviorProfileRepository;
import com.securetransact.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatisticalRiskScoringService {

    private final FraudRuleConfigRepository ruleConfigRepository;
    private final UserBehaviorProfileRepository behaviorProfileRepository;
    private final FraudBlacklistRepository blacklistRepository;
    private final TransactionRepository transactionRepository;

    private static final BigDecimal Z_SCORE_THRESHOLD = new BigDecimal("3.0");
    private static final MathContext MATH_CTX = new MathContext(10, RoundingMode.HALF_UP);

    public RiskScoringResult scoreTransaction(Transaction transaction, Account sourceAccount) {
        List<RiskFactor> factors = new ArrayList<>();
        int totalScore = 0;

        totalScore += evaluateBlacklistChecks(transaction, sourceAccount, factors);
        totalScore += evaluatePolicyRules(transaction, sourceAccount, factors);
        totalScore += evaluateBehavioralAnomaly(transaction, sourceAccount, factors);
        totalScore += evaluateVelocityAnomaly(transaction, sourceAccount, factors);
        totalScore += evaluateTemporalAnomaly(transaction, sourceAccount, factors);

        totalScore = Math.min(totalScore, 100);

        return RiskScoringResult.builder()
                .totalScore(totalScore)
                .riskLevel(determineRiskLevel(totalScore))
                .factors(factors)
                .modelVersion("statistical-risk-v1")
                .build();
    }

    private int evaluateBlacklistChecks(Transaction transaction, Account sourceAccount, List<RiskFactor> factors) {
        int score = 0;

        if (sourceAccount != null) {
            boolean sourceBlocked = blacklistRepository
                    .existsByTypeAndValueAndActiveTrue(BlacklistType.ACCOUNT_NUMBER, sourceAccount.getAccountNumber());
            if (sourceBlocked) {
                score += 50;
                factors.add(RiskFactor.builder()
                        .code("BLACKLISTED_SOURCE_ACCOUNT")
                        .points(50)
                        .message("Source account is on the blacklist")
                        .build());
            }
        }

        if (transaction.getToAccount() != null) {
            boolean destBlocked = blacklistRepository
                    .existsByTypeAndValueAndActiveTrue(BlacklistType.ACCOUNT_NUMBER, transaction.getToAccount().getAccountNumber());
            if (destBlocked) {
                score += 50;
                factors.add(RiskFactor.builder()
                        .code("BLACKLISTED_DEST_ACCOUNT")
                        .points(50)
                        .message("Destination account is on the blacklist")
                        .build());
            }
        }

        return score;
    }

    private int evaluatePolicyRules(Transaction transaction, Account sourceAccount, List<RiskFactor> factors) {
        int score = 0;
        List<FraudRuleConfig> enabledRules = ruleConfigRepository.findByEnabledTrueOrderByPriorityAsc();

        for (FraudRuleConfig rule : enabledRules) {
            int ruleScore = evaluateSinglePolicyRule(rule, transaction, sourceAccount);
            if (ruleScore > 0) {
                score += ruleScore;
                factors.add(RiskFactor.builder()
                        .code(rule.getRuleName())
                        .points(ruleScore)
                        .message(rule.getDescription())
                        .build());
            }
        }

        return score;
    }

    private int evaluateSinglePolicyRule(FraudRuleConfig rule, Transaction transaction, Account sourceAccount) {
        String ruleName = rule.getRuleName();

        return switch (ruleName) {
            case "LARGE_AMOUNT" -> {
                BigDecimal threshold = rule.getThresholdNumeric() != null
                        ? rule.getThresholdNumeric() : new BigDecimal("50000");
                yield transaction.getAmount().compareTo(threshold) > 0 ? rule.getScoreWeight() : 0;
            }
            case "NEW_ACCOUNT_LARGE_TXN" -> {
                if (sourceAccount == null) yield 0;
                BigDecimal threshold = rule.getThresholdNumeric() != null
                        ? rule.getThresholdNumeric() : new BigDecimal("10000");
                int daysThreshold = rule.getThresholdInteger() != null ? rule.getThresholdInteger() : 7;
                boolean isNew = sourceAccount.getCreatedAt().isAfter(LocalDateTime.now().minusDays(daysThreshold));
                boolean isLarge = transaction.getAmount().compareTo(threshold) > 0;
                yield (isNew && isLarge) ? rule.getScoreWeight() : 0;
            }
            case "HIGH_VELOCITY" -> {
                if (sourceAccount == null) yield 0;
                int windowMinutes = rule.getThresholdInteger() != null ? rule.getThresholdInteger() : 1;
                int maxCount = rule.getThresholdNumeric() != null
                        ? rule.getThresholdNumeric().intValue() : 5;
                long count = transactionRepository.countRecentTransactions(
                        sourceAccount.getId(), LocalDateTime.now().minusMinutes(windowMinutes));
                yield count >= maxCount ? rule.getScoreWeight() : 0;
            }
            case "RAPID_TRANSFER" -> {
                if (sourceAccount == null || transaction.getToAccount() == null) yield 0;
                int windowMinutes = rule.getThresholdInteger() != null ? rule.getThresholdInteger() : 10;
                int maxCount = rule.getThresholdNumeric() != null
                        ? rule.getThresholdNumeric().intValue() : 3;
                long count = transactionRepository.countRecentTransfersToAccount(
                        sourceAccount.getId(), transaction.getToAccount().getId(),
                        LocalDateTime.now().minusMinutes(windowMinutes));
                yield count >= maxCount ? rule.getScoreWeight() : 0;
            }
            case "ODD_HOURS" -> {
                int startHour = rule.getThresholdInteger() != null ? rule.getThresholdInteger() : 1;
                int endHour = rule.getThresholdNumeric() != null
                        ? rule.getThresholdNumeric().intValue() : 5;
                LocalTime now = LocalTime.now();
                boolean isOddHour = now.isAfter(LocalTime.of(startHour, 0))
                        && now.isBefore(LocalTime.of(endHour, 0));
                yield isOddHour ? rule.getScoreWeight() : 0;
            }
            default -> 0;
        };
    }

    private int evaluateBehavioralAnomaly(Transaction transaction, Account sourceAccount, List<RiskFactor> factors) {
        if (sourceAccount == null) return 0;

        return behaviorProfileRepository.findByUserId(sourceAccount.getUser().getId())
                .filter(UserBehaviorProfile::isHasBaseline)
                .map(profile -> {
                    int score = 0;
                    BigDecimal txnAmount = transaction.getAmount();

                    if (profile.getStddevTransactionAmount().compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal deviation = txnAmount.subtract(profile.getAvgTransactionAmount()).abs();
                        BigDecimal zScore = deviation.divide(profile.getStddevTransactionAmount(), MATH_CTX);

                        if (zScore.compareTo(Z_SCORE_THRESHOLD) > 0) {
                            int points = Math.min(zScore.intValue() * 10, 30);
                            score += points;
                            factors.add(RiskFactor.builder()
                                    .code("UNUSUAL_AMOUNT")
                                    .points(points)
                                    .message(String.format("Amount is %.1f standard deviations from your average", zScore))
                                    .build());
                        }
                    }

                    int hour = LocalDateTime.now().getHour();
                    int typicalStart = profile.getTypicalStartHour().intValue();
                    int typicalEnd = profile.getTypicalEndHour().intValue();
                    boolean isTypicalHour = (hour >= typicalStart && hour < typicalEnd);
                    if (!isTypicalHour && profile.getTotalTransactionCount() > 10) {
                        score += 15;
                        factors.add(RiskFactor.builder()
                                .code("UNUSUAL_HOUR")
                                .points(15)
                                .message("Transaction at unusual time for this user")
                                .build());
                    }

                    return score;
                })
                .orElse(0);
    }

    private int evaluateVelocityAnomaly(Transaction transaction, Account sourceAccount, List<RiskFactor> factors) {
        if (sourceAccount == null) return 0;

        long recentCount = transactionRepository.countRecentTransactions(
                sourceAccount.getId(), LocalDateTime.now().minusMinutes(60));

        if (recentCount > 10) {
            int points = Math.min((int)(recentCount - 10) * 5, 25);
            factors.add(RiskFactor.builder()
                    .code("HIGH_VELOCITY_ANOMALY")
                    .points(points)
                    .message(String.format("%d transactions in the last hour", recentCount))
                    .build());
            return points;
        }

        return 0;
    }

    private int evaluateTemporalAnomaly(Transaction transaction, Account sourceAccount, List<RiskFactor> factors) {
        int hour = LocalDateTime.now().getHour();

        if (hour >= 1 && hour <= 5) {
            factors.add(RiskFactor.builder()
                    .code("ODD_HOURS_ANOMALY")
                    .points(15)
                    .message("Transaction during high-risk time window (1-5 AM)")
                    .build());
            return 15;
        }

        return 0;
    }

    public static RiskLevel determineRiskLevel(int score) {
        if (score >= 76) return RiskLevel.CRITICAL;
        if (score >= 51) return RiskLevel.HIGH;
        if (score >= 21) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }
}
