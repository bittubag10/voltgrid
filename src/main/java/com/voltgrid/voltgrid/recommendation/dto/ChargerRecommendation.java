package com.voltgrid.voltgrid.recommendation.dto;

import com.voltgrid.voltgrid.enums.ConnectorType;

import java.math.BigDecimal;

public record ChargerRecommendation(
        int rank,
        Long chargerId,
        String chargerName,
        ConnectorType connectorType,
        BigDecimal powerKw,
        double effectivePowerKw,
        BigDecimal pricePerKwh,
        Long stationId,
        String stationName,
        String address,
        String city,
        BigDecimal latitude,
        BigDecimal longitude,
        double roadDistanceKm,
        double driveMinutes,
        double waitMinutes,
        double chargeMinutes,
        double totalMinutes,
        double energyToChargeKwh,
        BigDecimal estimatedCost
) {
}