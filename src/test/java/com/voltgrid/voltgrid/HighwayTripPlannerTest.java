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
import com.voltgrid.voltgrid.exception.UnreachableTripException;
import com.voltgrid.voltgrid.payment.repository.PaymentRepository;
import com.voltgrid.voltgrid.recommendation.dto.ChargerRecommendation;
import com.voltgrid.voltgrid.recommendation.dto.RecommendationRequest;
import com.voltgrid.voltgrid.recommendation.dto.RecommendationResponse;
import com.voltgrid.voltgrid.recommendation.service.RecommendationService;
import com.voltgrid.voltgrid.repository.UserRepository;
import com.voltgrid.voltgrid.station.entity.Station;
import com.voltgrid.voltgrid.station.entity.StationStatus;
import com.voltgrid.voltgrid.station.repository.StationRepository;
import com.voltgrid.voltgrid.trip.dto.TripPlanRequest;
import com.voltgrid.voltgrid.trip.dto.TripPlanResponse;
import com.voltgrid.voltgrid.trip.service.TripPlannerService;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
public class HighwayTripPlannerTest {

    @Autowired
    private TripPlannerService tripPlannerService;

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private ChargerRepository chargerRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testDriver;
    private User testOwner;
    private Vehicle testVehicle;
    private List<Station> createdStations = new ArrayList<>();
    private List<Charger> createdChargers = new ArrayList<>();
    private List<Booking> createdBookings = new ArrayList<>();
    private Charger chargerVadodara;

