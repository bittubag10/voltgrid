package com.voltgrid.voltgrid.station.dto;

import java.math.BigDecimal;

public record NearbyStationResponse(
        Long id,
        String name,
        String address,
        String city,
        BigDecimal latitude,
        BigDecimal longitude,
        double distanceKm,
        int matchingChargers,
        BigDecimal maxPowerKw
) {
}