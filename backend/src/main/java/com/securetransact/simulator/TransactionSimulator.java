package com.securetransact.simulator;

import com.securetransact.dto.TransactionRequest;
import com.securetransact.dto.TransactionResponse;
import com.securetransact.model.Account;
import com.securetransact.model.AccountStatus;
import com.securetransact.model.AccountType;
import com.securetransact.model.TransactionType;
import com.securetransact.model.User;
import com.securetransact.repository.AccountRepository;
import com.securetransact.repository.UserRepository;
import com.securetransact.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Drives the live fraud-detection demo. Emits realistic ambient traffic on a schedule
 * (ALLOW-level), and exposes named scenario triggers that burst transactions through the
 * real {@link TransactionService} pipeline so policy rules and the anomaly scorer fire
 * genuinely (HOLD -> risk case -> admin review -> audit). Gated by {@code app.demo.enabled}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionSimulator {

    private final TransactionService transactionService;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;

    @Value("${app.demo.enabled:false}")
    private boolean demoEnabled;

    @Value("${app.demo.source-email:alice@demo.securetransact.app}")
    private String sourceEmail;

    @Value("${app.demo.merchant-email:merchant@demo.securetransact.app}")
    private String merchantEmail;

    @Scheduled(fixedDelayString = "${app.simulator.ambient-interval-ms:8000}")
    public void emitAmbientTraffic() {
        if (!demoEnabled) return;
        Account source = aliceSavingsAccount();
        Account dest = merchantAccount();
        if (source == null || dest == null) return;

        BigDecimal amount = randomAmount("65.00", "180.00");
        TransactionResponse response = submit(source, dest, amount, "Purchase at local merchant");
        log.info("[demo] ambient txn {} -> {} (${}) status={} riskScore={}",
                source.getAccountNumber(), dest.getAccountNumber(), amount,
                response.getStatus(), response.getRiskScore());
    }

    public List<TransactionResponse> triggerRapidTransferBurst() {
        Account source = aliceSavingsAccount();
        Account dest = merchantAccount();
        if (source == null || dest == null) return List.of();
        return transferBurst(source, dest, 6, "50.00", "600.00");
    }

    public List<TransactionResponse> triggerHighVelocityBurst() {
        Account source = aliceCheckingAccount();
        if (source == null) return List.of();
        List<TransactionResponse> responses = new ArrayList<>();
        Account dest = merchantAccount();
        if (dest == null) return List.of();
        for (int i = 0; i < 12; i++) {
            responses.add(submit(source, dest, randomAmount("25.00", "120.00"), "Micro-payment #" + (i + 1)));
        }
        return responses;
    }

    public List<TransactionResponse> triggerLargeAmount() {
        Account source = aliceSavingsAccount();
        if (source == null) return List.of();
        Account dest = merchantAccount();
        if (dest == null) return List.of();
        return List.of(submit(source, dest, new BigDecimal("75000.00"),
                "Large wire to vendor"));
    }

    public List<TransactionResponse> triggerNewAccountLargeTxn() {
        User alice = userRepository.findByEmail(sourceEmail).orElse(null);
        if (alice == null) return List.of();
        Account dest = merchantAccount();
        if (dest == null) return List.of();

        Account fresh = accountRepository.save(Account.builder()
                .user(alice)
                .accountNumber("ST" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 10).toUpperCase())
                .accountType(AccountType.SAVINGS)
                .balance(new BigDecimal("250000.00"))
                .status(AccountStatus.ACTIVE)
                .build());
        log.info("[demo] created fresh account {} for new-account scenario", fresh.getAccountNumber());

        return transferBurst(fresh, dest, 3, "20000.00", "22000.00");
    }

    public List<TransactionResponse> triggerBlacklistTransfer() {
        Account source = aliceSavingsAccount();
        if (source == null) return List.of();
        Account blocked = accountRepository.findByAccountNumber("ST0000000000").orElse(null);
        if (blocked == null) return List.of();
        return List.of(submit(source, blocked, new BigDecimal("5000.00"),
                "Payment to flagged destination"));
    }

    private List<TransactionResponse> transferBurst(Account source, Account dest,
                                                    int count, String min, String max) {
        List<TransactionResponse> responses = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            responses.add(submit(source, dest, randomAmount(min, max), "Rapid transfer #" + (i + 1)));
        }
        return responses;
    }

    private TransactionResponse submit(Account source, Account dest, BigDecimal amount, String description) {
        TransactionRequest request = new TransactionRequest();
        request.setType(TransactionType.TRANSFER);
        request.setAmount(amount);
        request.setFromAccountId(source.getId());
        request.setToAccountId(dest.getId());
        request.setDescription(description);
        request.setIdempotencyKey("demo-" + UUID.randomUUID());
        return transactionService.submitTransaction(source.getUser().getId(), request);
    }

    private BigDecimal randomAmount(String min, String max) {
        BigDecimal lo = new BigDecimal(min);
        BigDecimal hi = new BigDecimal(max);
        BigDecimal scaled = lo.add(hi.subtract(lo)
                .multiply(BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble())));
        return scaled.setScale(2, RoundingMode.HALF_UP);
    }

    private Account aliceSavingsAccount() {
        User alice = userRepository.findByEmail(sourceEmail).orElse(null);
        if (alice == null) return null;
        return accountRepository.findByUserId(alice.getId()).stream()
                .filter(a -> a.getAccountType() == AccountType.SAVINGS && a.getStatus() == AccountStatus.ACTIVE)
                .findFirst().orElse(null);
    }

    private Account aliceCheckingAccount() {
        User alice = userRepository.findByEmail(sourceEmail).orElse(null);
        if (alice == null) return null;
        return accountRepository.findByUserId(alice.getId()).stream()
                .filter(a -> a.getAccountType() == AccountType.CHECKING && a.getStatus() == AccountStatus.ACTIVE)
                .findFirst().orElse(null);
    }

    public boolean isReady() {
        return aliceSavingsAccount() != null && destinationAccountOf(merchantEmail) != null;
    }

    private Account merchantAccount() {
        return destinationAccountOf(merchantEmail);
    }

    private Account destinationAccountOf(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return null;
        return accountRepository.findByUserId(user.getId()).stream()
                .filter(a -> a.getStatus() == AccountStatus.ACTIVE)
                .findFirst().orElse(null);
    }
}
