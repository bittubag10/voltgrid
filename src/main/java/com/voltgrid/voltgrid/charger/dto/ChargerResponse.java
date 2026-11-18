package com.voltgrid.voltgrid.charger.dto;

import com.voltgrid.voltgrid.charger.entity.Charger;
import com.voltgrid.voltgrid.charger.entity.ChargerStatus;
import com.voltgrid.voltgrid.enums.ConnectorType;

import java.math.BigDecimal;
import java.time.Instant;

public record ChargerResponse(
        Long id,
        Long stationId,
        String name,
        BigDecimal powerKw,
        ConnectorType connectorType,
        BigDecimal pricePerKwh,
        ChargerStatus status,
        Instant createdAt
) {
    public static ChargerResponse from(Charger c) {
        return new ChargerResponse(
                c.getId(),
                c.getStation().getId(),
                c.getName(),
                c.getPowerKw(),
                c.getConnectorType(),
                c.getPricePerKwh(),
                c.getStatus(),
                c.getCreatedAt()
        );
    }
}