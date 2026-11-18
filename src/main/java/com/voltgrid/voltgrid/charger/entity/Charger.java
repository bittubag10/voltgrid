package com.voltgrid.voltgrid.charger.entity;

import com.voltgrid.voltgrid.common.entity.BaseEntity;
import com.voltgrid.voltgrid.enums.ConnectorType;
import com.voltgrid.voltgrid.station.entity.Station;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "chargers",
        uniqueConstraints = @UniqueConstraint(name = "uk_chargers_station_name", columnNames = {"station_id", "name"}),
        indexes = @Index(name = "idx_chargers_station", columnList = "station_id")
)
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Charger extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "power_kw", nullable = false, precision = 5, scale = 2)
    private BigDecimal powerKw;

    @Enumerated(EnumType.STRING)
    @Column(name = "connector_type", nullable = false, length = 20)
    private ConnectorType connectorType;

    @Column(name = "price_per_kwh", nullable = false, precision = 5, scale = 2)
    private BigDecimal pricePerKwh;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChargerStatus status = ChargerStatus.AVAILABLE;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Charger other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}