package com.securetransact.controller.v1;

import com.securetransact.dto.TransactionRequest;
import com.securetransact.dto.TransactionResponse;
import com.securetransact.security.CustomUserDetails;
import com.securetransact.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Transaction submission and history")
public class TransactionControllerV1 {

    private final TransactionService transactionService;

    @PostMapping
    @Operation(summary = "Submit a transaction for processing")
    public ResponseEntity<TransactionResponse> submitTransaction(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.submitTransaction(user.getId(), request));
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Get transaction details")
    public ResponseEntity<TransactionResponse> getTransaction(
            @PathVariable Long transactionId,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(transactionService.getTransaction(transactionId, user.getId(), user.isAdmin()));
    }

    @GetMapping
    @Operation(summary = "List transactions for current user")
    public ResponseEntity<Page<TransactionResponse>> listTransactions(
            @AuthenticationPrincipal CustomUserDetails user,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(transactionService.getTransactionHistory(user.getId(), pageable));
    }

    @PostMapping("/{transactionId}/reverse")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reverse a settled payment with compensating ledger entries")
    public ResponseEntity<TransactionResponse> reverseTransaction(@PathVariable Long transactionId) {
        return ResponseEntity.ok(transactionService.reverseTransaction(transactionId));
    }
}
