package com.voltgrid.voltgrid.station.dto;

import java.math.BigDecimal;
import java.util.List;

public record StationAnalyticsDto(
        Long stationId,
        String stationName,
        String address,
        String city,
        int totalChargers,
        long completedSessions,
        long activeSessions,
        BigDecimal energyRevenue,
        BigDecimal penaltyRevenue,
        BigDecimal totalRevenue,
        double utilizationPercent,
        Integer busiestHour,
        String busiestHourLabel,
        List<HourlyActivityDto> hourlyActivity
) {
}
