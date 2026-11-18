package com.voltgrid.voltgrid.station.dto;

import com.voltgrid.voltgrid.station.entity.StationStatus;
import jakarta.validation.constraints.NotNull;

public record StationStatusRequest(
        @NotNull StationStatus status
) {
}