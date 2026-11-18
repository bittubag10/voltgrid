package com.voltgrid.voltgrid.vehicle.dto;


import com.voltgrid.voltgrid.enums.ConnectorType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record VehicleRequest(
        @NotBlank @Size(max = 100) String model,
        @NotNull @DecimalMin("1.0") @DecimalMax("300.0") BigDecimal usableBatteryKwh,
        @NotNull @DecimalMin("0.05") @DecimalMax("1.0") BigDecimal baseConsumptionKwhPerKm,
        @NotNull @DecimalMin("1.0") @DecimalMax("500.0") BigDecimal maxChargeKw,
        @NotNull ConnectorType connectorType
) {
}