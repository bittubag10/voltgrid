package com.voltgrid.voltgrid.booking.entity;

import com.voltgrid.voltgrid.charger.entity.Charger;
import com.voltgrid.voltgrid.common.entity.BaseEntity;
import com.voltgrid.voltgrid.entity.User;
import com.voltgrid.voltgrid.vehicle.entity.Vehicle;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "bookings",
        indexes = {
                @Index(name = "idx_bookings_charger_time", columnList = "charger_id, start_time, end_time"),
                @Index(name = "idx_bookings_vehicle_time", columnList = "vehicle_id, start_time, end_time"),
                @Index(name = "idx_bookings_driver", columnList = "driver_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Booking extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private User driver;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "charger_id", nullable = false)
    private Charger charger;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    // Only meaningful while PENDING_PAYMENT: the slot is held until this moment
    @Column(name = "expires_at")
    private Instant expiresAt;

    // Guards concurrent updates of the SAME booking row (it does not stop two INSERTs for one slot)
    @Version
    private Long version;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Booking other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}