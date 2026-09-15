package com.securetransact.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "fraud_blacklist")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class FraudBlacklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BlacklistType type;

    @Column(name = "blacklist_value", nullable = false)
    private String value;

    private String reason;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (active) active = true;
    }
}
