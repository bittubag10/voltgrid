package com.voltgrid.voltgrid.station.dto;

import java.math.BigDecimal;
import java.util.List;

public record OwnerOverallMetrics(
        int totalStations,
        int totalChargers,
        long totalCompletedSessions,
        long totalActiveSessions,
        BigDecimal totalEnergyRevenue,
        BigDecimal totalPenaltyRevenue,
        BigDecimal totalRevenue,
        double averageChargerUtilizationPercent,
        Integer busiestHour,
        String busiestHourLabel,
        List<HourlyActivityDto> hourlyActivity
) {
}
