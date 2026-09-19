package com.securetransact.controller.v1;

import com.securetransact.dto.*;
import com.securetransact.security.CustomUserDetails;
import com.securetransact.service.AuditService;
import com.securetransact.service.RiskCaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/risk-cases")
@RequiredArgsConstructor
@Tag(name = "Admin Risk Cases", description = "Admin risk case management")
public class AdminRiskCaseControllerV1 {

    private final RiskCaseService riskCaseService;

    @GetMapping
    @Operation(summary = "List open risk cases")
    public ResponseEntity<PaginatedResponse<RiskCaseResponse>> listOpenCases(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(riskCaseService.getOpenCases(pageable.getPageNumber(), pageable.getPageSize()));
    }

    @GetMapping("/{caseId}")
    @Operation(summary = "Get risk case details")
    public ResponseEntity<RiskCaseResponse> getCase(@PathVariable Long caseId) {
        return ResponseEntity.ok(riskCaseService.getCaseById(caseId));
    }

    @PatchMapping("/{caseId}/assign")
    @Operation(summary = "Assign a risk case to yourself")
    public ResponseEntity<RiskCaseResponse> assignCase(
            @PathVariable Long caseId,
            @AuthenticationPrincipal CustomUserDetails admin) {
        return ResponseEntity.ok(riskCaseService.assignCase(caseId, admin.getId()));
    }

    @PatchMapping("/{caseId}/decision")
    @Operation(summary = "Make a decision on a risk case")
    public ResponseEntity<RiskCaseResponse> decideCase(
            @PathVariable Long caseId,
            @Valid @RequestBody RiskCaseDecisionRequest request,
            @AuthenticationPrincipal CustomUserDetails admin) {
        return ResponseEntity.ok(riskCaseService.decideCase(caseId, request, admin.getId()));
    }

    @PostMapping("/{caseId}/notes")
    @Operation(summary = "Add an immutable timeline note to a risk case")
    public ResponseEntity<RiskCaseResponse> addNote(
            @PathVariable Long caseId,
            @RequestBody java.util.Map<String, String> request,
            @AuthenticationPrincipal CustomUserDetails admin) {
        return ResponseEntity.ok(riskCaseService.addNote(caseId, request.get("note"), admin.getId()));
    }
}
