package com.securetransact.integration;

import com.securetransact.dto.RiskCaseDecisionRequest;
import com.securetransact.dto.RiskCaseResponse;
import com.securetransact.dto.TransactionRequest;
import com.securetransact.dto.TransactionResponse;
import com.securetransact.model.*;
import com.securetransact.repository.*;
import com.securetransact.service.RiskCaseService;
import com.securetransact.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests against a real PostgreSQL container: Flyway applies V1-V5,
 * Hibernate validates the schema (ddl-auto=validate), and the full service
 * pipeline (validate -> risk -> decision -> settle / hold / block) runs against
 * real persisted data.
 */
@SpringBootTest
@ActiveProfiles("it")
@Testcontainers(disabledWithoutDocker = true)
class SecureTransactIntegrationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private FraudBlacklistRepository blacklistRepository;
    @Autowired
    private RiskEvaluationRepository riskEvaluationRepository;
    @Autowired
    private RiskCaseRepository riskCaseRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;
    @Autowired
    private TransactionService transactionService;
    @Autowired
    private RiskCaseService riskCaseService;

    private User payer;
    private User payee;
    private Account from;
    private Account to;

    @BeforeEach
    void setUp() {
        payer = createUser("payer-" + n() + "@it.test", Role.USER);
        payee = createUser("payee-" + n() + "@it.test", Role.USER);
        from = createAccount(payer, accountNumber(), AccountType.CHECKING, new BigDecimal("10000.00"));
        to = createAccount(payee, accountNumber(), AccountType.CHECKING, new BigDecimal("5000.00"));
    }

    @Test
    void shouldApplyMigrationsAndAutoSettleLowRiskTransfer() {
        TransactionRequest request = transfer(from.getId(), to.getId(), "1000.00", key());

        TransactionResponse response = transactionService.submitTransaction(payer.getId(), request);

        assertEquals(TransactionStatus.SETTLED, response.getStatus());
        assertTrue(response.getRiskScore() != null);

        Transaction persisted = transactionRepository.findById(response.getId()).orElseThrow();
        assertEquals(TransactionStatus.SETTLED, persisted.getStatus());
        assertEquals(new BigDecimal("9000.00"), accountRepository.findById(from.getId()).orElseThrow().getBalance());
        assertEquals(new BigDecimal("6000.00"), accountRepository.findById(to.getId()).orElseThrow().getBalance());

        RiskEvaluation evaluation = riskEvaluationRepository.findByTransactionId(response.getId()).orElseThrow();
        assertEquals(RiskDecision.ALLOW, evaluation.getDecision());
        assertEquals("statistical-risk-v1", evaluation.getModelVersion());

        assertFalse(auditEventRepository
                .findByResourceTypeAndResourceId("TRANSACTION", response.getId(), PageRequest.of(0, 10))
                .isEmpty());
    }

    @Test
    void shouldBlockTransactionWhenBothAccountsBlacklisted() {
        blacklistAccountNumber(from);
        blacklistAccountNumber(to);

        TransactionRequest request = transfer(from.getId(), to.getId(), "500.00", key());

        TransactionResponse response = transactionService.submitTransaction(payer.getId(), request);

        assertEquals(TransactionStatus.REJECTED, response.getStatus());
        assertEquals(RiskDecision.BLOCK, RiskDecision.valueOf(response.getRiskDecision()));
        assertTrue(response.getRiskScore() >= 76);
    }

    @Test
    void shouldHoldRiskCaseAndApproveToSettle() {
        blacklistAccountNumber(to);

        TransactionRequest request = transfer(from.getId(), to.getId(), "500.00", key());

        TransactionResponse response = transactionService.submitTransaction(payer.getId(), request);

        assertEquals(TransactionStatus.HELD_FOR_REVIEW, response.getStatus());

        Transaction held = transactionRepository.findById(response.getId()).orElseThrow();
        RiskEvaluation evaluation = riskEvaluationRepository.findByTransactionId(held.getId()).orElseThrow();
        assertEquals(RiskDecision.HOLD_FOR_REVIEW, evaluation.getDecision());

        RiskCase riskCase = riskCaseRepository.findByTransactionId(held.getId()).orElseThrow();
        assertEquals(CaseStatus.OPEN, riskCase.getStatus());

        User admin = createUser("admin-" + n() + "@it.test", Role.ADMIN);
        RiskCaseResponse assigned = riskCaseService.assignCase(riskCase.getId(), admin.getId());
        assertEquals(CaseStatus.IN_REVIEW, assigned.getStatus());

        RiskCaseDecisionRequest approve = new RiskCaseDecisionRequest();
        approve.setDecision(RiskDecision.ALLOW);
        approve.setReviewNotes("Legitimate transfer after manual review");

        RiskCaseResponse resolved = riskCaseService.decideCase(riskCase.getId(), approve, admin.getId());

        assertEquals(CaseStatus.APPROVED, resolved.getStatus());
        assertEquals(TransactionStatus.SETTLED,
                transactionRepository.findById(held.getId()).orElseThrow().getStatus());
        assertEquals(new BigDecimal("9500.00"), accountRepository.findById(from.getId()).orElseThrow().getBalance());
    }

    @Test
    void shouldDeduplicateIdempotentSubmission() {
        TransactionRequest request = transfer(from.getId(), to.getId(), "250.00", "dup-key-1");

        TransactionResponse first = transactionService.submitTransaction(payer.getId(), request);
        TransactionResponse second = transactionService.submitTransaction(payer.getId(), request);

        assertEquals(first.getId(), second.getId());
        assertEquals(1L,
                transactionRepository.findByIdempotencyKey("dup-key-1").isPresent() ? 1L : 0L);
        assertEquals(new BigDecimal("9750.00"), accountRepository.findById(from.getId()).orElseThrow().getBalance());
    }

    private TransactionRequest transfer(Long fromId, Long toId, String amount, String key) {
        TransactionRequest request = new TransactionRequest();
        request.setType(TransactionType.TRANSFER);
        request.setFromAccountId(fromId);
        request.setToAccountId(toId);
        request.setAmount(new BigDecimal(amount));
        request.setDescription("Integration test transfer");
        request.setIdempotencyKey(key);
        return request;
    }

    private User createUser(String email, Role role) {
        return userRepository.save(User.builder()
                .email(email)
                .password("$2a$10$it-test-only")
                .firstName("IT")
                .lastName("User")
                .role(role)
                .build());
    }

    private Account createAccount(User user, String number, AccountType type, BigDecimal balance) {
        return accountRepository.save(Account.builder()
                .user(user)
                .accountNumber(number)
                .accountType(type)
                .balance(balance)
                .status(AccountStatus.ACTIVE)
                .build());
    }

    private void blacklistAccountNumber(Account account) {
        blacklistRepository.save(FraudBlacklist.builder()
                .type(BlacklistType.ACCOUNT_NUMBER)
                .value(account.getAccountNumber())
                .reason("Integration test blacklist entry")
                .active(true)
                .build());
    }

    private static long counter = 0;

    private static synchronized Long n() {
        return ++counter;
    }

    private static synchronized String accountNumber() {
        return "ACC-IT-NUM-" + n();
    }

    private static synchronized String key() {
        return "it-key-" + n();
    }
}