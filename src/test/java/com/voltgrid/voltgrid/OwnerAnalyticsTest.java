package com.voltgrid.voltgrid;

import com.voltgrid.voltgrid.booking.entity.Booking;
import com.voltgrid.voltgrid.booking.entity.BookingStatus;
import com.voltgrid.voltgrid.booking.repository.BookingRepository;
import com.voltgrid.voltgrid.charger.entity.Charger;
import com.voltgrid.voltgrid.charger.entity.ChargerStatus;
import com.voltgrid.voltgrid.charger.repository.ChargerRepository;
import com.voltgrid.voltgrid.entity.User;
import com.voltgrid.voltgrid.enums.ConnectorType;
import com.voltgrid.voltgrid.enums.Role;
import com.voltgrid.voltgrid.repository.UserRepository;
import com.voltgrid.voltgrid.station.dto.OwnerAnalyticsResponse;
import com.voltgrid.voltgrid.station.entity.Station;
import com.voltgrid.voltgrid.station.entity.StationStatus;
import com.voltgrid.voltgrid.station.repository.StationRepository;
import com.voltgrid.voltgrid.station.service.OwnerAnalyticsService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class OwnerAnalyticsTest {

    @Autowired
    private OwnerAnalyticsService ownerAnalyticsService;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private ChargerRepository chargerRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User owner1;
    private User owner2;
    private User driver;
    private Vehicle vehicle;

    private Station station1;
    private Station station2;
    private Station stationOwner2;

    private Charger charger1A;
    private Charger charger1B;
    private Charger charger2A;
    private Charger chargerOwner2;

    private final List<Booking> testBookings = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String testId = UUID.randomUUID().toString().substring(0, 8);

        // 1. Create Owners & Driver
        owner1 = userRepository.save(User.builder()
                .name("Owner One " + testId)
                .email("owner1_" + testId + "@voltgrid.test")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .role(Role.OWNER)
                .enabled(true)
                .build());

        owner2 = userRepository.save(User.builder()
                .name("Owner Two " + testId)
                .email("owner2_" + testId + "@voltgrid.test")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .role(Role.OWNER)
                .enabled(true)
                .build());

        driver = userRepository.save(User.builder()
                .name("Driver " + testId)
                .email("driver_" + testId + "@voltgrid.test")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .role(Role.DRIVER)
                .enabled(true)
                .build());

        // 2. Create Vehicle
        vehicle = vehicleRepository.save(Vehicle.builder()
                .driver(driver)
                .model("Tesla Model Y " + testId)
                .usableBatteryKwh(new BigDecimal("75.00"))
                .baseConsumptionKwhPerKm(new BigDecimal("0.160"))
                .maxChargeKw(new BigDecimal("150.00"))
                .connectorType(ConnectorType.CCS2)
                .active(true)
                .build());

        // 3. Create Stations for Owner 1
        station1 = stationRepository.save(Station.builder()
                .name("Vadodara Central Hub " + testId)
                .owner(owner1)
                .address("Alkapuri Main")
                .city("Vadodara")
                .latitude(new BigDecimal("22.307200"))
                .longitude(new BigDecimal("73.181200"))
                .status(StationStatus.ACTIVE)
                .build());

        station2 = stationRepository.save(Station.builder()
                .name("Sayajigunj Fast Hub " + testId)
                .owner(owner1)
                .address("Sayajigunj Station Rd")
                .city("Vadodara")
                .latitude(new BigDecimal("22.312000"))
                .longitude(new BigDecimal("73.189000"))
                .status(StationStatus.ACTIVE)
                .build());

        // Station for Owner 2
        stationOwner2 = stationRepository.save(Station.builder()
                .name("Ahmedabad Metro Hub " + testId)
                .owner(owner2)
                .address("SG Highway")
                .city("Ahmedabad")
                .latitude(new BigDecimal("23.022500"))
                .longitude(new BigDecimal("72.571400"))
                .status(StationStatus.ACTIVE)
                .build());

        // 4. Create Chargers
        charger1A = chargerRepository.save(Charger.builder()
                .station(station1)
                .name("Bay 1A")
                .powerKw(new BigDecimal("120.00"))
                .connectorType(ConnectorType.CCS2)
                .pricePerKwh(new BigDecimal("18.00"))
                .status(ChargerStatus.AVAILABLE)
                .build());

        charger1B = chargerRepository.save(Charger.builder()
                .station(station1)
                .name("Bay 1B")
                .powerKw(new BigDecimal("60.00"))
                .connectorType(ConnectorType.TYPE2)
                .pricePerKwh(new BigDecimal("15.00"))
                .status(ChargerStatus.AVAILABLE)
                .build());

        charger2A = chargerRepository.save(Charger.builder()
                .station(station2)
                .name("Bay 2A")
                .powerKw(new BigDecimal("150.00"))
                .connectorType(ConnectorType.CCS2)
                .pricePerKwh(new BigDecimal("20.00"))
                .status(ChargerStatus.AVAILABLE)
                .build());

        chargerOwner2 = chargerRepository.save(Charger.builder()
                .station(stationOwner2)
                .name("Owner2 Bay")
                .powerKw(new BigDecimal("150.00"))
                .connectorType(ConnectorType.CCS2)
                .pricePerKwh(new BigDecimal("22.00"))
                .status(ChargerStatus.AVAILABLE)
                .build());
    }

    @AfterEach
    void tearDown() {
        for (Booking b : testBookings) {
            try {
                bookingRepository.delete(b);
            } catch (Exception ignored) {}
        }
        testBookings.clear();

        if (charger1A != null) chargerRepository.delete(charger1A);
        if (charger1B != null) chargerRepository.delete(charger1B);
        if (charger2A != null) chargerRepository.delete(charger2A);
        if (chargerOwner2 != null) chargerRepository.delete(chargerOwner2);

        if (station1 != null) stationRepository.delete(station1);
        if (station2 != null) stationRepository.delete(station2);
        if (stationOwner2 != null) stationRepository.delete(stationOwner2);

        if (vehicle != null) vehicleRepository.delete(vehicle);
        if (driver != null) userRepository.delete(driver);
        if (owner1 != null) userRepository.delete(owner1);
        if (owner2 != null) userRepository.delete(owner2);
    }

    @Test
    @DisplayName("Owner Analytics: Calculates overall and per-station completed sessions, energy revenue, and overstay penalty")
    void testOwnerAnalyticsOverallAndPerStationMetrics() {
        Instant baseTime = Instant.now().minus(Duration.ofDays(2)).truncatedTo(ChronoUnit.HOURS);

        // Booking 1 (Station 1, Charger 1A): COMPLETED session, Energy: ₹500, Penalty: ₹120
        Booking b1 = bookingRepository.save(Booking.builder()
                .driver(driver)
                .vehicle(vehicle)
                .charger(charger1A)
                .startTime(baseTime)
                .endTime(baseTime.plus(Duration.ofMinutes(45)))
                .sessionStartTime(baseTime)
                .chargeCompletedAt(baseTime.plus(Duration.ofMinutes(40)))
                .plugOutAt(baseTime.plus(Duration.ofMinutes(55))) // 15m overstay
                .status(BookingStatus.COMPLETED)
                .baseAmount(new BigDecimal("500.00"))
                .overstayPenalty(new BigDecimal("120.00"))
                .totalAmount(new BigDecimal("620.00"))
                .build());
        testBookings.add(b1);

        // Booking 2 (Station 1, Charger 1B): COMPLETED session, Energy: ₹300, Penalty: ₹0
        Instant baseTime2 = baseTime.plus(Duration.ofHours(3));
        Booking b2 = bookingRepository.save(Booking.builder()
                .driver(driver)
                .vehicle(vehicle)
                .charger(charger1B)
                .startTime(baseTime2)
                .endTime(baseTime2.plus(Duration.ofMinutes(30)))
                .sessionStartTime(baseTime2)
                .chargeCompletedAt(baseTime2.plus(Duration.ofMinutes(25)))
                .plugOutAt(baseTime2.plus(Duration.ofMinutes(26)))
                .status(BookingStatus.COMPLETED)
                .baseAmount(new BigDecimal("300.00"))
                .overstayPenalty(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("300.00"))
                .build());
        testBookings.add(b2);

        // Booking 3 (Station 2, Charger 2A): ACTIVE session, Energy: ₹450, Penalty: ₹50
        Instant baseTime3 = Instant.now().minus(Duration.ofMinutes(20));
        Booking b3 = bookingRepository.save(Booking.builder()
                .driver(driver)
                .vehicle(vehicle)
                .charger(charger2A)
                .startTime(baseTime3)
                .endTime(baseTime3.plus(Duration.ofMinutes(45)))
                .sessionStartTime(baseTime3)
                .status(BookingStatus.ACTIVE)
                .baseAmount(new BigDecimal("450.00"))
                .overstayPenalty(new BigDecimal("50.00"))
                .totalAmount(new BigDecimal("500.00"))
                .build());
        testBookings.add(b3);

        // Fetch Analytics for Owner 1
        OwnerAnalyticsResponse response = ownerAnalyticsService.getOwnerAnalytics(owner1.getId());

        assertThat(response).isNotNull();
        assertThat(response.overall()).isNotNull();

        // Overall assertions
        assertThat(response.overall().totalStations()).isEqualTo(2);
        assertThat(response.overall().totalChargers()).isEqualTo(3); // 2 in st1, 1 in st2
        assertThat(response.overall().totalCompletedSessions()).isEqualTo(2);
        assertThat(response.overall().totalActiveSessions()).isEqualTo(1);

        // Total Energy Revenue: 500 + 300 + 450 = 1250.00
        assertThat(response.overall().totalEnergyRevenue()).isEqualByComparingTo("1250.00");
        // Total Penalty Revenue: 120 + 0 + 50 = 170.00
        assertThat(response.overall().totalPenaltyRevenue()).isEqualByComparingTo("170.00");
        // Total Combined Revenue: 1420.00
        assertThat(response.overall().totalRevenue()).isEqualByComparingTo("1420.00");

        // Hourly Activity array (24 hours)
        assertThat(response.overall().hourlyActivity()).hasSize(24);
        assertThat(response.overall().busiestHour()).isNotNull();

        // Per-Station Breakdown assertions
        assertThat(response.stations()).hasSize(2);

        var st1Analytics = response.stations().stream()
                .filter(s -> s.stationId().equals(station1.getId()))
                .findFirst().orElseThrow();
        assertThat(st1Analytics.totalChargers()).isEqualTo(2);
        assertThat(st1Analytics.completedSessions()).isEqualTo(2);
        assertThat(st1Analytics.activeSessions()).isEqualTo(0);
        assertThat(st1Analytics.energyRevenue()).isEqualByComparingTo("800.00"); // 500 + 300
        assertThat(st1Analytics.penaltyRevenue()).isEqualByComparingTo("120.00");
        assertThat(st1Analytics.totalRevenue()).isEqualByComparingTo("920.00");

        var st2Analytics = response.stations().stream()
                .filter(s -> s.stationId().equals(station2.getId()))
                .findFirst().orElseThrow();
        assertThat(st2Analytics.totalChargers()).isEqualTo(1);
        assertThat(st2Analytics.completedSessions()).isEqualTo(0);
        assertThat(st2Analytics.activeSessions()).isEqualTo(1);
        assertThat(st2Analytics.energyRevenue()).isEqualByComparingTo("450.00");
        assertThat(st2Analytics.penaltyRevenue()).isEqualByComparingTo("50.00");
        assertThat(st2Analytics.totalRevenue()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("Multi-Tenant Isolation: Owner A cannot see Owner B's revenue, penalties, or stations")
    void testMultiTenantOwnerIsolation() {
        // Create booking for Owner 2
        Instant now = Instant.now().minus(Duration.ofHours(1));
        Booking bOwner2 = bookingRepository.save(Booking.builder()
                .driver(driver)
                .vehicle(vehicle)
                .charger(chargerOwner2)
                .startTime(now)
                .endTime(now.plus(Duration.ofMinutes(45)))
                .status(BookingStatus.COMPLETED)
                .baseAmount(new BigDecimal("999.00"))
                .overstayPenalty(new BigDecimal("200.00"))
                .totalAmount(new BigDecimal("1199.00"))
                .build());
        testBookings.add(bOwner2);

        // Fetch Analytics for Owner 1 (should be 0 because owner1 has no bookings in this test)
        OwnerAnalyticsResponse response1 = ownerAnalyticsService.getOwnerAnalytics(owner1.getId());
        assertThat(response1.overall().totalCompletedSessions()).isEqualTo(0);
        assertThat(response1.overall().totalEnergyRevenue()).isEqualByComparingTo("0.00");
        assertThat(response1.overall().totalPenaltyRevenue()).isEqualByComparingTo("0.00");
        assertThat(response1.stations().stream().noneMatch(s -> s.stationId().equals(stationOwner2.getId()))).isTrue();

        // Fetch Analytics for Owner 2 (should see exactly 1 completed session, ₹999 energy, ₹200 penalty)
        OwnerAnalyticsResponse response2 = ownerAnalyticsService.getOwnerAnalytics(owner2.getId());
        assertThat(response2.overall().totalCompletedSessions()).isEqualTo(1);
        assertThat(response2.overall().totalEnergyRevenue()).isEqualByComparingTo("999.00");
        assertThat(response2.overall().totalPenaltyRevenue()).isEqualByComparingTo("200.00");
        assertThat(response2.overall().totalRevenue()).isEqualByComparingTo("1199.00");
        assertThat(response2.stations()).hasSize(1);
        assertThat(response2.stations().get(0).stationId()).isEqualTo(stationOwner2.getId());
    }
}
