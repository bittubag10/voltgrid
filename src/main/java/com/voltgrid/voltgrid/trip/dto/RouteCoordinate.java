package com.voltgrid.voltgrid.trip.dto;

import java.math.BigDecimal;

public record RouteCoordinate(
        BigDecimal lat,
        BigDecimal lng,
        String name,
        String type
) {
}
