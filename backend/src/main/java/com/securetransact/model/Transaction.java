package com.securetransact.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_account_id")
    private Account fromAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_account_id")
    private Account toAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    private Integer riskScore;

    @Column(unique = true)
    private String idempotencyKey;

    private String description;

    @Column(name = "payment_reference", nullable = false, unique = true, updatable = false)
    private String paymentReference;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentRail rail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentChannel channel;

    @Column(name = "device_id", length = 255)
    private String deviceId;

    @Column(name = "payer_country", length = 2)
    private String payerCountry;

    @Column(name = "beneficiary_country", length = 2)
    private String beneficiaryCountry;

    @Column(name = "is_cross_border", nullable = false)
    private boolean crossBorder;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = TransactionStatus.CREATED;
        if (paymentReference == null) paymentReference = "PAY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        if (currency == null) currency = "USD";
        if (rail == null) rail = PaymentRail.ACCOUNT_TRANSFER;
        if (channel == null) channel = PaymentChannel.WEB;
    }
}
