package com.voltgrid.voltgrid.charger.dto;

import com.voltgrid.voltgrid.charger.entity.ChargerStatus;
import jakarta.validation.constraints.NotNull;

public record ChargerStatusRequest(
        @NotNull ChargerStatus status
) {
}