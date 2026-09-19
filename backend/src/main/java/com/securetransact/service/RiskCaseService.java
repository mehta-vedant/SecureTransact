package com.securetransact.service;

import com.securetransact.dto.PaginatedResponse;
import com.securetransact.dto.RiskCaseDecisionRequest;
import com.securetransact.dto.RiskCaseEventResponse;
import com.securetransact.dto.RiskCaseResponse;
import com.securetransact.exception.ResourceNotFoundException;
import com.securetransact.model.*;
import com.securetransact.repository.RiskCaseRepository;
import com.securetransact.repository.RiskCaseEventRepository;
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
    private final RiskCaseEventRepository riskCaseEventRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final TransactionProcessor processor;

    @Transactional
    public RiskCase createRiskCase(Transaction transaction, RiskEvaluation evaluation) {
        RiskCase riskCase = RiskCase.builder()
                .transaction(transaction)
                .riskEvaluation(evaluation)
                .status(CaseStatus.OPEN)
                .priority(priorityFor(evaluation.getTotalScore()))
                .build();

        RiskCase saved = riskCaseRepository.save(riskCase);
        recordEvent(saved, null, CaseEventType.CREATED, "Case created from automated risk hold");
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
        return toResponse(riskCase);
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
        recordEvent(saved, admin, CaseEventType.ASSIGNED, "Assigned to " + admin.getFirstName() + " " + admin.getLastName());

        log.info("Assigned risk case {} to admin {}", caseId, adminUserId);
        return toResponse(saved);
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
                recordEvent(riskCase, admin, CaseEventType.ESCALATED, "Escalated for further review");
            }
        }

        transactionRepository.save(transaction);
        RiskCase saved = riskCaseRepository.save(riskCase);
        recordEvent(saved, admin, CaseEventType.DECISION_RECORDED,
                "Decision: " + request.getDecision() + (request.getReviewNotes() == null ? "" : " — " + request.getReviewNotes()));

        log.info("Risk case {} decided: {} by admin {}", caseId, request.getDecision(), adminUserId);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public long countOpenCases() {
        return riskCaseRepository.countByStatus(CaseStatus.OPEN)
                + riskCaseRepository.countByStatus(CaseStatus.IN_REVIEW);
    }

    private PaginatedResponse<RiskCaseResponse> toPaginatedResponse(Page<RiskCase> page) {
        List<RiskCaseResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .toList();
        return new PaginatedResponse<>(
                content, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(),
                page.isFirst(), page.isLast());
    }

    @Transactional
    public RiskCaseResponse addNote(Long caseId, String note, Long adminUserId) {
        if (note == null || note.isBlank()) throw new IllegalArgumentException("A case note is required");
        RiskCase riskCase = riskCaseRepository.findById(caseId)
                .orElseThrow(() -> new ResourceNotFoundException("Risk case not found: " + caseId));
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin user not found: " + adminUserId));
        recordEvent(riskCase, admin, CaseEventType.NOTE_ADDED, note.trim());
        return toResponse(riskCase);
    }

    private RiskCaseResponse toResponse(RiskCase riskCase) {
        RiskCaseResponse response = RiskCaseResponse.from(riskCase);
        response.setTimeline(riskCaseEventRepository.findByRiskCaseIdOrderByCreatedAtAsc(riskCase.getId()).stream()
                .map(RiskCaseEventResponse::from).toList());
        return response;
    }

    private void recordEvent(RiskCase riskCase, User actor, CaseEventType type, String message) {
        riskCaseEventRepository.save(RiskCaseEvent.builder().riskCase(riskCase).actor(actor).type(type).message(message).build());
    }

    private CasePriority priorityFor(int score) {
        if (score >= 76) return CasePriority.CRITICAL;
        if (score >= 60) return CasePriority.HIGH;
        if (score >= 40) return CasePriority.MEDIUM;
        return CasePriority.LOW;
    }
}
