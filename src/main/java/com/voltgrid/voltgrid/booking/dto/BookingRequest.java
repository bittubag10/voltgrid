package com.voltgrid.voltgrid.booking.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record BookingRequest(
        @NotNull Long vehicleId,
        @NotNull Long chargerId,
        @NotNull Instant startTime,
        @NotNull Instant endTime
) {
}