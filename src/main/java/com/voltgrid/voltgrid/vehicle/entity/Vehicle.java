package com.voltgrid.voltgrid.vehicle.entity;


import com.voltgrid.voltgrid.common.entity.BaseEntity;
import com.voltgrid.voltgrid.entity.User;
import com.voltgrid.voltgrid.enums.ConnectorType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "vehicles",
        indexes = @Index(name = "idx_vehicles_driver", columnList = "driver_id")
)
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Vehicle extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private User driver;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(name = "usable_battery_kwh", nullable = false, precision = 6, scale = 2)
    private BigDecimal usableBatteryKwh;

    @Column(name = "base_consumption_kwh_per_km", nullable = false, precision = 5, scale = 3)
    private BigDecimal baseConsumptionKwhPerKm;

    @Column(name = "max_charge_kw", nullable = false, precision = 6, scale = 2)
    private BigDecimal maxChargeKw;

    @Enumerated(EnumType.STRING)
    @Column(name = "connector_type", nullable = false, length = 20)
    private ConnectorType connectorType;

    // Soft delete: future bookings will reference vehicles, so we never hard-delete
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vehicle other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}