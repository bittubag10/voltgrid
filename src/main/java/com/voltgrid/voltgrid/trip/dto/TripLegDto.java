package com.voltgrid.voltgrid.trip.dto;

public record TripLegDto(
        int legNumber,
        String fromName,
        String toName,
        double distanceKm,
        double driveMinutes,
        double startBatteryPercent,
        double endBatteryPercent
) {
}
