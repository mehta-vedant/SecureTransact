package com.securetransact.service;

import com.securetransact.dto.BlacklistEntryRequest;
import com.securetransact.dto.BlacklistEntryResponse;
import com.securetransact.exception.ResourceNotFoundException;
import com.securetransact.model.AuditAction;
import com.securetransact.model.BlacklistType;
import com.securetransact.model.FraudBlacklist;
import com.securetransact.repository.FraudBlacklistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BlacklistService {

    private final FraudBlacklistRepository blacklistRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<BlacklistEntryResponse> list(BlacklistType type, Boolean active) {
        List<FraudBlacklist> entries;
        if (type != null && active != null) {
            entries = blacklistRepository.findAllByTypeAndActive(type, active);
        } else if (type != null) {
            entries = blacklistRepository.findAllByType(type);
        } else if (active != null) {
            entries = blacklistRepository.findAllByActive(active);
        } else {
            entries = blacklistRepository.findAll();
        }
        return entries.stream().map(BlacklistEntryResponse::from).toList();
    }

    @Transactional
    public BlacklistEntryResponse add(BlacklistEntryRequest request) {
        FraudBlacklist entry = blacklistRepository
                .findByTypeAndValue(request.getType(), request.getValue())
                .filter(existing -> !existing.isActive())
                .map(existing -> {
                    existing.setActive(true);
                    existing.setReason(request.getReason() != null ? request.getReason() : existing.getReason());
                    return blacklistRepository.save(existing);
                })
                .orElseGet(() -> blacklistRepository.findByTypeAndValue(
                        request.getType(), request.getValue()).orElseGet(() -> blacklistRepository.save(
                        FraudBlacklist.builder()
                                .type(request.getType())
                                .value(request.getValue())
                                .reason(request.getReason())
                                .active(true)
                                .build())));

        auditService.recordEvent(AuditAction.BLACKLIST_UPDATED, "FRAUD_BLACKLIST",
                entry.getId(), "Added blacklist entry " + request.getType() + "=" + request.getValue(),
                null, null, null);
        return BlacklistEntryResponse.from(entry);
    }

    @Transactional
    public void deactivate(Long id) {
        FraudBlacklist entry = blacklistRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Blacklist entry not found"));
        entry.setActive(false);
        blacklistRepository.save(entry);
        auditService.recordEvent(AuditAction.BLACKLIST_UPDATED, "FRAUD_BLACKLIST",
                id, "Deactivated blacklist entry " + entry.getType() + "=" + entry.getValue(),
                null, null, null);
    }
}