package com.voltgrid.voltgrid.trip.dto;

import java.math.BigDecimal;
import java.util.List;

public record TripPlanResponse(
        Long vehicleId,
        String vehicleModel,
        double totalDistanceKm,
        double totalDriveMinutes,
        double totalChargeMinutes,
        double totalTripMinutes,
        BigDecimal totalCost,
        int totalStops,
        List<TripStopDto> stops,
        List<TripLegDto> legs,
        List<RouteCoordinate> routeCoordinates,
        String summary
) {
}
