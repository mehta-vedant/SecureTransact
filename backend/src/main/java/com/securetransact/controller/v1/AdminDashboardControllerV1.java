package com.securetransact.controller.v1;

import com.securetransact.dto.DashboardMetricsResponse;
import com.securetransact.dto.PaginatedResponse;
import com.securetransact.dto.AccountResponse;
import com.securetransact.dto.AuditEventResponse;
import com.securetransact.service.AdminService;
import com.securetransact.service.AccountService;
import com.securetransact.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin Dashboard", description = "Admin dashboard and operations")
public class AdminDashboardControllerV1 {

    private final AdminService adminService;
    private final AccountService accountService;

    @GetMapping("/dashboard")
    @Operation(summary = "Get admin dashboard metrics")
    public ResponseEntity<DashboardMetricsResponse> getDashboard() {
        return ResponseEntity.ok(adminService.getDashboardMetrics());
    }

    @GetMapping("/accounts")
    @Operation(summary = "List all accounts")
    public ResponseEntity<PaginatedResponse<AccountResponse>> listAccounts(
            @PageableDefault(size = 20) Pageable pageable) {
        Page<AccountResponse> accounts = adminService.getAllAccounts(pageable);
        return ResponseEntity.ok(new PaginatedResponse<>(
                accounts.getContent(),
                pageable.getPageNumber(), pageable.getPageSize(),
                accounts.getTotalElements(), accounts.getTotalPages(),
                accounts.isFirst(), accounts.isLast()));
    }

    @GetMapping("/audit-events")
    @Operation(summary = "List audit events")
    public ResponseEntity<PaginatedResponse<AuditEventResponse>> listAuditEvents(
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(adminService.getAuditEvents(pageable.getPageNumber(), pageable.getPageSize()));
    }

    @PostMapping("/accounts/{accountId}/freeze")
    @Operation(summary = "Freeze an account from the fraud operations workspace")
    public ResponseEntity<AccountResponse> freezeAccount(@PathVariable Long accountId,
            @RequestBody(required = false) java.util.Map<String, String> request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal CustomUserDetails admin) {
        return ResponseEntity.ok(accountService.freezeAccount(accountId, admin.getId(), request == null ? null : request.get("reason")));
    }

    @PostMapping("/accounts/{accountId}/unfreeze")
    @Operation(summary = "Unfreeze an account after fraud review")
    public ResponseEntity<AccountResponse> unfreezeAccount(@PathVariable Long accountId,
            @RequestBody(required = false) java.util.Map<String, String> request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal CustomUserDetails admin) {
        return ResponseEntity.ok(accountService.unfreezeAccount(accountId, admin.getId(), request == null ? null : request.get("reason")));
    }
}
