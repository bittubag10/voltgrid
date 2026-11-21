package com.voltgrid.voltgrid.ai.dto;

import com.voltgrid.voltgrid.trip.dto.TripPlanResponse;

import java.math.BigDecimal;

public record AiTripParseResponse(
        String prompt,
        String intent, // "HIGHWAY_TRIP" or "LOCAL_CHARGER_SEARCH"
        String originCity,
        BigDecimal startLat,
        BigDecimal startLng,
        String destinationCity,
        BigDecimal destLat,
        BigDecimal destLng,
        BigDecimal currentBatteryPercent,
        BigDecimal targetBatteryPercent,
        boolean acOn,
        int speedKmh,
        String terrain,
        String summary,
        boolean aiParsed,
        TripPlanResponse plannedTrip
) {
}
