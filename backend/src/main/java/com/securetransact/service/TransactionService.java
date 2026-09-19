package com.securetransact.service;

import com.securetransact.audit.Auditable;
import com.securetransact.dto.TransactionRequest;
import com.securetransact.dto.TransactionResponse;
import com.securetransact.exception.ConflictException;
import com.securetransact.model.*;
import com.securetransact.repository.RiskEvaluationRepository;
import com.securetransact.repository.TransactionRepository;
import com.securetransact.risk.RiskDecisionEngine;
import com.securetransact.risk.RiskEngineResult;
import com.securetransact.risk.RiskEngineService;
import com.securetransact.risk.RiskScoringResult;
import com.securetransact.risk.BehavioralProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionValidator validator;
    private final TransactionProcessor processor;
    private final RiskEngineService riskEngineService;
    private final RiskCaseService riskCaseService;
    private final AuditService auditService;
    private final RiskEvaluationRepository riskEvaluationRepository;
    private final BehavioralProfileService behavioralProfileService;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Auditable(action = AuditAction.TRANSACTION_CREATED, resourceType = "TRANSACTION",
            description = "Transaction submitted for processing")
    public TransactionResponse submitTransaction(Long userId, TransactionRequest request) {
        if (request.getIdempotencyKey() != null) {
            Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existing.isPresent()) {
                return TransactionResponse.from(existing.get());
            }
        }

        Account fromAccount = null;
        Account toAccount = null;

        switch (request.getType()) {
            case DEPOSIT -> toAccount = validator.validateAndGetToAccount(request.getToAccountId());
            case WITHDRAWAL -> {
                fromAccount = validator.validateAndGetFromAccount(request.getFromAccountId(), userId);
                validator.validateSufficientBalance(fromAccount, request.getAmount());
            }
            case TRANSFER -> {
                fromAccount = validator.validateAndGetFromAccount(request.getFromAccountId(), userId);
                toAccount = validator.validateAndGetToAccount(request.getToAccountId());
                validator.validateDifferentAccounts(fromAccount, toAccount);
                validator.validateSufficientBalance(fromAccount, request.getAmount());
            }
        }

        Transaction transaction = Transaction.builder()
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .type(request.getType())
                .amount(request.getAmount())
                .status(TransactionStatus.CREATED)
                .idempotencyKey(request.getIdempotencyKey())
                .description(request.getDescription())
                .currency(normalizeCurrency(request.getCurrency()))
                .channel(request.getChannel() == null ? PaymentChannel.WEB : request.getChannel())
                .deviceId(request.getDeviceId())
                .payerCountry(normalizeCountry(request.getPayerCountry()))
                .beneficiaryCountry(normalizeCountry(request.getBeneficiaryCountry()))
                .crossBorder(isCrossBorder(request))
                .build();

        try {
            transaction = transactionRepository.save(transaction);
        } catch (DataIntegrityViolationException e) {
            Transaction existing = transactionRepository.findByIdempotencyKey(request.getIdempotencyKey())
                    .orElseThrow(() -> new ConflictException("Duplicate idempotency key"));
            return TransactionResponse.from(existing);
        }

        Account sourceAccount = (fromAccount != null) ? fromAccount : toAccount;
        RiskEngineResult engineResult = riskEngineService.evaluateTransaction(transaction, sourceAccount);

        RiskScoringResult scoringResult = engineResult.getScoringResult();
        transaction.setRiskScore(scoringResult.getTotalScore());

        RiskEvaluation evaluation = RiskEvaluation.builder()
                .transaction(transaction)
                .totalScore(scoringResult.getTotalScore())
                .riskLevel(scoringResult.getRiskLevel())
                .decision(scoringResult.getDecision())
                .modelVersion(scoringResult.getModelVersion())
                .reasons(formatReasons(engineResult))
                .mlProbability(scoringResult.getMlProbability())
                .build();
        riskEvaluationRepository.save(evaluation);

        switch (scoringResult.getDecision()) {
            case ALLOW -> {
                TransactionStatus settlementStatus = processor.processMoneyMovement(transaction);
                transaction.setStatus(settlementStatus);

                if (settlementStatus == TransactionStatus.SETTLED) {
                    behavioralProfileService.updateProfileAfterTransaction(sourceAccount, transaction.getAmount());
                    auditService.recordEvent(AuditAction.TRANSACTION_SETTLED, "TRANSACTION",
                            transaction.getId(), "Auto-settled after risk evaluation", null, null, null);
                } else {
                    auditService.recordEvent(AuditAction.TRANSACTION_FAILED, "TRANSACTION",
                            transaction.getId(), "Settlement failed", null, null, null);
                }
            }
            case HOLD_FOR_REVIEW -> {
                transaction.setStatus(TransactionStatus.HELD_FOR_REVIEW);
                riskCaseService.createRiskCase(transaction, evaluation);
                auditService.recordEvent(AuditAction.TRANSACTION_HELD, "TRANSACTION",
                        transaction.getId(), "Held for review - risk score: " + scoringResult.getTotalScore(),
                        null, null, null);
            }
            case BLOCK -> {
                transaction.setStatus(TransactionStatus.REJECTED);
                auditService.recordEvent(AuditAction.TRANSACTION_REJECTED, "TRANSACTION",
                        transaction.getId(), "Blocked by risk engine - score: " + scoringResult.getTotalScore(),
                        null, null, null);
            }
        }

        transaction = transactionRepository.save(transaction);
        log.info("Transaction {} completed: status={}, riskScore={}, decision={}",
                transaction.getId(), transaction.getStatus(), scoringResult.getTotalScore(),
                scoringResult.getDecision());

        return TransactionResponse.from(transaction);
    }

    public TransactionResponse getTransaction(Long transactionId, Long userId, boolean isAdmin) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new com.securetransact.exception.ResourceNotFoundException("Transaction not found"));

        boolean isOwner = (transaction.getFromAccount() != null
                        && transaction.getFromAccount().getUser().getId().equals(userId))
                || (transaction.getToAccount() != null
                        && transaction.getToAccount().getUser().getId().equals(userId));

        if (!isOwner && !isAdmin) {
            throw new com.securetransact.exception.ForbiddenException("You do not have access to this transaction");
        }

        return TransactionResponse.from(transaction);
    }

    public Page<TransactionResponse> getTransactionHistory(Long userId, Pageable pageable) {
        return transactionRepository.findByUserId(userId, pageable)
                .map(TransactionResponse::from);
    }

    @Transactional
    @Auditable(action = AuditAction.TRANSACTION_REVERSED, resourceType = "TRANSACTION",
            description = "Payment reversal requested")
    public TransactionResponse reverseTransaction(Long transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new com.securetransact.exception.ResourceNotFoundException("Transaction not found"));

        TransactionStatus reversalStatus = processor.reverseMoneyMovement(transaction);
        if (reversalStatus != TransactionStatus.REVERSED) {
            throw new ConflictException("Payment cannot be reversed in its current state");
        }
        transaction.setStatus(TransactionStatus.REVERSED);
        transactionRepository.save(transaction);
        auditService.recordEvent(AuditAction.TRANSACTION_REVERSED, "TRANSACTION", transaction.getId(),
                "Settled payment reversed through compensating ledger entries", null, null, null);
        return TransactionResponse.from(transaction);
    }

    private String formatReasons(RiskEngineResult result) {
        if (result.getScoringResult().getFactors() == null || result.getScoringResult().getFactors().isEmpty()) {
            return "";
        }
        return result.getScoringResult().getFactors().stream()
                .map(f -> f.getCode() + ": " + f.getMessage() + " (+" + f.getPoints() + ")")
                .reduce((a, b) -> a + "||" + b)
                .orElse("");
    }

    private boolean isCrossBorder(TransactionRequest request) {
        String payerCountry = normalizeCountry(request.getPayerCountry());
        String beneficiaryCountry = normalizeCountry(request.getBeneficiaryCountry());
        return payerCountry != null && beneficiaryCountry != null && !payerCountry.equals(beneficiaryCountry);
    }

    private String normalizeCountry(String country) {
        return country == null || country.isBlank() ? null : country.trim().toUpperCase();
    }

    private String normalizeCurrency(String currency) {
        return currency == null || currency.isBlank() ? "USD" : currency.trim().toUpperCase();
    }
}
