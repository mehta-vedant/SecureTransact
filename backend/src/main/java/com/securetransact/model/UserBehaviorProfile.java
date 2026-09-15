package com.securetransact.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_behavior_profiles")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UserBehaviorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal avgTransactionAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal stddevTransactionAmount;

    @Column(nullable = false)
    private int totalTransactionCount;

    @Column(name = "transactions_last_24h", nullable = false)
    private int transactionsLast24h;

    @Column(nullable = false)
    private int transactionsLastHour;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal typicalStartHour;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal typicalEndHour;

    @Column(nullable = false)
    private boolean hasBaseline;

    @Column(nullable = false)
    private LocalDateTime lastUpdated;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        lastUpdated = now;
        if (avgTransactionAmount == null) avgTransactionAmount = BigDecimal.ZERO;
        if (stddevTransactionAmount == null) stddevTransactionAmount = BigDecimal.ZERO;
        if (typicalStartHour == null) typicalStartHour = new BigDecimal("8");
        if (typicalEndHour == null) typicalEndHour = new BigDecimal("22");
    }
}