    @BeforeEach
    void setUp() {
        String uuid = UUID.randomUUID().toString().substring(0, 8);

        testDriver = userRepository.save(User.builder()
                .name("Trip Driver " + uuid)
                .email("tripdriver_" + uuid + "@test.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(Role.DRIVER)
                .enabled(true)
                .build());

        testOwner = userRepository.save(User.builder()
                .name("Trip Owner " + uuid)
                .email("tripowner_" + uuid + "@test.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(Role.OWNER)
                .enabled(true)
                .build());

        // EV with 75 kWh battery, 0.16 kWh/km consumption, 150 kW max charging power
        testVehicle = vehicleRepository.save(Vehicle.builder()
                .driver(testDriver)
                .model("Tesla Model Y Long Range " + uuid)
                .usableBatteryKwh(new BigDecimal("75.00"))
                .baseConsumptionKwhPerKm(new BigDecimal("0.160"))
                .maxChargeKw(new BigDecimal("150.00"))
                .connectorType(ConnectorType.CCS2)
                .active(true)
                .build());

        // Highway corridor: Vadodara -> Bharuch -> Surat -> Vapi -> Manor -> Mumbai BKC
        Station sVadodara = createStationWithCharger("Test Hub Vadodara " + uuid,
                "Alkapuri, Vadodara", "Vadodara",
                new BigDecimal("22.307200"), new BigDecimal("73.181200"),
                "Vadodara Bay 1 " + uuid, new BigDecimal("120.00"), ConnectorType.CCS2, new BigDecimal("18.00"));
        chargerVadodara = chargerRepository.findByStationIdInAndStatusNot(List.of(sVadodara.getId()), ChargerStatus.OUT_OF_SERVICE).get(0);

        createStationWithCharger("Test Hub Bharuch " + uuid,
                "NH48, Bharuch", "Bharuch",
                new BigDecimal("21.705100"), new BigDecimal("72.995900"),
                "Bharuch Bay 1 " + uuid, new BigDecimal("120.00"), ConnectorType.CCS2, new BigDecimal("19.00"));

        createStationWithCharger("Test Hub Surat " + uuid,
                "Varachha, Surat", "Surat",
                new BigDecimal("21.170200"), new BigDecimal("72.831100"),
                "Surat Bay 1 " + uuid, new BigDecimal("150.00"), ConnectorType.CCS2, new BigDecimal("20.00"));

        createStationWithCharger("Test Hub Vapi " + uuid,
                "NH48, Vapi", "Vapi",
                new BigDecimal("20.389300"), new BigDecimal("72.910600"),
                "Vapi Bay 1 " + uuid, new BigDecimal("150.00"), ConnectorType.CCS2, new BigDecimal("20.00"));

        createStationWithCharger("Test Hub Manor " + uuid,
                "NH48, Manor", "Manor",
                new BigDecimal("19.721400"), new BigDecimal("72.868700"),
                "Manor Bay 1 " + uuid, new BigDecimal("150.00"), ConnectorType.CCS2, new BigDecimal("21.00"));

        createStationWithCharger("Test Hub Mumbai " + uuid,
                "BKC, Mumbai", "Mumbai",
                new BigDecimal("19.065700"), new BigDecimal("72.868700"),
                "Mumbai Bay 1 " + uuid, new BigDecimal("180.00"), ConnectorType.CCS2, new BigDecimal("22.00"));
    }

    @AfterEach
    void tearDown() {
        for (Booking b : createdBookings) {
            paymentRepository.deleteAll(paymentRepository.findByBookingId(b.getId()));
            bookingRepository.delete(b);
        }
        for (Charger c : createdChargers) {
            chargerRepository.delete(c);
        }
        for (Station s : createdStations) {
            stationRepository.delete(s);
        }
        if (testVehicle != null) {
            vehicleRepository.delete(testVehicle);
        }
        if (testDriver != null) {
            userRepository.delete(testDriver);
        }
        if (testOwner != null) {
            userRepository.delete(testOwner);
        }
    }

    private Station createStationWithCharger(String sName, String addr, String city,
                                             BigDecimal lat, BigDecimal lng,
                                             String cName, BigDecimal powerKw,
                                             ConnectorType connectorType, BigDecimal price) {
        Station st = stationRepository.save(Station.builder()
                .owner(testOwner)
                .name(sName)
                .address(addr)
                .city(city)
                .latitude(lat)
                .longitude(lng)
                .status(StationStatus.ACTIVE)
                .build());
        createdStations.add(st);

        Charger ch = chargerRepository.save(Charger.builder()
                .station(st)
                .name(cName)
                .powerKw(powerKw)
                .connectorType(connectorType)
                .pricePerKwh(price)
                .status(ChargerStatus.AVAILABLE)
                .build());
        createdChargers.add(ch);

        return st;
    }

    @Test
    @DisplayName("Direct Route: 0 stops when destination is within single-charge battery range")
    void testDirectRouteWhenWithinRange() {
        // Start in Vadodara, Destination 8 km away within Vadodara
        TripPlanRequest request = new TripPlanRequest(
                testVehicle.getId(),
                new BigDecimal("22.307200"),
                new BigDecimal("73.181200"),
                new BigDecimal("22.318900"),
                new BigDecimal("73.149200"),
                new BigDecimal("80.0"), // 80% battery
                false,
                60.0,
                "FLAT"
        );

        TripPlanResponse response = tripPlannerService.planTrip(testDriver.getId(), request);

        assertThat(response).isNotNull();
        assertThat(response.totalStops()).isEqualTo(0);
        assertThat(response.stops()).isEmpty();
        assertThat(response.legs()).hasSize(1);
        assertThat(response.totalChargeMinutes()).isEqualTo(0.0);
        assertThat(response.totalTripMinutes()).isEqualTo(response.totalDriveMinutes());
        assertThat(response.legs().get(0).endBatteryPercent()).isGreaterThan(70.0);
    }

    @Test
    @DisplayName("Multi-Stop Route: Successfully finds optimal charging stops when distance exceeds single-charge range")
    void testMultiStopRouteWhenDirectDistanceExceedsRange() {
        // Start in Vadodara (22.3072, 73.1812), Destination in Mumbai BKC (19.0657, 72.8687)
        // Road distance is ~410 km.
        // Current battery is only 30% (usable range is only ~100 km, cannot reach Mumbai directly)
        TripPlanRequest request = new TripPlanRequest(
                testVehicle.getId(),
                new BigDecimal("22.307200"),
                new BigDecimal("73.181200"),
                new BigDecimal("19.065700"),
                new BigDecimal("72.868700"),
                new BigDecimal("30.0"),
                false,
                80.0,
                "HIGHWAY"
        );

        TripPlanResponse response = tripPlannerService.planTrip(testDriver.getId(), request);

        assertThat(response).isNotNull();
        assertThat(response.totalDistanceKm()).isGreaterThan(350.0);
        assertThat(response.totalStops()).isGreaterThanOrEqualTo(2);
        assertThat(response.stops()).isNotEmpty();
        assertThat(response.legs()).isNotEmpty();

        // Every stop must respect the 5% safety reserve on arrival
        for (var stop : response.stops()) {
            assertThat(stop.arrivalBatteryPercent()).isGreaterThanOrEqualTo(5.0);
            assertThat(stop.targetBatteryPercent()).isGreaterThan(stop.arrivalBatteryPercent());
            assertThat(stop.chargeDurationMinutes()).isGreaterThan(0.0);
            assertThat(stop.estimatedCost()).isGreaterThan(BigDecimal.ZERO);
        }

        // Total time must equal driving + charging (within 0.2 min rounding tolerance)
        assertThat(response.totalTripMinutes())
                .isCloseTo(response.totalDriveMinutes() + response.totalChargeMinutes(), org.assertj.core.data.Offset.offset(0.2));
        assertThat(response.summary()).contains("Highway trip planned");
    }

    @Test
    @DisplayName("Unreachable Highway Gap: Throws clear UnreachableTripException when gap exceeds range")
    void testUnreachableHighwayGapReturnsClearError() {
        // Destination in remote Himalayas (Leh, Ladakh: 34.1526, 77.5771), > 1200 km away with no stations
        TripPlanRequest request = new TripPlanRequest(
                testVehicle.getId(),
                new BigDecimal("22.307200"),
                new BigDecimal("73.181200"),
                new BigDecimal("34.152600"),
                new BigDecimal("77.577100"),
                new BigDecimal("50.0"),
                false,
                80.0,
                "FLAT"
        );

        assertThatThrownBy(() -> tripPlannerService.planTrip(testDriver.getId(), request))
                .isInstanceOf(UnreachableTripException.class)
                .hasMessageContaining("unreachable");
    }

    @Test
    @DisplayName("Dynamic Queue Wait Time: Non-zero wait time calculated when charger has an ACTIVE booking")
    void testDynamicQueueWaitTimeInScorerAndRecommendation() {
        Instant now = Instant.now();

        // Create an ACTIVE booking on chargerVadodara ending 25 minutes from now
        Booking activeBooking = bookingRepository.save(Booking.builder()
                .driver(testDriver)
                .charger(chargerVadodara)
                .vehicle(testVehicle)
                .startTime(now.minus(15, ChronoUnit.MINUTES))
                .endTime(now.plus(25, ChronoUnit.MINUTES))
                .status(BookingStatus.ACTIVE)
                .sessionStartTime(now.minus(15, ChronoUnit.MINUTES))
                .baseAmount(new BigDecimal("500.00"))
                .overstayPenalty(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("500.00"))
                .build());
        createdBookings.add(activeBooking);

        // Driver is right next to Vadodara hub (0 drive time)
        RecommendationRequest recRequest = new RecommendationRequest(
                testVehicle.getId(),
                new BigDecimal("22.307200"),
                new BigDecimal("73.181200"),
                new BigDecimal("20.0"), // batteryPercent
                false, // acOn
                50, // speedKmph
                new BigDecimal("80.0"), // targetPercent
                10 // topK = 10 so our test charger is included
        );

        RecommendationResponse response = recommendationService.recommend(testDriver.getId(), recRequest);

        assertThat(response).isNotNull();
        assertThat(response.recommendations()).isNotEmpty();

        ChargerRecommendation vadodaraRec = response.recommendations().stream()
                .filter(r -> r.chargerId().equals(chargerVadodara.getId()))
                .findFirst()
                .orElse(null);

        assertThat(vadodaraRec).isNotNull();
        // Since active session ends in ~25 minutes and driver is 0 minutes away, wait time should be ~24-25 minutes!
        assertThat(vadodaraRec.waitMinutes()).isGreaterThan(20.0);
        assertThat(vadodaraRec.totalMinutes()).isGreaterThan(vadodaraRec.chargeMinutes() + vadodaraRec.driveMinutes());
    }
}
