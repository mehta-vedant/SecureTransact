package com.securetransact.dto;

import com.securetransact.model.BlacklistType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BlacklistEntryRequest {

    @NotNull(message = "type is required")
    private BlacklistType type;

    @NotBlank(message = "value is required")
    private String value;

    private String reason;
}