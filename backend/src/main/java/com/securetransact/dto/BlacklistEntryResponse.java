package com.securetransact.dto;

import com.securetransact.model.BlacklistType;
import com.securetransact.model.FraudBlacklist;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BlacklistEntryResponse {
    private Long id;
    private BlacklistType type;
    private String value;
    private String reason;
    private boolean active;
    private LocalDateTime createdAt;

    public static BlacklistEntryResponse from(FraudBlacklist entry) {
        BlacklistEntryResponse response = new BlacklistEntryResponse();
        response.setId(entry.getId());
        response.setType(entry.getType());
        response.setValue(entry.getValue());
        response.setReason(entry.getReason());
        response.setActive(entry.isActive());
        response.setCreatedAt(entry.getCreatedAt());
        return response;
    }
}