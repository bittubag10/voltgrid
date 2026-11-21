package com.voltgrid.voltgrid.dto;

import com.voltgrid.voltgrid.booking.dto.BookingResponse;
import com.voltgrid.voltgrid.station.dto.StationResponse;

import java.math.BigDecimal;
import java.util.List;

public record AdminOverviewResponse(
        long totalUsers,
        long totalDrivers,
        long totalOwners,
        long totalStations,
        long activeStations,
        long totalChargers,
        long availableChargers,
        long occupiedChargers,
        long totalBookings,
        long activeSessions,
        BigDecimal totalRevenue,
        BigDecimal totalOverstayPenalties,
        List<StationResponse> stations,
        List<BookingResponse> recentBookings
) {
}
