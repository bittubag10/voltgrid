package com.voltgrid.voltgrid.station.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record StationRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 300) String address,
        @NotBlank @Size(max = 100) String city,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") @Digits(integer = 2, fraction = 6) BigDecimal latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") @Digits(integer = 3, fraction = 6) BigDecimal longitude
) {
}