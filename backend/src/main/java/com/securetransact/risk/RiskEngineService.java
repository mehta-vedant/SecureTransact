package com.securetransact.risk;

import com.securetransact.model.Account;
import com.securetransact.model.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiskEngineService {

    private final StatisticalRiskScoringService statisticalScoringService;
    private final RiskDecisionEngine decisionEngine;
    private final BehavioralProfileService behavioralProfileService;

    public RiskEngineResult evaluateTransaction(Transaction transaction, Account sourceAccount) {
        RiskScoringResult scoringResult = statisticalScoringService.scoreTransaction(transaction, sourceAccount);

        var decision = decisionEngine.decide(scoringResult.getRiskLevel(), scoringResult.getTotalScore());
        scoringResult.setDecision(decision);

        behavioralProfileService.updateProfileAfterTransaction(sourceAccount, transaction.getAmount());

        log.info("Risk evaluation for txn {}: score={}, level={}, decision={}, factors={}",
                transaction.getId(), scoringResult.getTotalScore(), scoringResult.getRiskLevel(),
                decision, scoringResult.getFactors().size());

        return RiskEngineResult.builder()
                .scoringResult(scoringResult)
                .build();
    }
}
