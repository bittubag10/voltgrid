package com.voltgrid.voltgrid.vehicle.dto;



import com.voltgrid.voltgrid.enums.ConnectorType;
import com.voltgrid.voltgrid.vehicle.entity.Vehicle;

import java.math.BigDecimal;
import java.time.Instant;

public record VehicleResponse(
        Long id,
        String model,
        BigDecimal usableBatteryKwh,
        BigDecimal baseConsumptionKwhPerKm,
        BigDecimal maxChargeKw,
        ConnectorType connectorType,
        Instant createdAt
) {
    public static VehicleResponse from(Vehicle v) {
        return new VehicleResponse(
                v.getId(),
                v.getModel(),
                v.getUsableBatteryKwh(),
                v.getBaseConsumptionKwhPerKm(),
                v.getMaxChargeKw(),
                v.getConnectorType(),
                v.getCreatedAt()
        );
    }
}