package com.securetransact.controller.v1;

import com.securetransact.dto.BlacklistEntryRequest;
import com.securetransact.dto.BlacklistEntryResponse;
import com.securetransact.model.BlacklistType;
import com.securetransact.service.BlacklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/blacklist")
@RequiredArgsConstructor
@Tag(name = "Admin Blacklist", description = "Manage blacklisted accounts, users, IPs, and emails")
public class BlacklistControllerV1 {

    private final BlacklistService blacklistService;

    @GetMapping
    @Operation(summary = "List blacklist entries", description = "Optional filters: type and active")
    public ResponseEntity<List<BlacklistEntryResponse>> list(
            @RequestParam(required = false) BlacklistType type,
            @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(blacklistService.list(type, active));
    }

    @PostMapping
    @Operation(summary = "Add a blacklist entry", description = "Reactivates an existing inactive entry for the same type/value")
    public ResponseEntity<BlacklistEntryResponse> add(@Valid @RequestBody BlacklistEntryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(blacklistService.add(request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate a blacklist entry", description = "Soft delete: sets active=false, keeps audit history")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        blacklistService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}