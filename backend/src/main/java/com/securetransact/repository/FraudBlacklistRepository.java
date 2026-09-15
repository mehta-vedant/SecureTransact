package com.securetransact.repository;

import com.securetransact.model.BlacklistType;
import com.securetransact.model.FraudBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FraudBlacklistRepository extends JpaRepository<FraudBlacklist, Long> {

    Optional<FraudBlacklist> findByTypeAndValueAndActiveTrue(BlacklistType type, String value);

    Optional<FraudBlacklist> findByTypeAndValue(BlacklistType type, String value);

    List<FraudBlacklist> findAllByTypeAndActive(BlacklistType type, boolean active);

    List<FraudBlacklist> findAllByType(BlacklistType type);

    List<FraudBlacklist> findAllByActive(boolean active);

    boolean existsByTypeAndValueAndActiveTrue(BlacklistType type, String value);
}
