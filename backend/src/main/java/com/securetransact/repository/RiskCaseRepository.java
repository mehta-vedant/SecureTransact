package com.securetransact.repository;

import com.securetransact.model.CaseStatus;
import com.securetransact.model.CasePriority;
import com.securetransact.model.RiskCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface RiskCaseRepository extends JpaRepository<RiskCase, Long> {

    Optional<RiskCase> findByTransactionId(Long transactionId);

    Page<RiskCase> findByStatus(CaseStatus status, Pageable pageable);

    Page<RiskCase> findByAssignedToId(Long userId, Pageable pageable);

    @Query("SELECT rc FROM RiskCase rc WHERE rc.status IN :statuses ORDER BY rc.createdAt DESC")
    Page<RiskCase> findByStatusIn(@Param("statuses") List<CaseStatus> statuses, Pageable pageable);

    long countByStatus(CaseStatus status);

    long countByPriorityAndStatusIn(CasePriority priority, List<CaseStatus> statuses);

    Optional<RiskCase> findFirstByStatusInOrderByCreatedAtAsc(List<CaseStatus> statuses);

    @Query("SELECT COUNT(rc) FROM RiskCase rc WHERE rc.createdAt > :since")
    long countCreatedSince(@Param("since") java.time.LocalDateTime since);
}
