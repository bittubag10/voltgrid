package com.voltgrid.voltgrid.station.dto;

import java.math.BigDecimal;

public record HourlyActivityDto(
        int hour,
        String hourLabel,
        long sessionCount,
        BigDecimal revenue
) {
}
