package com.securetransact.service;

import com.securetransact.dto.PaginatedResponse;
import com.securetransact.dto.RiskCaseDecisionRequest;
import com.securetransact.dto.RiskCaseResponse;
import com.securetransact.exception.ResourceNotFoundException;
import com.securetransact.model.*;
import com.securetransact.repository.RiskCaseRepository;
import com.securetransact.repository.TransactionRepository;
import com.securetransact.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiskCaseService {

    private final RiskCaseRepository riskCaseRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final TransactionProcessor processor;

    @Transactional
    public RiskCase createRiskCase(Transaction transaction, RiskEvaluation evaluation) {
        RiskCase riskCase = RiskCase.builder()
                .transaction(transaction)
                .riskEvaluation(evaluation)
                .status(CaseStatus.OPEN)
                .build();

        RiskCase saved = riskCaseRepository.save(riskCase);
        log.info("Created risk case {} for transaction {}", saved.getId(), transaction.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<RiskCaseResponse> getOpenCases(int page, int size) {
        Page<RiskCase> cases = riskCaseRepository.findByStatusIn(
                List.of(CaseStatus.OPEN, CaseStatus.IN_REVIEW, CaseStatus.ESCALATED),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return toPaginatedResponse(cases);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<RiskCaseResponse> getAllCases(int page, int size) {
        Page<RiskCase> cases = riskCaseRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return toPaginatedResponse(cases);
    }

    @Transactional(readOnly = true)
    public RiskCaseResponse getCaseById(Long caseId) {
        RiskCase riskCase = riskCaseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Risk case not found: " + caseId));
        return RiskCaseResponse.from(riskCase);
    }

    @Transactional
    public RiskCaseResponse assignCase(Long caseId, Long adminUserId) {
        RiskCase riskCase = riskCaseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Risk case not found: " + caseId));

        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found: " + adminUserId));

        riskCase.setAssignedTo(admin);
        riskCase.setStatus(CaseStatus.IN_REVIEW);
        RiskCase saved = riskCaseRepository.save(riskCase);

        log.info("Assigned risk case {} to admin {}", caseId, adminUserId);
        return RiskCaseResponse.from(saved);
    }

    @Transactional
    public RiskCaseResponse decideCase(Long caseId, RiskCaseDecisionRequest request, Long adminUserId) {
        RiskCase riskCase = riskCaseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Risk case not found: " + caseId));

        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found: " + adminUserId));

        riskCase.setAdminDecision(request.getDecision());
        riskCase.setReviewNotes(request.getReviewNotes());
        riskCase.setReviewedBy(admin);
        riskCase.setReviewedAt(LocalDateTime.now());

        Transaction transaction = riskCase.getTransaction();

        switch (request.getDecision()) {
            case ALLOW -> {
                riskCase.setStatus(CaseStatus.APPROVED);
                TransactionStatus settlementStatus = processor.processMoneyMovement(transaction);
                transaction.setStatus(settlementStatus);
            }
            case BLOCK -> {
                riskCase.setStatus(CaseStatus.REJECTED);
                transaction.setStatus(TransactionStatus.REJECTED);
            }
            case HOLD_FOR_REVIEW -> {
                riskCase.setStatus(CaseStatus.ESCALATED);
            }
        }

        transactionRepository.save(transaction);
        RiskCase saved = riskCaseRepository.save(riskCase);

        log.info("Risk case {} decided: {} by admin {}", caseId, request.getDecision(), adminUserId);
        return RiskCaseResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public long countOpenCases() {
        return riskCaseRepository.countByStatus(CaseStatus.OPEN)
                + riskCaseRepository.countByStatus(CaseStatus.IN_REVIEW);
    }

    private PaginatedResponse<RiskCaseResponse> toPaginatedResponse(Page<RiskCase> page) {
        List<RiskCaseResponse> content = page.getContent().stream()
                .map(RiskCaseResponse::from)
                .toList();
        return new PaginatedResponse<>(
                content, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(),
                page.isFirst(), page.isLast());
    }
}
