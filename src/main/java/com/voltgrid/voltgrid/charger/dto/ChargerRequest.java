package com.voltgrid.voltgrid.charger.dto;


import com.voltgrid.voltgrid.enums.ConnectorType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ChargerRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @DecimalMin("1.0") @DecimalMax("500.0") @Digits(integer = 3, fraction = 2) BigDecimal powerKw,
        @NotNull ConnectorType connectorType,
        @NotNull @DecimalMin("0.01") @DecimalMax("500.0") @Digits(integer = 3, fraction = 2) BigDecimal pricePerKwh
) {
}