package com.voltgrid.voltgrid.trip.dto;

import com.voltgrid.voltgrid.enums.ConnectorType;

import java.math.BigDecimal;

public record TripStopDto(
        int stopNumber,
        Long stationId,
        String stationName,
        String address,
        String city,
        BigDecimal latitude,
        BigDecimal longitude,
        Long recommendedChargerId,
        String chargerName,
        ConnectorType connectorType,
        BigDecimal powerKw,
        double effectivePowerKw,
        double arrivalBatteryPercent,
        double targetBatteryPercent,
        double energyChargedKwh,
        double chargeDurationMinutes,
        BigDecimal estimatedCost
) {
}
