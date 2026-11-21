package com.voltgrid.voltgrid.booking.service;

import com.voltgrid.voltgrid.booking.dto.BookingRequest;
import com.voltgrid.voltgrid.booking.dto.BookingResponse;
import com.voltgrid.voltgrid.booking.entity.Booking;
import com.voltgrid.voltgrid.booking.entity.BookingStatus;
import com.voltgrid.voltgrid.booking.repository.BookingRepository;
import com.voltgrid.voltgrid.booking.rules.BookingRules;
import com.voltgrid.voltgrid.charger.entity.Charger;
import com.voltgrid.voltgrid.charger.entity.ChargerStatus;
import com.voltgrid.voltgrid.charger.repository.ChargerRepository;
import com.voltgrid.voltgrid.dto.PageResponse;
import com.voltgrid.voltgrid.entity.User;
import com.voltgrid.voltgrid.exception.BadRequestException;
import com.voltgrid.voltgrid.exception.ConflictException;
import com.voltgrid.voltgrid.exception.ResourceNotFoundException;
import com.voltgrid.voltgrid.repository.UserRepository;
import com.voltgrid.voltgrid.station.entity.StationStatus;
import com.voltgrid.voltgrid.vehicle.entity.Vehicle;
import com.voltgrid.voltgrid.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingService {

    private static final int MAX_ACTIVE_UPCOMING_BOOKINGS = 5;
    private static final List<BookingStatus> HOLDING = List.of(BookingStatus.CONFIRMED, BookingStatus.ACTIVE);

    private final BookingRepository bookingRepository;
    private final VehicleRepository vehicleRepository;
    private final ChargerRepository chargerRepository;
    private final UserRepository userRepository;
    private final OverstayService overstayService;
    private final Clock clock;

    @Value("${app.booking.payment-hold-minutes:5}")
    private long paymentHoldMinutes;

    @Transactional
    public BookingResponse create(Long driverId, BookingRequest request) {
        Instant now = clock.instant();
        Instant start = request.startTime();
        Instant end = request.endTime();

        BookingRules.validateSlot(start, end, now);

        // LOCK ORDER (always the same, so two requests can never wait on each other in a cycle):
        //   1) the driver row  -> serialises this driver's own bookings (vehicle overlap, booking limit)
        //   2) the charger row -> serialises everyone competing for this charger's slots
        // Both locks are released automatically when this transaction commits or rolls back.
        User driver = userRepository.findByIdForUpdate(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Ownership check: someone else's vehicle is "not found"
        Vehicle vehicle = vehicleRepository.findByIdAndDriverIdAndActiveTrue(request.vehicleId(), driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));

        Charger charger = chargerRepository.findByIdForUpdate(request.chargerId())
                .filter(c -> c.getStatus() != ChargerStatus.OUT_OF_SERVICE
                        && c.getStation().getStatus() == StationStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Charger not found or not available for booking"));

        if (vehicle.getConnectorType() != charger.getConnectorType()) {
            throw new BadRequestException("This charger's connector (" + charger.getConnectorType()
                    + ") does not fit your vehicle (" + vehicle.getConnectorType() + ")");
        }

        if (bookingRepository.countActiveUpcoming(driverId, HOLDING, BookingStatus.PENDING_PAYMENT, now)
                >= MAX_ACTIVE_UPCOMING_BOOKINGS) {
            throw new BadRequestException("You can have at most " + MAX_ACTIVE_UPCOMING_BOOKINGS
                    + " active upcoming bookings");
        }

        // Safe now: nobody else can insert a booking for this charger (or for this driver)
        // until we commit, and after we commit they will see our row in these queries.
        if (bookingRepository.countChargerOverlaps(
                charger.getId(), start, end, HOLDING, BookingStatus.PENDING_PAYMENT, now) > 0) {
            throw new ConflictException("This time slot is no longer available for the selected charger");
        }
        if (bookingRepository.countVehicleOverlaps(
                vehicle.getId(), start, end, HOLDING, BookingStatus.PENDING_PAYMENT, now) > 0) {
            throw new ConflictException("Your vehicle already has a booking in this time slot");
        }

        long durationMinutes = Duration.between(start, end).toMinutes();
        BigDecimal hours = BigDecimal.valueOf(durationMinutes).divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
        BigDecimal estimatedKwh = charger.getPowerKw().multiply(hours);
        BigDecimal baseAmount = estimatedKwh.multiply(charger.getPricePerKwh()).setScale(2, RoundingMode.HALF_UP);

        Booking booking = Booking.builder()
                .driver(driver)
                .vehicle(vehicle)
                .charger(charger)
                .startTime(start)
                .endTime(end)
                .status(BookingStatus.PENDING_PAYMENT)
                .expiresAt(now.plus(Duration.ofMinutes(paymentHoldMinutes)))
                .baseAmount(baseAmount)
                .overstayPenalty(BigDecimal.ZERO)
                .totalAmount(baseAmount)
                .build();

        return BookingResponse.from(bookingRepository.save(booking), now);
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> list(Long driverId, BookingStatus status, Pageable pageable) {
        Instant now = clock.instant();

        Page<Booking> page = (status == null)
                ? bookingRepository.findByDriverId(driverId, pageable)
                : bookingRepository.findByDriverIdAndStatus(driverId, status, pageable);

        return PageResponse.from(page.map(b -> BookingResponse.from(b, now)));
    }

    @Transactional(readOnly = true)
    public BookingResponse get(Long driverId, Long bookingId) {
        return BookingResponse.from(findOwned(driverId, bookingId), clock.instant());
    }

    @Transactional
    public BookingResponse cancel(Long driverId, Long bookingId) {
        Instant now = clock.instant();
        Booking booking = findOwned(driverId, bookingId);

        boolean cancellableStatus = booking.getStatus() == BookingStatus.PENDING_PAYMENT
                || booking.getStatus() == BookingStatus.CONFIRMED;

        if (!cancellableStatus || !booking.getStartTime().isAfter(now)) {
            throw new ConflictException("This booking can no longer be cancelled");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setExpiresAt(null);
        return BookingResponse.from(booking, now);
    }

    @Transactional
    public int expirePendingBookings() {
        return bookingRepository.expirePendingBookings(clock.instant());
    }

    @Transactional
    public BookingResponse startSession(Long driverId, Long bookingId) {
        Instant now = clock.instant();
        Booking booking = findOwned(driverId, bookingId);

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ConflictException("Booking must be CONFIRMED to start charging session (current status: "
                    + booking.getStatus() + ")");
        }

        booking.setStatus(BookingStatus.ACTIVE);
        booking.setSessionStartTime(now);
        booking.getCharger().setStatus(ChargerStatus.OCCUPIED);

        return BookingResponse.from(booking, now);
    }

    @Transactional
    public BookingResponse completeCharging(Long driverId, Long bookingId) {
        Instant now = clock.instant();
        Booking booking = findOwned(driverId, bookingId);

        if (booking.getStatus() != BookingStatus.ACTIVE) {
            throw new ConflictException("Booking must be ACTIVE to mark charging complete (current status: "
                    + booking.getStatus() + ")");
        }

        if (booking.getChargeCompletedAt() == null) {
            booking.setChargeCompletedAt(now);
        }

        // Car remains plugged in, charger remains OCCUPIED, booking remains ACTIVE
        return BookingResponse.from(booking, now);
    }

    @Transactional
    public BookingResponse plugOut(Long driverId, Long bookingId) {
        Instant now = clock.instant();
        Booking booking = findOwned(driverId, bookingId);

        if (booking.getStatus() != BookingStatus.ACTIVE) {
            throw new ConflictException("Booking must be ACTIVE to plug out (current status: "
                    + booking.getStatus() + ")");
        }

        booking.setPlugOutAt(now);
        if (booking.getChargeCompletedAt() == null) {
            booking.setChargeCompletedAt(now);
        }

        BigDecimal penalty = overstayService.calculatePenalty(booking.getChargeCompletedAt(), now);
        booking.setOverstayPenalty(penalty);
        BigDecimal base = booking.getBaseAmount() != null ? booking.getBaseAmount() : BigDecimal.ZERO;
        booking.setTotalAmount(base.add(penalty));

        booking.setStatus(BookingStatus.COMPLETED);
        booking.getCharger().setStatus(ChargerStatus.AVAILABLE);

        return BookingResponse.from(booking, now);
    }

    @Value("${app.payment.mock-enabled:false}")
    private boolean mockPaymentEnabled;

    @Transactional
    public BookingResponse simulateOverstay(Long driverId, Long bookingId, int minutes) {
        if (!mockPaymentEnabled) {
            throw new ConflictException("Simulator helpers are disabled in this environment");
        }
        Booking booking = (driverId != null) ? findOwned(driverId, bookingId)
                : bookingRepository.findById(bookingId).orElseThrow(() -> new ResourceNotFoundException("Booking not found"));

        if (booking.getStatus() != BookingStatus.ACTIVE && booking.getStatus() != BookingStatus.COMPLETED) {
            throw new ConflictException("Booking must be ACTIVE or COMPLETED to simulate overstay (current status: "
                    + booking.getStatus() + ")");
        }

        Instant now = clock.instant();
        Instant shifted = now.minus(Duration.ofMinutes(minutes));
        booking.setChargeCompletedAt(shifted);

        Instant endCalc = (booking.getPlugOutAt() != null) ? booking.getPlugOutAt() : now;
        BigDecimal penalty = overstayService.calculatePenalty(shifted, endCalc);
        booking.setOverstayPenalty(penalty);
        BigDecimal base = booking.getBaseAmount() != null ? booking.getBaseAmount() : BigDecimal.ZERO;
        booking.setTotalAmount(base.add(penalty));

        Booking saved = bookingRepository.save(booking);
        return BookingResponse.from(saved, now);
    }

    public Booking findOwned(Long driverId, Long bookingId) {
        return bookingRepository.findByIdAndDriverId(bookingId, driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
    }
}