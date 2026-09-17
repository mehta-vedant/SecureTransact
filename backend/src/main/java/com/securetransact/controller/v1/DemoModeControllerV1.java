package com.securetransact.controller.v1;

import com.securetransact.dto.TransactionResponse;
import com.securetransact.simulator.TransactionSimulator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/demo")
@RequiredArgsConstructor
@Tag(name = "Demo Mode", description = "Live fraud-detection demo scenario triggers (admin)")
public class DemoModeControllerV1 {

    private final TransactionSimulator simulator;

    @GetMapping("/health")
    @Operation(summary = "Check demo mode readiness")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "demoMode", simulator.isReady()
        ));
    }

    @PostMapping("/trigger/rapid-transfer")
    @Operation(summary = "Burst rapid transfers to the same destination")
    public ResponseEntity<List<TransactionResponse>> rapidTransfer() {
        return ResponseEntity.ok(simulator.triggerRapidTransferBurst());
    }

    @PostMapping("/trigger/high-velocity")
    @Operation(summary = "Burst many small transactions in a short window")
    public ResponseEntity<List<TransactionResponse>> highVelocity() {
        return ResponseEntity.ok(simulator.triggerHighVelocityBurst());
    }

    @PostMapping("/trigger/large-amount")
    @Operation(summary = "Submit a single large-amount transaction")
    public ResponseEntity<List<TransactionResponse>> largeAmount() {
        return ResponseEntity.ok(simulator.triggerLargeAmount());
    }

    @PostMapping("/trigger/new-account-large-txn")
    @Operation(summary = "Create a fresh account then submit large transactions from it")
    public ResponseEntity<List<TransactionResponse>> newAccountLargeTxn() {
        return ResponseEntity.ok(simulator.triggerNewAccountLargeTxn());
    }

    @PostMapping("/trigger/blacklist-transfer")
    @Operation(summary = "Submit a transaction to a blacklisted destination")
    public ResponseEntity<List<TransactionResponse>> blacklistTransfer() {
        return ResponseEntity.ok(simulator.triggerBlacklistTransfer());
    }
}