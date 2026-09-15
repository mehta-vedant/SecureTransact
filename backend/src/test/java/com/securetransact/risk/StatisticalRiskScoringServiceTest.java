package com.securetransact.risk;

import com.securetransact.model.*;
import com.securetransact.repository.FraudBlacklistRepository;
import com.securetransact.repository.FraudRuleConfigRepository;
import com.securetransact.repository.UserBehaviorProfileRepository;
import com.securetransact.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticalRiskScoringServiceTest {

    @Mock private FraudRuleConfigRepository ruleConfigRepository;
    @Mock private UserBehaviorProfileRepository behaviorProfileRepository;
    @Mock private FraudBlacklistRepository blacklistRepository;
    @Mock private TransactionRepository transactionRepository;

    @InjectMocks
    private StatisticalRiskScoringService scoringService;

    private Account testAccount;
    private Transaction smallTransaction;
    private Transaction largeTransaction;

    @BeforeEach
    void setUp() {
        User user = User.builder().id(1L).email("test@test.com").build();

        testAccount = Account.builder()
                .id(1L)
                .accountNumber("ACC-001")
                .balance(new BigDecimal("100000"))
                .status(AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now().minusMonths(6))
                .user(user)
                .build();

        smallTransaction = Transaction.builder()
                .amount(new BigDecimal("50"))
                .type(TransactionType.DEPOSIT)
                .build();

        largeTransaction = Transaction.builder()
                .amount(new BigDecimal("75000"))
                .type(TransactionType.WITHDRAWAL)
                .build();
    }

    private void enableDefaultRules() {
        List<FraudRuleConfig> rules = List.of(
                FraudRuleConfig.builder().ruleName("LARGE_AMOUNT").enabled(true)
                        .scoreWeight(30).priority(1).thresholdNumeric(new BigDecimal("50000")).build(),
                FraudRuleConfig.builder().ruleName("HIGH_VELOCITY").enabled(true)
                        .scoreWeight(25).priority(3).thresholdInteger(1).thresholdNumeric(new BigDecimal("5")).build(),
                FraudRuleConfig.builder().ruleName("ODD_HOURS").enabled(true)
                        .scoreWeight(15).priority(5).thresholdInteger(1).thresholdNumeric(new BigDecimal("5")).build()
        );
        when(ruleConfigRepository.findByEnabledTrueOrderByPriorityAsc()).thenReturn(rules);
    }

    @Test
    void shouldReturnLowRiskForSmallTransaction() {
        enableDefaultRules();
        when(blacklistRepository.existsByTypeAndValueAndActiveTrue(any(), anyString())).thenReturn(false);
        when(behaviorProfileRepository.findByUserId(anyLong())).thenReturn(Optional.empty());
        when(transactionRepository.countRecentTransactions(anyLong(), any())).thenReturn(0L);

        RiskScoringResult result = scoringService.scoreTransaction(smallTransaction, testAccount);

        assertNotNull(result);
        assertEquals(RiskLevel.LOW, result.getRiskLevel());
        assertTrue(result.getTotalScore() < 21);
    }

    @Test
    void shouldFlagLargeAmount() {
        enableDefaultRules();
        when(blacklistRepository.existsByTypeAndValueAndActiveTrue(any(), anyString())).thenReturn(false);
        when(behaviorProfileRepository.findByUserId(anyLong())).thenReturn(Optional.empty());
        when(transactionRepository.countRecentTransactions(anyLong(), any())).thenReturn(0L);

        RiskScoringResult result = scoringService.scoreTransaction(largeTransaction, testAccount);

        assertTrue(result.getTotalScore() >= 30);
        assertTrue(result.getFactors().stream()
                .anyMatch(f -> f.getCode().equals("LARGE_AMOUNT")));
    }

    @Test
    void shouldFlagHighVelocity() {
        enableDefaultRules();
        when(blacklistRepository.existsByTypeAndValueAndActiveTrue(any(), anyString())).thenReturn(false);
        when(behaviorProfileRepository.findByUserId(anyLong())).thenReturn(Optional.empty());
        when(transactionRepository.countRecentTransactions(anyLong(), any())).thenReturn(6L);

        RiskScoringResult result = scoringService.scoreTransaction(smallTransaction, testAccount);

        assertTrue(result.getTotalScore() >= 25);
        assertTrue(result.getFactors().stream()
                .anyMatch(f -> f.getCode().equals("HIGH_VELOCITY")));
    }

    @Test
    void shouldFlagBlacklistedSourceAccount() {
        enableDefaultRules();
        when(blacklistRepository.existsByTypeAndValueAndActiveTrue(
                eq(BlacklistType.ACCOUNT_NUMBER), eq("ACC-001"))).thenReturn(true);
        when(behaviorProfileRepository.findByUserId(anyLong())).thenReturn(Optional.empty());
        when(transactionRepository.countRecentTransactions(anyLong(), any())).thenReturn(0L);

        RiskScoringResult result = scoringService.scoreTransaction(smallTransaction, testAccount);

        assertTrue(result.getTotalScore() >= 50);
        assertTrue(result.getFactors().stream()
                .anyMatch(f -> f.getCode().equals("BLACKLISTED_SOURCE_ACCOUNT")));
    }

    @Test
    void shouldFlagBlacklistedDestinationAccount() {
        enableDefaultRules();
        when(blacklistRepository.existsByTypeAndValueAndActiveTrue(
                eq(BlacklistType.ACCOUNT_NUMBER), anyString())).thenReturn(false);
        when(blacklistRepository.existsByTypeAndValueAndActiveTrue(
                eq(BlacklistType.ACCOUNT_NUMBER), eq("ACC-BLOCKED"))).thenReturn(true);
        when(behaviorProfileRepository.findByUserId(anyLong())).thenReturn(Optional.empty());
        when(transactionRepository.countRecentTransactions(anyLong(), any())).thenReturn(0L);

        smallTransaction.setToAccount(Account.builder()
                .accountNumber("ACC-BLOCKED")
                .build());

        RiskScoringResult result = scoringService.scoreTransaction(smallTransaction, testAccount);

        assertTrue(result.getTotalScore() >= 50);
        assertTrue(result.getFactors().stream()
                .anyMatch(f -> f.getCode().equals("BLACKLISTED_DEST_ACCOUNT")));
    }

    @Test
    void shouldScoreMultipleFactorsCumulatively() {
        enableDefaultRules();
        when(blacklistRepository.existsByTypeAndValueAndActiveTrue(any(), anyString())).thenReturn(false);
        when(behaviorProfileRepository.findByUserId(anyLong())).thenReturn(Optional.empty());
        // 6 transactions in last hour → triggers HIGH_VELOCITY
        when(transactionRepository.countRecentTransactions(anyLong(), any())).thenReturn(6L);

        RiskScoringResult result = scoringService.scoreTransaction(largeTransaction, testAccount);

        // LARGE_AMOUNT(30) + HIGH_VELOCITY(25) = 55
        assertTrue(result.getTotalScore() >= 55);
        assertTrue(result.getRiskLevel() == RiskLevel.HIGH || result.getRiskLevel() == RiskLevel.CRITICAL);
    }

    @Test
    void shouldReturnCriticalForScoreAbove75() {
        enableDefaultRules();
        when(blacklistRepository.existsByTypeAndValueAndActiveTrue(
                eq(BlacklistType.ACCOUNT_NUMBER), eq("ACC-001"))).thenReturn(true);
        when(behaviorProfileRepository.findByUserId(anyLong())).thenReturn(Optional.empty());
        when(transactionRepository.countRecentTransactions(anyLong(), any())).thenReturn(6L);

        RiskScoringResult result = scoringService.scoreTransaction(largeTransaction, testAccount);

        // BLACKLISTED(50) + LARGE_AMOUNT(30) + HIGH_VELOCITY(25) = capped at 100 → CRITICAL
        assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
        assertEquals(100, result.getTotalScore());
    }
}
