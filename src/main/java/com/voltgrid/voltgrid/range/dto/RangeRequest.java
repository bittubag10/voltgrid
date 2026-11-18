package com.voltgrid.voltgrid.range.dto;

import com.voltgrid.voltgrid.enums.Terrain;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record RangeRequest(
        @NotNull Long vehicleId,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") @Digits(integer = 3, fraction = 2) BigDecimal batteryPercent,
        @NotNull Boolean acOn,
        @Min(1) @Max(130) Integer speedKmph,
        @Min(-40) @Max(60) Integer temperatureCelsius,
        Terrain terrain
) {
}