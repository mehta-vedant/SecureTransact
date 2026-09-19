package com.securetransact.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
public class DashboardMetricsResponse {
    private long totalTransactionsToday;
    private BigDecimal totalVolumeToday;
    private long flaggedTransactionsToday;
    private long activeAccounts;
    private long completedTransactionsToday;
    private long failedTransactionsToday;
    private long openRiskCases;
    private long criticalRiskCases;
    private long oldestOpenCaseMinutes;
}
