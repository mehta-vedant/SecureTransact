package com.securetransact.dto;

import com.securetransact.model.Transaction;
import com.securetransact.model.TransactionStatus;
import com.securetransact.model.TransactionType;
import com.securetransact.risk.RiskDecisionEngine;
import com.securetransact.risk.StatisticalRiskScoringService;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransactionResponse {
    private Long id;
    private TransactionType type;
    private BigDecimal amount;
    private TransactionStatus status;
    private Integer riskScore;
    private String riskLevel;
    private String riskDecision;
    private String description;
    private Long fromAccountId;
    private Long toAccountId;
    private String fromAccountNumber;
    private String toAccountNumber;
    private String idempotencyKey;
    private boolean crossBorder;
    private LocalDateTime createdAt;

    public static TransactionResponse from(Transaction txn) {
        TransactionResponse response = new TransactionResponse();
        response.setId(txn.getId());
        response.setType(txn.getType());
        response.setAmount(txn.getAmount());
        response.setStatus(txn.getStatus());
        response.setRiskScore(txn.getRiskScore());
        if (txn.getRiskScore() != null) {
            response.setRiskLevel(StatisticalRiskScoringService.determineRiskLevel(txn.getRiskScore()).name());
            response.setRiskDecision(RiskDecisionEngine.fromScore(txn.getRiskScore()).name());
        }
        response.setDescription(txn.getDescription());
        response.setIdempotencyKey(txn.getIdempotencyKey());
        response.setCrossBorder(txn.isCrossBorder());
        response.setCreatedAt(txn.getCreatedAt());

        if (txn.getFromAccount() != null) {
            response.setFromAccountId(txn.getFromAccount().getId());
            response.setFromAccountNumber(txn.getFromAccount().getAccountNumber());
        }
        if (txn.getToAccount() != null) {
            response.setToAccountId(txn.getToAccount().getId());
            response.setToAccountNumber(txn.getToAccount().getAccountNumber());
        }

        return response;
    }
}
