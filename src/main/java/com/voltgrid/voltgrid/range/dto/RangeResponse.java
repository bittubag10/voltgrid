package com.voltgrid.voltgrid.range.dto;

public record RangeResponse(
        Long vehicleId,
        String vehicleModel,
        double batteryPercent,
        boolean acOn,
        int speedKmph,
        double reservePercent,
        double usableEnergyKwh,
        double effectiveConsumptionKwhPerKm,
        double formulaRangeKm,
        Double aiFactor,
        double estimatedRangeKm,
        String method
) {
}