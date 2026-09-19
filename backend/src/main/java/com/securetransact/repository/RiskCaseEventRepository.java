package com.securetransact.repository;

import com.securetransact.model.RiskCaseEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RiskCaseEventRepository extends JpaRepository<RiskCaseEvent, Long> {
    List<RiskCaseEvent> findByRiskCaseIdOrderByCreatedAtAsc(Long riskCaseId);
}
