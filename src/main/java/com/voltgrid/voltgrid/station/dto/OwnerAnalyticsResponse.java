package com.voltgrid.voltgrid.station.dto;

import java.util.List;

public record OwnerAnalyticsResponse(
        OwnerOverallMetrics overall,
        List<StationAnalyticsDto> stations
) {
}
