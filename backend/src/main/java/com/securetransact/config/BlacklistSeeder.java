package com.securetransact.config;

import com.securetransact.model.BlacklistType;
import com.securetransact.model.FraudBlacklist;
import com.securetransact.repository.FraudBlacklistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BlacklistSeeder implements CommandLineRunner {

    private final FraudBlacklistRepository blacklistRepository;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Override
    public void run(String... args) {
        if (adminEmail == null || adminEmail.isBlank()) {
            return;
        }
        if (blacklistRepository.count() > 0) {
            return;
        }

        blacklistRepository.saveAll(List.of(
                FraudBlacklist.builder()
                        .type(BlacklistType.ACCOUNT_NUMBER)
                        .value("ST0000000000")
                        .reason("Demo: blocked account for interviews")
                        .active(true)
                        .build(),
                FraudBlacklist.builder()
                        .type(BlacklistType.USER_ID)
                        .value("0")
                        .reason("Demo: blocked user id")
                        .active(true)
                        .build(),
                FraudBlacklist.builder()
                        .type(BlacklistType.EMAIL)
                        .value("blocked@example.com")
                        .reason("Demo: denied email")
                        .active(true)
                        .build(),
                FraudBlacklist.builder()
                        .type(BlacklistType.IP_ADDRESS)
                        .value("203.0.113.1")
                        .reason("Demo: flagged IP (TEST-NET)")
                        .active(true)
                        .build()
        ));

        log.info("Seeded 4 demo blacklist entries");
    }
}