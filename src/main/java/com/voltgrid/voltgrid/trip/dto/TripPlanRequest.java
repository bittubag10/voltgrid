package com.voltgrid.voltgrid.trip.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TripPlanRequest(
        @NotNull(message = "vehicleId is required")
        Long vehicleId,

        @NotNull(message = "startLat is required")
        @DecimalMin(value = "-90.0", message = "startLat must be >= -90")
        @DecimalMax(value = "90.0", message = "startLat must be <= 90")
        BigDecimal startLat,

        @NotNull(message = "startLng is required")
        @DecimalMin(value = "-180.0", message = "startLng must be >= -180")
        @DecimalMax(value = "180.0", message = "startLng must be <= 180")
        BigDecimal startLng,

        @NotNull(message = "destLat is required")
        @DecimalMin(value = "-90.0", message = "destLat must be >= -90")
        @DecimalMax(value = "90.0", message = "destLat must be <= 90")
        BigDecimal destLat,

        @NotNull(message = "destLng is required")
        @DecimalMin(value = "-180.0", message = "destLng must be >= -180")
        @DecimalMax(value = "180.0", message = "destLng must be <= 180")
        BigDecimal destLng,

        @NotNull(message = "currentBatteryPercent is required")
        @DecimalMin(value = "0.0", message = "currentBatteryPercent must be >= 0")
        @DecimalMax(value = "100.0", message = "currentBatteryPercent must be <= 100")
        BigDecimal currentBatteryPercent,

        Boolean acOn,
        Double speedKmh,
        String terrain
) {
}
