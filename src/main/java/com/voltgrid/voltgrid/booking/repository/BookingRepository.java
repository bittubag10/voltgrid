package com.voltgrid.voltgrid.booking.repository;

import com.voltgrid.voltgrid.booking.entity.Booking;
import com.voltgrid.voltgrid.booking.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Two intervals overlap when: slotStart < existingEnd AND slotEnd > existingStart.
    // A PENDING_PAYMENT booking only holds the slot until its expiresAt.

    @Query("""
            select count(b) from Booking b
            where b.charger.id = :chargerId
              and b.startTime < :slotEnd and b.endTime > :slotStart
              and (b.status in (:holding) or (b.status = :pending and b.expiresAt > :now))
            """)
    long countChargerOverlaps(@Param("chargerId") Long chargerId,
                              @Param("slotStart") Instant slotStart,
                              @Param("slotEnd") Instant slotEnd,
                              @Param("holding") Collection<BookingStatus> holding,
                              @Param("pending") BookingStatus pending,
                              @Param("now") Instant now);

    @Query("""
            select count(b) from Booking b
            where b.vehicle.id = :vehicleId
              and b.startTime < :slotEnd and b.endTime > :slotStart
              and (b.status in (:holding) or (b.status = :pending and b.expiresAt > :now))
            """)
    long countVehicleOverlaps(@Param("vehicleId") Long vehicleId,
                              @Param("slotStart") Instant slotStart,
                              @Param("slotEnd") Instant slotEnd,
                              @Param("holding") Collection<BookingStatus> holding,
                              @Param("pending") BookingStatus pending,
                              @Param("now") Instant now);

    @Query("""
            select count(b) from Booking b
            where b.driver.id = :driverId
              and b.endTime > :now
              and (b.status in (:holding) or (b.status = :pending and b.expiresAt > :now))
            """)
    long countActiveUpcoming(@Param("driverId") Long driverId,
                             @Param("holding") Collection<BookingStatus> holding,
                             @Param("pending") BookingStatus pending,
                             @Param("now") Instant now);

    // EntityGraph loads charger, station and vehicle in the same query (no N+1 when mapping to DTOs)

    @EntityGraph(attributePaths = {"charger", "charger.station", "vehicle"})
    Page<Booking> findByDriverId(Long driverId, Pageable pageable);

    @EntityGraph(attributePaths = {"charger", "charger.station", "vehicle"})
    Page<Booking> findByDriverIdAndStatus(Long driverId, BookingStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"charger", "charger.station", "vehicle"})
    Optional<Booking> findByIdAndDriverId(Long id, Long driverId);

    @Modifying(clearAutomatically = true)
    @Query("""
            update Booking b
            set b.status = com.voltgrid.voltgrid.booking.entity.BookingStatus.EXPIRED
            where b.status = com.voltgrid.voltgrid.booking.entity.BookingStatus.PENDING_PAYMENT
              and b.expiresAt <= :now
            """)
    int expirePendingBookings(@Param("now") Instant now);
}