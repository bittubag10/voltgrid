package com.voltgrid.voltgrid.booking.scheduler;

import com.voltgrid.voltgrid.booking.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Step 17: Expiry Scheduler
 * Bulk-updates expired PENDING_PAYMENT bookings to EXPIRED in PostgreSQL using an idempotent UPDATE query.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingExpiryScheduler {

    private final BookingService bookingService;

    @Scheduled(fixedDelayString = "${app.booking.expiry-check-ms:60000}")
    public void expirePendingBookings() {
        int count = bookingService.expirePendingBookings();
        if (count > 0) {
            log.info("Expired {} pending booking(s)", count);
        }
    }
}
