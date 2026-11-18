package com.voltgrid.voltgrid;

import com.voltgrid.voltgrid.booking.dto.BookingRequest;
import com.voltgrid.voltgrid.booking.dto.BookingResponse;
import com.voltgrid.voltgrid.booking.entity.Booking;
import com.voltgrid.voltgrid.booking.entity.BookingStatus;
import com.voltgrid.voltgrid.booking.repository.BookingRepository;
import com.voltgrid.voltgrid.booking.service.BookingService;
import com.voltgrid.voltgrid.booking.service.OverstayService;
import com.voltgrid.voltgrid.charger.entity.Charger;
import com.voltgrid.voltgrid.charger.entity.ChargerStatus;
import com.voltgrid.voltgrid.charger.repository.ChargerRepository;
import com.voltgrid.voltgrid.entity.User;
import com.voltgrid.voltgrid.enums.ConnectorType;
import com.voltgrid.voltgrid.enums.Role;
import com.voltgrid.voltgrid.exception.BadRequestException;
import com.voltgrid.voltgrid.exception.ConflictException;
import com.voltgrid.voltgrid.payment.dto.PaymentIntentResponse;
import com.voltgrid.voltgrid.payment.entity.Payment;
import com.voltgrid.voltgrid.payment.entity.PaymentStatus;
import com.voltgrid.voltgrid.payment.gateway.StripePaymentGateway;
import com.voltgrid.voltgrid.payment.repository.PaymentRepository;
import com.voltgrid.voltgrid.payment.service.PaymentService;
import com.voltgrid.voltgrid.repository.UserRepository;
import com.voltgrid.voltgrid.station.entity.Station;
import com.voltgrid.voltgrid.station.entity.StationStatus;
import com.voltgrid.voltgrid.station.repository.StationRepository;
import com.voltgrid.voltgrid.vehicle.entity.Vehicle;
import com.voltgrid.voltgrid.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
public class PaymentAndSessionLifecycleTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OverstayService overstayService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private ChargerRepository chargerRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User driver;
    private User owner;
    private Station station;
    private Charger charger;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        String testId = UUID.randomUUID().toString().substring(0, 8);

        owner = userRepository.save(User.builder()
                .name("Owner " + testId)
                .email("owner_" + testId + "@test.voltgrid")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .role(Role.OWNER)
                .enabled(true)
                .build());

        station = stationRepository.save(Station.builder()
                .name("Station " + testId)
                .owner(owner)
                .address("100 Innovation Way")
                .city("Metro")
                .latitude(new BigDecimal("12.971600"))
                .longitude(new BigDecimal("77.594600"))
                .status(StationStatus.ACTIVE)
                .build());

        charger = chargerRepository.save(Charger.builder()
                .name("Charger " + testId)
                .station(station)
                .connectorType(ConnectorType.TYPE2)
                .powerKw(new BigDecimal("60.00"))
                .pricePerKwh(new BigDecimal("12.50"))
                .status(ChargerStatus.AVAILABLE)
                .build());

        driver = userRepository.save(User.builder()
                .name("Driver " + testId)
                .email("driver_" + testId + "@test.voltgrid")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .role(Role.DRIVER)
                .enabled(true)
                .build());

        vehicle = vehicleRepository.save(Vehicle.builder()
                .driver(driver)
                .model("Volt Sedan " + testId)
                .connectorType(ConnectorType.TYPE2)
                .usableBatteryKwh(new BigDecimal("75.00"))
                .baseConsumptionKwhPerKm(new BigDecimal("0.160"))
                .maxChargeKw(new BigDecimal("60.00"))
                .active(true)
                .build());
    }

    @AfterEach
    void tearDown() {
        if (charger != null) {
            List<Booking> chargerBookings = bookingRepository.findAll().stream()
                    .filter(b -> b.getCharger().getId().equals(charger.getId()))
                    .toList();
            for (Booking b : chargerBookings) {
                paymentRepository.deleteAll(paymentRepository.findByBookingId(b.getId()));
            }
            bookingRepository.deleteAll(chargerBookings);
            chargerRepository.delete(charger);
        }
        if (vehicle != null) {
            vehicleRepository.delete(vehicle);
        }
        if (driver != null) {
            userRepository.delete(driver);
        }
        if (station != null) {
            stationRepository.delete(station);
        }
        if (owner != null) {
            userRepository.delete(owner);
        }
    }

    private Booking createTestBooking() {
        Instant start = Instant.now().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.HOURS);
        Instant end = start.plus(Duration.ofMinutes(30));
        BookingResponse response = bookingService.create(driver.getId(),
                new BookingRequest(vehicle.getId(), charger.getId(), start, end));
        return bookingRepository.findById(response.id()).orElseThrow();
    }

    @Test
    @DisplayName("Step 18: Payment pre-authorization and mock confirmation flow")
    void testPaymentPreAuthAndMockConfirmation() {
        Booking booking = createTestBooking();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(booking.getBaseAmount()).isNotNull().isGreaterThan(BigDecimal.ZERO);

        // 1. Pre-authorize payment with idempotency key
        String idempotencyKey = "idemp_" + UUID.randomUUID();
        PaymentIntentResponse intent = paymentService.preAuthorize(driver.getId(), booking.getId(), idempotencyKey);
        assertThat(intent).isNotNull();
        assertThat(intent.paymentIntentId()).startsWith("pi_");

        Payment payment = paymentRepository.findFirstByBookingIdOrderByIdDesc(booking.getId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(payment.getIdempotencyKey()).isEqualTo(idempotencyKey);

        // 2. Mock payment confirmation fallback
        BookingResponse confirmed = paymentService.mockConfirmPayment(driver.getId(), booking.getId());
        assertThat(confirmed.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(confirmed.expiresAt()).isNull();

        Booking reloaded = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(reloaded.getExpiresAt()).isNull();

        // 3. Confirming an already-confirmed booking is idempotent
        BookingResponse idempotentConfirm = paymentService.mockConfirmPayment(driver.getId(), booking.getId());
        assertThat(idempotentConfirm.status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Step 18: Stripe webhook signature verification moves booking to CONFIRMED")
    void testStripeWebhookConfirmation() {
        Booking booking = createTestBooking();
        String paymentIntentId = "pi_stripe_" + UUID.randomUUID().toString().replace("-", "");

        String secret = "whsec_test_secret_123456789";
        long timestamp = Instant.now().getEpochSecond();

        String payload = """
                {
                  "type": "payment_intent.succeeded",
                  "data": {
                    "object": {
                      "id": "%s",
                      "amount": 25000,
                      "currency": "inr",
                      "metadata": {
                        "bookingId": "%d"
                      }
                    }
                  }
                }
                """.formatted(paymentIntentId, booking.getId());

        String validSignature = StripePaymentGateway.generateSignatureHeader(payload, secret, timestamp);

        // Test signature verification directly
        boolean isValid = StripePaymentGateway.verifyStripeSignature(payload, validSignature, secret);
        assertThat(isValid).isTrue();

        boolean isInvalid = StripePaymentGateway.verifyStripeSignature(payload, validSignature + "bad", secret);
        assertThat(isInvalid).isFalse();

        // Process webhook
        paymentService.handleStripeWebhook(payload, validSignature);

        Booking reloaded = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(reloaded.getExpiresAt()).isNull();
        assertThat(reloaded.getPaymentIntentId()).isEqualTo(paymentIntentId);

        // Test with invalid signature throws BadRequestException
        assertThatThrownBy(() -> paymentService.handleStripeWebhook(payload, "invalid_sig_header"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Step 19: Charging session simulator lifecycle (start -> charge-complete -> plug-out)")
    void testChargingSessionLifecycle() {
        Booking booking = createTestBooking();
        // Move booking to CONFIRMED first
        paymentService.mockConfirmPayment(driver.getId(), booking.getId());

        // 1. start -> booking becomes ACTIVE, charger becomes OCCUPIED
        BookingResponse started = bookingService.startSession(driver.getId(), booking.getId());
        assertThat(started.status()).isEqualTo(BookingStatus.ACTIVE);
        assertThat(started.sessionStartTime()).isNotNull();

        Charger reloadedCharger = chargerRepository.findById(charger.getId()).orElseThrow();
        assertThat(reloadedCharger.getStatus()).isEqualTo(ChargerStatus.OCCUPIED);

        // 2. charge-complete -> records timestamp, car remains plugged in, charger remains OCCUPIED
        BookingResponse chargeComplete = bookingService.completeCharging(driver.getId(), booking.getId());
        assertThat(chargeComplete.status()).isEqualTo(BookingStatus.ACTIVE);
        assertThat(chargeComplete.chargeCompletedAt()).isNotNull();

        reloadedCharger = chargerRepository.findById(charger.getId()).orElseThrow();
        assertThat(reloadedCharger.getStatus()).isEqualTo(ChargerStatus.OCCUPIED);

        // 3. plug-out -> booking becomes COMPLETED, charger returns to AVAILABLE
        BookingResponse pluggedOut = bookingService.plugOut(driver.getId(), booking.getId());
        assertThat(pluggedOut.status()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(pluggedOut.plugOutAt()).isNotNull();

        reloadedCharger = chargerRepository.findById(charger.getId()).orElseThrow();
        assertThat(reloadedCharger.getStatus()).isEqualTo(ChargerStatus.AVAILABLE);

        // 4. Starting a COMPLETED booking must fail
        assertThatThrownBy(() -> bookingService.startSession(driver.getId(), booking.getId()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("Step 20: Overstay penalty calculation and settlement at plug-out")
    void testOverstayPenaltyCalculationAndSettlement() {
        Instant completedAt = Instant.now().minus(Duration.ofMinutes(25)); // 25 mins ago
        // Grace period = 10 mins, billable = 15 mins @ ₹10/min = ₹150.00
        BigDecimal penalty = overstayService.calculatePenalty(completedAt, Instant.now());
        assertThat(penalty).isEqualByComparingTo(new BigDecimal("150.00"));

        // When within grace period (e.g. 5 mins ago): penalty = 0
        Instant recentComplete = Instant.now().minus(Duration.ofMinutes(5));
        BigDecimal noPenalty = overstayService.calculatePenalty(recentComplete, Instant.now());
        assertThat(noPenalty).isEqualByComparingTo(BigDecimal.ZERO);

        // Test active session overstay accrual
        Booking booking = createTestBooking();
        paymentService.mockConfirmPayment(driver.getId(), booking.getId());
        bookingService.startSession(driver.getId(), booking.getId());
        bookingService.completeCharging(driver.getId(), booking.getId());

        // Simulate charging completed 25 minutes ago
        Booking activeBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        activeBooking.setChargeCompletedAt(Instant.now().minus(Duration.ofMinutes(25)));
        bookingRepository.save(activeBooking);

        // Run accrual job
        int accruedCount = overstayService.accrueOverstayPenalties();
        assertThat(accruedCount).isGreaterThanOrEqualTo(1);

        Booking accruedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(accruedBooking.getOverstayPenalty()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(accruedBooking.getTotalAmount()).isEqualByComparingTo(
                accruedBooking.getBaseAmount().add(new BigDecimal("150.00"))
        );

        // Plug-out finalizes and settles total amount
        BookingResponse finalResponse = bookingService.plugOut(driver.getId(), booking.getId());
        assertThat(finalResponse.status()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(finalResponse.overstayPenalty()).isGreaterThanOrEqualTo(new BigDecimal("150.00"));
    }
}
