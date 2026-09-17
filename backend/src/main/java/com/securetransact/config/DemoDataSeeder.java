package com.securetransact.config;

import com.securetransact.model.*;
import com.securetransact.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds the demo environment used by the live fraud-detection demo: a victim user with a
 * realistic balance and a behavioral baseline, a merchant destination account, and a
 * blacklisted account. Idempotent and gated behind {@code app.demo.enabled} so it never
 * runs in tests or a non-demo deployment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final UserBehaviorProfileRepository profileRepository;
    private final FraudBlacklistRepository blacklistRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.demo.enabled:false}")
    private boolean demoEnabled;

    @Value("${app.demo.source-email:alice@demo.securetransact.app}")
    private String sourceEmail;

    @Value("${app.demo.source-password:demo-alice-1234}")
    private String sourcePassword;

    @Value("${app.demo.merchant-email:merchant@demo.securetransact.app}")
    private String merchantEmail;

    @Value("${app.demo.blocked-email:blocked@demo.securetransact.app}")
    private String blockedEmail;

    @Value("${app.demo.blocked-account:ST0000000000}")
    private String blockedAccountNumber;

    @Override
    public void run(String... args) {
        if (!demoEnabled) {
            log.info("Demo data seeding disabled (app.demo.enabled=false)");
            return;
        }
        if (userRepository.existsByEmail(sourceEmail)) {
            log.info("Demo data already seeded; skipping");
            return;
        }

        User alice = userRepository.save(User.builder()
                .firstName("Alice")
                .lastName("Demo")
                .email(sourceEmail)
                .password(passwordEncoder.encode(sourcePassword))
                .role(Role.USER)
                .build());

        User merchant = userRepository.save(User.builder()
                .firstName("Merchant")
                .lastName("Demo")
                .email(merchantEmail)
                .password(passwordEncoder.encode("merchant-demo-5678"))
                .role(Role.USER)
                .build());

        User blockedOwner = userRepository.save(User.builder()
                .firstName("Blocked")
                .lastName("Demo")
                .email(blockedEmail)
                .password(passwordEncoder.encode("blocked-demo-9999"))
                .role(Role.USER)
                .build());

        Account aliceSavings = saveAccount(alice, "SAVINGS", "150000.00");
        Account aliceChecking = saveAccount(alice, "CHECKING", "12000.00");
        Account merchantAccount = saveAccount(merchant, "CHECKING", "5000.00");
        Account blockedAccount = saveAccount(blockedOwner, "SAVINGS", "100.00");
        repointAccountNumber(blockedAccount, blockedAccountNumber);

        backdateCreatedAt(aliceSavings.getId(), 120);
        backdateCreatedAt(aliceChecking.getId(), 120);
        backdateCreatedAt(merchantAccount.getId(), 365);

        profileRepository.save(UserBehaviorProfile.builder()
                .user(alice)
                .avgTransactionAmount(new BigDecimal("250.00"))
                .stddevTransactionAmount(new BigDecimal("60.00"))
                .totalTransactionCount(150)
                .transactionsLast24h(5)
                .transactionsLastHour(0)
                .typicalStartHour(new BigDecimal("9"))
                .typicalEndHour(new BigDecimal("21"))
                .hasBaseline(true)
                .build());

        if (!blacklistRepository.existsByTypeAndValueAndActiveTrue(
                BlacklistType.ACCOUNT_NUMBER, blockedAccountNumber)) {
            blacklistRepository.save(FraudBlacklist.builder()
                    .type(BlacklistType.ACCOUNT_NUMBER)
                    .value(blockedAccountNumber)
                    .reason("Demo: destination controlled by fraudster")
                    .active(true)
                    .build());
        }

        log.info("Seeded demo data: alice={}, merchant={}, blocked={}",
                alice.getId(), merchant.getId(), blockedAccount.getId());
    }

    private Account saveAccount(User owner, String type, String balance) {
        Account account = Account.builder()
                .user(owner)
                .accountNumber("ST" + java.util.UUID.randomUUID().toString().replaceAll("-", "").substring(0, 10).toUpperCase())
                .accountType(AccountType.valueOf(type))
                .balance(new BigDecimal(balance))
                .status(AccountStatus.ACTIVE)
                .build();
        return accountRepository.save(account);
    }

    private void repointAccountNumber(Account account, String accountNumber) {
        jdbcTemplate.update("UPDATE accounts SET account_number = ? WHERE id = ?",
                accountNumber, account.getId());
    }

    private void backdateCreatedAt(Long accountId, int days) {
        jdbcTemplate.update(
                "UPDATE accounts SET created_at = NOW() - (? || ' days')::interval WHERE id = ?",
                days, accountId);
    }
}