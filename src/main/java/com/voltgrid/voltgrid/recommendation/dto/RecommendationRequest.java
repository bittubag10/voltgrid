package com.voltgrid.voltgrid.recommendation.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record RecommendationRequest(
        @NotNull Long vehicleId,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal lat,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal lng,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") @Digits(integer = 3, fraction = 2) BigDecimal batteryPercent,
        @NotNull Boolean acOn,
        @Min(1) @Max(130) Integer speedKmph,
        @DecimalMin("1.0") @DecimalMax("100.0") @Digits(integer = 3, fraction = 2) BigDecimal targetPercent,
        @Min(1) @Max(10) Integer topK
) {
}