package com.securetransact.service;

import com.securetransact.dto.AccountResponse;
import com.securetransact.dto.AuditEventResponse;
import com.securetransact.dto.DashboardMetricsResponse;
import com.securetransact.dto.PaginatedResponse;
import com.securetransact.dto.TransactionResponse;
import com.securetransact.exception.ConflictException;
import com.securetransact.exception.ResourceNotFoundException;
import com.securetransact.model.*;
import com.securetransact.repository.AccountRepository;
import com.securetransact.repository.RiskCaseRepository;
import com.securetransact.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final RiskCaseRepository riskCaseRepository;
    private final AuditService auditService;
    private final TransactionProcessor processor;

    public DashboardMetricsResponse getDashboardMetrics() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        List<CaseStatus> openCaseStatuses = List.of(CaseStatus.OPEN, CaseStatus.IN_REVIEW, CaseStatus.ESCALATED);
        long openRiskCases = openCaseStatuses.stream().mapToLong(riskCaseRepository::countByStatus).sum();
        long oldestOpenCaseMinutes = riskCaseRepository.findFirstByStatusInOrderByCreatedAtAsc(openCaseStatuses)
                .map(caseItem -> Duration.between(caseItem.getCreatedAt(), LocalDateTime.now()).toMinutes())
                .orElse(0L);

        return DashboardMetricsResponse.builder()
                .totalTransactionsToday(transactionRepository.countTransactionsSince(startOfDay))
                .totalVolumeToday(transactionRepository.sumCompletedAmountSince(startOfDay))
                .flaggedTransactionsToday(transactionRepository.countByStatusSince(TransactionStatus.HELD_FOR_REVIEW, startOfDay))
                .completedTransactionsToday(transactionRepository.countByStatusSince(TransactionStatus.SETTLED, startOfDay))
                .failedTransactionsToday(transactionRepository.countByStatusSince(TransactionStatus.FAILED, startOfDay))
                .activeAccounts(accountRepository.countByStatus(AccountStatus.ACTIVE))
                .openRiskCases(openRiskCases)
                .criticalRiskCases(riskCaseRepository.countByPriorityAndStatusIn(CasePriority.CRITICAL, openCaseStatuses))
                .oldestOpenCaseMinutes(oldestOpenCaseMinutes)
                .build();
    }

    public Page<TransactionResponse> getFlaggedTransactions(Pageable pageable) {
        return transactionRepository.findByStatus(TransactionStatus.HELD_FOR_REVIEW, pageable)
                .map(TransactionResponse::from);
    }

    public Page<AccountResponse> getAllAccounts(Pageable pageable) {
        return accountRepository.findAll(pageable).map(AccountResponse::from);
    }

    @Transactional
    public TransactionResponse reviewTransaction(Long transactionId, String decision, Long adminUserId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        if (transaction.getStatus() != TransactionStatus.HELD_FOR_REVIEW) {
            throw new ConflictException("Only transactions held for review can be reviewed");
        }

        String normalized = decision == null ? "" : decision.trim().toUpperCase();
        switch (normalized) {
            case "APPROVE" -> {
                TransactionStatus status = processor.processMoneyMovement(transaction);
                transaction.setStatus(status);
                if (status == TransactionStatus.SETTLED) {
                    auditService.recordEvent(AuditAction.TRANSACTION_SETTLED, "TRANSACTION",
                            transactionId, "Approved by admin " + adminUserId, null, null, null);
                } else {
                    auditService.recordEvent(AuditAction.TRANSACTION_FAILED, "TRANSACTION",
                            transactionId, "Settlement failed after admin approval", null, null, null);
                }
            }
            case "REJECT" -> {
                transaction.setStatus(TransactionStatus.REJECTED);
                auditService.recordEvent(AuditAction.TRANSACTION_REJECTED, "TRANSACTION",
                        transactionId, "Rejected by admin " + adminUserId, null, null, null);
            }
            default -> throw new IllegalArgumentException("Decision must be APPROVE or REJECT");
        }

        return TransactionResponse.from(transactionRepository.save(transaction));
    }

    public PaginatedResponse<AuditEventResponse> getAuditEvents(int page, int size) {
        return auditService.getAuditEvents(page, size);
    }
}
