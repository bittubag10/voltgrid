package com.voltgrid.voltgrid;

import com.voltgrid.voltgrid.booking.dto.BookingRequest;
import com.voltgrid.voltgrid.booking.dto.BookingResponse;
import com.voltgrid.voltgrid.booking.entity.Booking;
import com.voltgrid.voltgrid.booking.entity.BookingStatus;
import com.voltgrid.voltgrid.booking.repository.BookingRepository;
import com.voltgrid.voltgrid.booking.scheduler.BookingExpiryScheduler;
import com.voltgrid.voltgrid.booking.service.BookingService;
import com.voltgrid.voltgrid.charger.entity.Charger;
import com.voltgrid.voltgrid.charger.entity.ChargerStatus;
import com.voltgrid.voltgrid.charger.repository.ChargerRepository;
import com.voltgrid.voltgrid.entity.User;
import com.voltgrid.voltgrid.enums.ConnectorType;
import com.voltgrid.voltgrid.enums.Role;
import com.voltgrid.voltgrid.exception.ConflictException;
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
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class BookingConcurrencyTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

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

    @Autowired
    private BookingExpiryScheduler bookingExpiryScheduler;

    private User stationOwner;
    private Station station;
    private Charger charger;
    private final List<User> drivers = new ArrayList<>();
    private final List<Vehicle> vehicles = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String testId = UUID.randomUUID().toString().substring(0, 8);

        // 1. Create Station Owner
        stationOwner = userRepository.save(User.builder()
                .name("Owner " + testId)
                .email("owner_" + testId + "@voltgrid.test")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .role(Role.OWNER)
                .enabled(true)
                .build());

        // 2. Create Active Station
        station = stationRepository.save(Station.builder()
                .name("Station " + testId)
                .owner(stationOwner)
                .address("123 Electric Ave")
                .city("Metro")
                .latitude(new BigDecimal("12.971600"))
                .longitude(new BigDecimal("77.594600"))
                .status(StationStatus.ACTIVE)
                .build());

        // 3. Create Available Charger
        charger = chargerRepository.save(Charger.builder()
                .name("Charger " + testId)
                .station(station)
                .connectorType(ConnectorType.TYPE2)
                .powerKw(new BigDecimal("50.00"))
                .pricePerKwh(new BigDecimal("15.00"))
                .status(ChargerStatus.AVAILABLE)
                .build());

        // 4. Create 20 distinct Drivers and Vehicles
        for (int i = 0; i < 20; i++) {
            User driver = userRepository.save(User.builder()
                    .name("Driver " + testId + " " + i)
                    .email("driver_" + testId + "_" + i + "@voltgrid.test")
                    .passwordHash(passwordEncoder.encode("Secret123!"))
                    .role(Role.DRIVER)
                    .enabled(true)
                    .build());
            drivers.add(driver);

            Vehicle vehicle = vehicleRepository.save(Vehicle.builder()
                    .driver(driver)
                    .model("EV Model " + i)
                    .connectorType(ConnectorType.TYPE2)
                    .usableBatteryKwh(new BigDecimal("60.00"))
                    .baseConsumptionKwhPerKm(new BigDecimal("0.180"))
                    .maxChargeKw(new BigDecimal("50.00"))
                    .active(true)
                    .build());
            vehicles.add(vehicle);
        }
    }

    @AfterEach
    void tearDown() {
        if (charger != null) {
            List<Booking> chargerBookings = bookingRepository.findAll().stream()
                    .filter(b -> b.getCharger().getId().equals(charger.getId()))
                    .toList();
            bookingRepository.deleteAll(chargerBookings);
            chargerRepository.delete(charger);
        }
        if (!vehicles.isEmpty()) {
            vehicleRepository.deleteAll(vehicles);
            vehicles.clear();
        }
        if (!drivers.isEmpty()) {
            userRepository.deleteAll(drivers);
            drivers.clear();
        }
        if (station != null) {
            stationRepository.delete(station);
        }
        if (stationOwner != null) {
            userRepository.delete(stationOwner);
        }
    }

    @Test
    @DisplayName("Step 16 / 16b: 20 threads simultaneously booking same charger and slot - exactly 1 succeeds, zero double-bookings")
    void testConcurrentBookingSameChargerAndSlot() throws InterruptedException {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // Next day 10:00 UTC (guaranteed aligned on 15-min boundary and within allowed advance)
        Instant start = Instant.now().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.HOURS);
        Instant end = start.plus(Duration.ofMinutes(30));

        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Throwable> unexpectedExceptions = new CopyOnWriteArrayList<>();
        List<BookingResponse> responses = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final Long driverId = drivers.get(i).getId();
            final Long vehicleId = vehicles.get(i).getId();

            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    BookingRequest request = new BookingRequest(vehicleId, charger.getId(), start, end);
                    BookingResponse response = bookingService.create(driverId, request);
                    successCount.incrementAndGet();
                    responses.add(response);
                } catch (ConflictException | PessimisticLockingFailureException e) {
                    conflictCount.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedExceptions.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Fire all 20 threads simultaneously!

        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(unexpectedExceptions).isEmpty();
        assertThat(successCount.get())
                .as("Exactly 1 thread must succeed in booking the charger slot")
                .isEqualTo(1);
        assertThat(conflictCount.get())
                .as("All other 19 threads must be rejected with Conflict or Lock timeout")
                .isEqualTo(19);

        // Verify database state: exactly 1 booking exists for this charger in this slot
        long countInDb = bookingRepository.countChargerOverlaps(
                charger.getId(), start, end,
                List.of(BookingStatus.CONFIRMED, BookingStatus.ACTIVE),
                BookingStatus.PENDING_PAYMENT,
                Instant.now()
        );
        assertThat(countInDb)
                .as("Zero double-bookings: exactly 1 active holding booking in DB")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Step 17: Expiry Scheduler bulk-updates expired PENDING_PAYMENT bookings to EXPIRED idempotently")
    void testBookingExpiryScheduler() {
        Instant now = Instant.now();
        Instant start = now.plus(Duration.ofDays(2)).truncatedTo(ChronoUnit.HOURS);
        Instant end = start.plus(Duration.ofMinutes(30));

        // 1. Create a booking that expired 1 minute ago
        Booking expiredBooking = bookingRepository.save(Booking.builder()
                .driver(drivers.get(0))
                .vehicle(vehicles.get(0))
                .charger(charger)
                .startTime(start)
                .endTime(end)
                .status(BookingStatus.PENDING_PAYMENT)
                .expiresAt(now.minusSeconds(60))
                .build());

        // 2. Create another booking whose hold expires in 5 minutes
        Instant start2 = start.plus(Duration.ofHours(1));
        Instant end2 = start2.plus(Duration.ofMinutes(30));
        Booking activeHoldBooking = bookingRepository.save(Booking.builder()
                .driver(drivers.get(1))
                .vehicle(vehicles.get(1))
                .charger(charger)
                .startTime(start2)
                .endTime(end2)
                .status(BookingStatus.PENDING_PAYMENT)
                .expiresAt(now.plusSeconds(300))
                .build());

        // 3. Create a CONFIRMED booking
        Instant start3 = start2.plus(Duration.ofHours(1));
        Instant end3 = start3.plus(Duration.ofMinutes(30));
        Booking confirmedBooking = bookingRepository.save(Booking.builder()
                .driver(drivers.get(2))
                .vehicle(vehicles.get(2))
                .charger(charger)
                .startTime(start3)
                .endTime(end3)
                .status(BookingStatus.CONFIRMED)
                .expiresAt(null)
                .build());

        // Run scheduler
        bookingExpiryScheduler.expirePendingBookings();

        // Verify status changes in PostgreSQL
        Booking reloadedExpired = bookingRepository.findById(expiredBooking.getId()).orElseThrow();
        assertThat(reloadedExpired.getStatus()).isEqualTo(BookingStatus.EXPIRED);

        Booking reloadedActive = bookingRepository.findById(activeHoldBooking.getId()).orElseThrow();
        assertThat(reloadedActive.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);

        Booking reloadedConfirmed = bookingRepository.findById(confirmedBooking.getId()).orElseThrow();
        assertThat(reloadedConfirmed.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        // Verify idempotency: running it a second time updates 0 rows and maintains the same state
        int secondRunUpdated = bookingService.expirePendingBookings();
        assertThat(secondRunUpdated).isEqualTo(0);

        reloadedExpired = bookingRepository.findById(expiredBooking.getId()).orElseThrow();
        assertThat(reloadedExpired.getStatus()).isEqualTo(BookingStatus.EXPIRED);
    }
}
