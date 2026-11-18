package com.voltgrid.voltgrid.station.dto;

import com.voltgrid.voltgrid.station.entity.Station;
import com.voltgrid.voltgrid.station.entity.StationStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record StationResponse(
        Long id,
        String name,
        String address,
        String city,
        BigDecimal latitude,
        BigDecimal longitude,
        StationStatus status,
        Instant createdAt
) {
    public static StationResponse from(Station s) {
        return new StationResponse(
                s.getId(),
                s.getName(),
                s.getAddress(),
                s.getCity(),
                s.getLatitude(),
                s.getLongitude(),
                s.getStatus(),
                s.getCreatedAt()
        );
    }
}