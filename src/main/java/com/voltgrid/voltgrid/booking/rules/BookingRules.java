package com.voltgrid.voltgrid.booking.rules;

import com.voltgrid.voltgrid.exception.BadRequestException;

import java.time.Duration;
import java.time.Instant;

/** Slot validation rules (no Spring, no database), easy to unit test. */
public final class BookingRules {

    public static final Duration MIN_LEAD_TIME = Duration.ofMinutes(5);
    public static final Duration MAX_ADVANCE = Duration.ofDays(7);
    public static final long SLOT_MINUTES = 15;
    public static final long MIN_DURATION_MINUTES = 15;
    public static final long MAX_DURATION_MINUTES = 120;

    private BookingRules() {
    }

    public static void validateSlot(Instant start, Instant end, Instant now) {
        if (!end.isAfter(start)) {
            throw new BadRequestException("endTime must be after startTime");
        }
        if (start.isBefore(now.plus(MIN_LEAD_TIME))) {
            throw new BadRequestException("startTime must be at least "
                    + MIN_LEAD_TIME.toMinutes() + " minutes from now");
        }
        if (start.isAfter(now.plus(MAX_ADVANCE))) {
            throw new BadRequestException("startTime cannot be more than "
                    + MAX_ADVANCE.toDays() + " days ahead");
        }
        if (!isOnSlotBoundary(start) || !isOnSlotBoundary(end)) {
            throw new BadRequestException("startTime and endTime must be on a "
                    + SLOT_MINUTES + "-minute boundary (for example 10:00, 10:15, 10:30)");
        }

        long minutes = Duration.between(start, end).toMinutes();
        if (minutes < MIN_DURATION_MINUTES || minutes > MAX_DURATION_MINUTES) {
            throw new BadRequestException("Booking duration must be between "
                    + MIN_DURATION_MINUTES + " and " + MAX_DURATION_MINUTES + " minutes");
        }
    }

    private static boolean isOnSlotBoundary(Instant time) {
        return time.getNano() == 0 && time.getEpochSecond() % (SLOT_MINUTES * 60) == 0;
    }
}