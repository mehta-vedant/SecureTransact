package com.securetransact.dto;

import com.securetransact.model.TransactionType;
import com.securetransact.model.PaymentChannel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransactionRequest {

    @NotNull(message = "Transaction type is required")
    private TransactionType type;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @DecimalMax(value = "99999999.99", message = "Amount exceeds maximum allowed")
    private BigDecimal amount;

    private Long fromAccountId;

    private Long toAccountId;

    @Size(max = 200, message = "Description must be at most 200 characters")
    private String description;

    @NotBlank(message = "Idempotency key is required")
    @Size(max = 64, message = "Idempotency key must be at most 64 characters")
    private String idempotencyKey;

    private Boolean crossBorder;

    @Size(min = 3, max = 3, message = "Currency must be a 3-letter code")
    private String currency;

    private PaymentChannel channel;

    @Size(max = 255, message = "Device ID must be at most 255 characters")
    private String deviceId;

    @Size(min = 2, max = 2, message = "Payer country must be a 2-letter code")
    private String payerCountry;

    @Size(min = 2, max = 2, message = "Beneficiary country must be a 2-letter code")
    private String beneficiaryCountry;

    @AssertTrue(message = "Transaction accounts do not match the selected transaction type")
    public boolean isAccountShapeValid() {
        if (type == null) return true;
        return switch (type) {
            case DEPOSIT -> toAccountId != null && fromAccountId == null;
            case WITHDRAWAL -> fromAccountId != null && toAccountId == null;
            case TRANSFER -> fromAccountId != null && toAccountId != null;
        };
    }
}
