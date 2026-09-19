package com.securetransact.dto;

import com.securetransact.model.CaseStatus;
import com.securetransact.model.RiskCase;
import com.securetransact.model.RiskDecision;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class RiskCaseResponse {
    private Long id;
    private Long transactionId;
    private BigDecimal transactionAmount;
    private Long fromAccountId;
    private Long toAccountId;
    private Long riskEvaluationId;
    private int riskScore;
    private String riskLevel;
    private String riskReasons;
    private String modelVersion;
    private BigDecimal anomalySignal;
    private CaseStatus status;
    private String priority;
    private List<RiskCaseEventResponse> timeline;
    private Long assignedToId;
    private String assignedToName;
    private String reviewNotes;
    private RiskDecision adminDecision;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
    private Long reviewedById;
    private String reviewedByName;

    public static RiskCaseResponse from(RiskCase rc) {
        RiskCaseResponse response = new RiskCaseResponse();
        response.setId(rc.getId());
        response.setStatus(rc.getStatus());
        response.setPriority(rc.getPriority() == null ? "MEDIUM" : rc.getPriority().name());
        response.setReviewNotes(rc.getReviewNotes());
        response.setAdminDecision(rc.getAdminDecision());
        response.setCreatedAt(rc.getCreatedAt());
        response.setReviewedAt(rc.getReviewedAt());

        if (rc.getTransaction() != null) {
            response.setTransactionId(rc.getTransaction().getId());
            response.setTransactionAmount(rc.getTransaction().getAmount());
            if (rc.getTransaction().getFromAccount() != null) response.setFromAccountId(rc.getTransaction().getFromAccount().getId());
            if (rc.getTransaction().getToAccount() != null) response.setToAccountId(rc.getTransaction().getToAccount().getId());
        }
        if (rc.getRiskEvaluation() != null) {
            response.setRiskEvaluationId(rc.getRiskEvaluation().getId());
            response.setRiskScore(rc.getRiskEvaluation().getTotalScore());
            response.setRiskLevel(rc.getRiskEvaluation().getRiskLevel().name());
            response.setRiskReasons(rc.getRiskEvaluation().getReasons());
            response.setModelVersion(rc.getRiskEvaluation().getModelVersion());
            response.setAnomalySignal(rc.getRiskEvaluation().getMlProbability());
        }
        if (rc.getAssignedTo() != null) {
            response.setAssignedToId(rc.getAssignedTo().getId());
            response.setAssignedToName(rc.getAssignedTo().getFirstName() + " " + rc.getAssignedTo().getLastName());
        }
        if (rc.getReviewedBy() != null) {
            response.setReviewedById(rc.getReviewedBy().getId());
            response.setReviewedByName(rc.getReviewedBy().getFirstName() + " " + rc.getReviewedBy().getLastName());
        }

        return response;
    }
}
