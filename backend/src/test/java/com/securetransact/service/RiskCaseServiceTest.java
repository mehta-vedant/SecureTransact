package com.securetransact.service;

import com.securetransact.dto.RiskCaseDecisionRequest;
import com.securetransact.dto.RiskCaseResponse;
import com.securetransact.exception.ResourceNotFoundException;
import com.securetransact.model.*;
import com.securetransact.repository.RiskCaseRepository;
import com.securetransact.repository.RiskCaseEventRepository;
import com.securetransact.repository.TransactionRepository;
import com.securetransact.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskCaseServiceTest {

    @Mock private RiskCaseRepository riskCaseRepository;
    @Mock private RiskCaseEventRepository riskCaseEventRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private UserRepository userRepository;
    @Mock private TransactionProcessor processor;
    @Mock private LedgerPostingService ledgerPostingService;

    @InjectMocks
    private RiskCaseService riskCaseService;

    private User admin;
    private Transaction transaction;
    private RiskCase sampleCase;

    @BeforeEach
    void setUp() {
        admin = User.builder()
                .id(7L)
                .firstName("Admin")
                .lastName("User")
                .email("admin@test.com")
                .role(Role.ADMIN)
                .build();
        transaction = Transaction.builder()
                .id(100L)
                .type(TransactionType.TRANSFER)
                .amount(new BigDecimal("1500.00"))
                .status(TransactionStatus.HELD_FOR_REVIEW)
                .build();
        sampleCase = RiskCase.builder()
                .id(1L)
                .transaction(transaction)
                .status(CaseStatus.OPEN)
                .build();
    }

    @Test
    void shouldCreateRiskCase() {
        RiskEvaluation evaluation = RiskEvaluation.builder()
                .id(50L)
                .transaction(transaction)
                .totalScore(72)
                .riskLevel(RiskLevel.HIGH)
                .decision(RiskDecision.HOLD_FOR_REVIEW)
                .modelVersion("statistical-risk-v1")
                .build();

        when(riskCaseRepository.save(any(RiskCase.class))).thenAnswer(inv -> {
            RiskCase rc = inv.getArgument(0);
            rc.setId(2L);
            return rc;
        });

        RiskCase created = riskCaseService.createRiskCase(transaction, evaluation);

        assertNotNull(created);
        assertEquals(2L, created.getId());
        assertEquals(CaseStatus.OPEN, created.getStatus());
        assertEquals(transaction, created.getTransaction());
        assertEquals(evaluation, created.getRiskEvaluation());
        verify(riskCaseRepository).save(any(RiskCase.class));
    }

    @Test
    void shouldAssignCase() {
        when(riskCaseRepository.findById(1L)).thenReturn(Optional.of(sampleCase));
        when(userRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(riskCaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RiskCaseResponse assigned = riskCaseService.assignCase(1L, 7L);

        assertEquals(CaseStatus.IN_REVIEW, assigned.getStatus());
        assertEquals(7L, assigned.getAssignedToId());
        assertEquals("Admin User", assigned.getAssignedToName());
    }

    @Test
    void shouldApproveCase() {
        when(riskCaseRepository.findById(1L)).thenReturn(Optional.of(sampleCase));
        when(userRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(riskCaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(processor.processMoneyMovement(transaction)).thenReturn(TransactionStatus.SETTLED);

        RiskCaseDecisionRequest request = new RiskCaseDecisionRequest();
        request.setDecision(RiskDecision.ALLOW);
        request.setReviewNotes("Legitimate large transfer");

        RiskCaseResponse resolved = riskCaseService.decideCase(1L, request, 7L);

        assertEquals(CaseStatus.APPROVED, resolved.getStatus());
        assertEquals("Legitimate large transfer", resolved.getReviewNotes());
        assertEquals(RiskDecision.ALLOW, resolved.getAdminDecision());
        assertEquals(TransactionStatus.SETTLED, transaction.getStatus());
        verify(processor).processMoneyMovement(transaction);
        verify(transactionRepository).save(sampleCase.getTransaction());
    }

    @Test
    void shouldBlockCase() {
        when(riskCaseRepository.findById(1L)).thenReturn(Optional.of(sampleCase));
        when(userRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(riskCaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RiskCaseDecisionRequest request = new RiskCaseDecisionRequest();
        request.setDecision(RiskDecision.BLOCK);
        request.setReviewNotes("Confirmed fraud");

        RiskCaseResponse resolved = riskCaseService.decideCase(1L, request, 7L);

        assertEquals(CaseStatus.REJECTED, resolved.getStatus());
        assertEquals("Confirmed fraud", resolved.getReviewNotes());
        assertEquals(RiskDecision.BLOCK, resolved.getAdminDecision());
        assertEquals(TransactionStatus.REJECTED, transaction.getStatus());
    }

    @Test
    void shouldEscalateCase() {
        when(riskCaseRepository.findById(1L)).thenReturn(Optional.of(sampleCase));
        when(userRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(riskCaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RiskCaseDecisionRequest request = new RiskCaseDecisionRequest();
        request.setDecision(RiskDecision.HOLD_FOR_REVIEW);
        request.setReviewNotes("Escalate to senior review");

        RiskCaseResponse resolved = riskCaseService.decideCase(1L, request, 7L);

        assertEquals(CaseStatus.ESCALATED, resolved.getStatus());
        assertEquals(TransactionStatus.HELD_FOR_REVIEW, transaction.getStatus());
    }

    @Test
    void shouldThrowWhenCaseNotFound() {
        when(riskCaseRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                riskCaseService.assignCase(999L, 7L));
    }
}
