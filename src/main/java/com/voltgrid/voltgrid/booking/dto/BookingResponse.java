package com.voltgrid.voltgrid.booking.dto;

import com.voltgrid.voltgrid.booking.entity.Booking;
import com.voltgrid.voltgrid.booking.entity.BookingStatus;
import com.voltgrid.voltgrid.enums.ConnectorType;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

public record BookingResponse(
        Long id,
        BookingStatus status,
        Long chargerId,
        String chargerName,
        ConnectorType connectorType,
        Long stationId,
        String stationName,
        String stationAddress,
        Long vehicleId,
        String vehicleModel,
        Instant startTime,
        Instant endTime,
        long durationMinutes,
        Instant expiresAt,
        BigDecimal baseAmount,
        BigDecimal overstayPenalty,
        BigDecimal totalAmount,
        Instant sessionStartTime,
        Instant chargeCompletedAt,
        Instant plugOutAt,
        String paymentIntentId,
        Instant createdAt
) {
    public BookingResponse(
            Long id,
            BookingStatus status,
            Long chargerId,
            String chargerName,
            ConnectorType connectorType,
            Long stationId,
            String stationName,
            String stationAddress,
            Long vehicleId,
            String vehicleModel,
            Instant startTime,
            Instant endTime,
            long durationMinutes,
            Instant expiresAt,
            Instant createdAt
    ) {
        this(id, status, chargerId, chargerName, connectorType, stationId, stationName,
                stationAddress, vehicleId, vehicleModel, startTime, endTime, durationMinutes,
                expiresAt, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, null, null, createdAt);
    }

    public static BookingResponse from(Booking b, Instant now) {

        // A PENDING_PAYMENT booking whose hold has run out is already free to others.
        // Show it as EXPIRED right away (a cleanup job will persist this in a later step).
        boolean holdExpired = b.getStatus() == BookingStatus.PENDING_PAYMENT
                && b.getExpiresAt() != null
                && !b.getExpiresAt().isAfter(now);
        BookingStatus effectiveStatus = holdExpired ? BookingStatus.EXPIRED : b.getStatus();

        return new BookingResponse(
                b.getId(),
                effectiveStatus,
                b.getCharger().getId(),
                b.getCharger().getName(),
                b.getCharger().getConnectorType(),
                b.getCharger().getStation().getId(),
                b.getCharger().getStation().getName(),
                b.getCharger().getStation().getAddress(),
                b.getVehicle().getId(),
                b.getVehicle().getModel(),
                b.getStartTime(),
                b.getEndTime(),
                Duration.between(b.getStartTime(), b.getEndTime()).toMinutes(),
                effectiveStatus == BookingStatus.PENDING_PAYMENT ? b.getExpiresAt() : null,
                b.getBaseAmount() != null ? b.getBaseAmount() : BigDecimal.ZERO,
                b.getOverstayPenalty() != null ? b.getOverstayPenalty() : BigDecimal.ZERO,
                b.getTotalAmount() != null ? b.getTotalAmount() : BigDecimal.ZERO,
                b.getSessionStartTime(),
                b.getChargeCompletedAt(),
                b.getPlugOutAt(),
                b.getPaymentIntentId(),
                b.getCreatedAt()
        );
    }
}
