package com.securetransact.ml;

import com.securetransact.model.Account;
import com.securetransact.model.Transaction;
import com.securetransact.model.UserBehaviorProfile;
import com.securetransact.repository.TransactionRepository;
import com.securetransact.repository.UserBehaviorProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MlFeatureCollector {

    private final TransactionRepository transactionRepository;
    private final UserBehaviorProfileRepository behaviorProfileRepository;

    public MlFeaturesRequest collect(Transaction transaction, Account sourceAccount) {
        LocalDateTime now = LocalDateTime.now();
        Long accountId = sourceAccount != null ? sourceAccount.getId() : null;

        Optional<UserBehaviorProfile> profile = accountId != null && sourceAccount.getUser() != null
                ? behaviorProfileRepository.findByUserId(sourceAccount.getUser().getId())
                : Optional.empty();

        long txnCount1h = accountId != null
                ? transactionRepository.countRecentTransactions(accountId, now.minusHours(1)) : 0;
        long txnCount24h = accountId != null
                ? transactionRepository.countRecentTransactions(accountId, now.minusHours(24)) : 0;
        BigDecimal avgAmount7d = accountId != null
                ? transactionRepository.avgAmountSince(accountId, now.minusDays(7)) : BigDecimal.ZERO;
        long uniqueRecipients24h = accountId != null
                ? transactionRepository.countDistinctRecipientsSince(accountId, now.minusHours(24)) : 0;
        boolean isNewPayee = accountId != null && transaction.getToAccount() != null
                ? transactionRepository.countTransfersToAccount(accountId, transaction.getToAccount().getId()) == 0
                : false;

        return new MlFeaturesRequest(
                transaction.getAmount(),
                transaction.getCreatedAt() != null ? transaction.getCreatedAt().toString() : now.toString(),
                profile.map(UserBehaviorProfile::getAvgTransactionAmount).orElse(BigDecimal.ZERO),
                profile.map(UserBehaviorProfile::getStddevTransactionAmount)
                        .filter(std -> std.compareTo(BigDecimal.ZERO) > 0)
                        .orElse(BigDecimal.ONE),
                sourceAccount != null && sourceAccount.getCreatedAt() != null
                        ? sourceAccount.getCreatedAt().toString() : now.toString(),
                txnCount1h,
                txnCount24h,
                avgAmount7d,
                uniqueRecipients24h,
                transaction.isCrossBorder(),
                isNewPayee);
    }
}