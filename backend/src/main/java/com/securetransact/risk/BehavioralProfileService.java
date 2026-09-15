package com.securetransact.risk;

import com.securetransact.model.Account;
import com.securetransact.model.Transaction;
import com.securetransact.model.UserBehaviorProfile;
import com.securetransact.repository.TransactionRepository;
import com.securetransact.repository.UserBehaviorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BehavioralProfileService {

    private final UserBehaviorProfileRepository profileRepository;
    private final TransactionRepository transactionRepository;

    private static final MathContext MATH_CTX = new MathContext(10, RoundingMode.HALF_UP);

    @Async
    public void updateProfileAfterTransaction(Account sourceAccount, BigDecimal amount) {
        if (sourceAccount == null || sourceAccount.getUser() == null) return;

        Long userId = sourceAccount.getUser().getId();

        UserBehaviorProfile profile = profileRepository.findByUserId(userId)
                .orElse(UserBehaviorProfile.builder()
                        .user(sourceAccount.getUser())
                        .totalTransactionCount(0)
                        .avgTransactionAmount(BigDecimal.ZERO)
                        .stddevTransactionAmount(BigDecimal.ZERO)
                        .transactionsLast24h(0)
                        .transactionsLastHour(0)
                        .typicalStartHour(new BigDecimal("8"))
                        .typicalEndHour(new BigDecimal("22"))
                        .build());

        int n = profile.getTotalTransactionCount();
        BigDecimal oldAvg = profile.getAvgTransactionAmount();
        BigDecimal oldStddev = profile.getStddevTransactionAmount();

        int newCount = n + 1;
        BigDecimal newAvg = oldAvg.multiply(BigDecimal.valueOf(n))
                .add(amount)
                .divide(BigDecimal.valueOf(newCount), MATH_CTX);

        BigDecimal varianceIncrement = amount.subtract(newAvg)
                .multiply(amount.subtract(oldAvg));
        BigDecimal newVariance = oldStddev.pow(2)
                .multiply(BigDecimal.valueOf(n))
                .add(varianceIncrement)
                .divide(BigDecimal.valueOf(newCount), MATH_CTX);
        BigDecimal newStddev = newVariance.compareTo(BigDecimal.ZERO) > 0
                ? sqrt(newVariance) : BigDecimal.ZERO;

        int hour = LocalDateTime.now().getHour();
        BigDecimal currentTypicalStart = profile.getTypicalStartHour();
        if (currentTypicalStart == null) {
            currentTypicalStart = new BigDecimal("8");
            profile.setTypicalStartHour(currentTypicalStart);
        }
        BigDecimal currentTypicalEnd = profile.getTypicalEndHour();
        if (currentTypicalEnd == null) {
            currentTypicalEnd = new BigDecimal("22");
            profile.setTypicalEndHour(currentTypicalEnd);
        }
        BigDecimal weightedHour = BigDecimal.valueOf(hour);
        BigDecimal newStart = currentTypicalStart.multiply(new BigDecimal("0.9"))
                .add(weightedHour.multiply(new BigDecimal("0.1"))).setScale(0, RoundingMode.HALF_UP);
        BigDecimal newEnd = currentTypicalEnd.multiply(new BigDecimal("0.9"))
                .add(weightedHour.multiply(new BigDecimal("0.1"))).setScale(0, RoundingMode.HALF_UP);

        profile.setAvgTransactionAmount(newAvg);
        profile.setStddevTransactionAmount(newStddev);
        profile.setTotalTransactionCount(newCount);
        profile.setTypicalStartHour(newStart);
        profile.setTypicalEndHour(newEnd);
        profile.setHasBaseline(newCount >= 5);
        profile.setLastUpdated(LocalDateTime.now());

        if (sourceAccount.getId() != null) {
            LocalDateTime now = LocalDateTime.now();
            profile.setTransactionsLast24h((int) transactionRepository
                    .countRecentTransactions(sourceAccount.getId(), now.minusHours(24)));
            profile.setTransactionsLastHour((int) transactionRepository
                    .countRecentTransactions(sourceAccount.getId(), now.minusHours(1)));
        }

        profileRepository.save(profile);
        log.debug("Updated behavioral profile for user {}: avg={}, stddev={}, count={}",
                userId, newAvg, newStddev, newCount);
    }

    private BigDecimal sqrt(BigDecimal value) {
        BigDecimal x = value;
        BigDecimal half = new BigDecimal("0.5");
        for (int i = 0; i < 20; i++) {
            x = x.add(value.divide(x, MATH_CTX)).multiply(half);
        }
        return x;
    }
}
