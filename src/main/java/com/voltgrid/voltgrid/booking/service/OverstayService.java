package com.voltgrid.voltgrid.booking.service;

import com.voltgrid.voltgrid.booking.entity.Booking;
import com.voltgrid.voltgrid.booking.repository.BookingRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OverstayService {

    private final BookingRepository bookingRepository;
    private final Clock clock;

    @Getter
    @Value("${app.overstay.grace-period-minutes:10}")
    private long gracePeriodMinutes;

    @Getter
    @Value("${app.overstay.penalty-rate-per-minute:10.00}")
    private BigDecimal penaltyRatePerMinute;

    public BigDecimal calculatePenalty(Instant chargeCompletedAt, Instant checkTime) {
        if (chargeCompletedAt == null || checkTime == null) {
            return BigDecimal.ZERO;
        }
        if (checkTime.isBefore(chargeCompletedAt)) {
            return BigDecimal.ZERO;
        }

        long elapsedMinutes = Duration.between(chargeCompletedAt, checkTime).toMinutes();
        if (elapsedMinutes <= gracePeriodMinutes) {
            return BigDecimal.ZERO;
        }

        long billableMinutes = elapsedMinutes - gracePeriodMinutes;
        return penaltyRatePerMinute.multiply(BigDecimal.valueOf(billableMinutes))
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public int accrueOverstayPenalties() {
        Instant now = clock.instant();
        List<Booking> activeOverstayCandidates = bookingRepository.findActiveOverstayCandidates();
        int updatedCount = 0;

        for (Booking booking : activeOverstayCandidates) {
            BigDecimal penalty = calculatePenalty(booking.getChargeCompletedAt(), now);
            if (penalty.compareTo(booking.getOverstayPenalty()) != 0) {
                booking.setOverstayPenalty(penalty);
                BigDecimal base = booking.getBaseAmount() != null ? booking.getBaseAmount() : BigDecimal.ZERO;
                booking.setTotalAmount(base.add(penalty));
                updatedCount++;
            }
        }

        if (updatedCount > 0) {
            log.info("Accrued overstay penalty on {} active sessions at {}", updatedCount, now);
        }
        return updatedCount;
    }
}
