package com.voltgrid.voltgrid;

import com.voltgrid.voltgrid.ai.dto.AiTripParseRequest;
import com.voltgrid.voltgrid.ai.dto.AiTripParseResponse;
import com.voltgrid.voltgrid.ai.service.AiTripAssistantService;
import com.voltgrid.voltgrid.charger.entity.Charger;
import com.voltgrid.voltgrid.charger.entity.ChargerStatus;
import com.voltgrid.voltgrid.charger.repository.ChargerRepository;
import com.voltgrid.voltgrid.entity.User;
import com.voltgrid.voltgrid.enums.ConnectorType;
import com.voltgrid.voltgrid.enums.Role;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class AiTripAssistantTest {

    @Autowired
    private AiTripAssistantService aiTripAssistantService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private ChargerRepository chargerRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User driver;
    private User owner;
    private Vehicle driverVehicle;
    private final List<Long> createdStationIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String testSuffix = UUID.randomUUID().toString().substring(0, 8);
        driver = userRepository.save(User.builder()
                .email("ai-driver-" + testSuffix + "@voltgrid.test")
                .passwordHash(passwordEncoder.encode("Test@1234"))
                .name("AI Test Driver")
                .role(Role.DRIVER)
                .enabled(true)
                .build());

        owner = userRepository.save(User.builder()
                .email("ai-owner-" + testSuffix + "@voltgrid.test")
                .passwordHash(passwordEncoder.encode("Test@1234"))
                .name("AI Test Owner")
                .role(Role.OWNER)
                .enabled(true)
                .build());

        // Long-range EV (75 kWh usable battery, 0.160 kWh/km, 150 kW DC, CCS2)
        driverVehicle = vehicleRepository.save(Vehicle.builder()
                .driver(driver)
                .model("Hyundai Ioniq 5 " + testSuffix)
                .usableBatteryKwh(new BigDecimal("75.00"))
                .baseConsumptionKwhPerKm(new BigDecimal("0.160"))
                .maxChargeKw(new BigDecimal("150.00"))
                .connectorType(ConnectorType.CCS2)
                .active(true)
                .build());

        // Setup Anand Hub station midway between Vadodara and Ahmedabad (~22.56, 72.95)
        Station anandHub = stationRepository.save(Station.builder()
                .owner(owner)
                .name("VoltGrid Supercharger Anand Test Hub " + testSuffix)
                .address("NH48 Expressway Exit, Anand")
                .city("Anand")
                .latitude(new BigDecimal("22.564500"))
                .longitude(new BigDecimal("72.953200"))
                .status(StationStatus.ACTIVE)
                .build());
        createdStationIds.add(anandHub.getId());

        chargerRepository.save(Charger.builder()
                .station(anandHub)
                .name("Anand DC Bay 1")
                .connectorType(ConnectorType.CCS2)
                .powerKw(new BigDecimal("120.00"))
                .pricePerKwh(new BigDecimal("18.00"))
                .status(ChargerStatus.AVAILABLE)
                .build());
    }

    @AfterEach
    void tearDown() {
        if (driverVehicle != null) {
            vehicleRepository.deleteById(driverVehicle.getId());
        }
        for (Long sId : createdStationIds) {
            chargerRepository.deleteAll(chargerRepository.findByStationIdOrderByIdAsc(sId));
            stationRepository.deleteById(sId);
        }
        if (driver != null) userRepository.deleteById(driver.getId());
        if (owner != null) userRepository.deleteById(owner.getId());
    }

    @Test
    @DisplayName("Grounded AI extracts structured trip parameters from highway driving prompt")
    void testExtractStructuredHighwayTripParameters() {
        String prompt = "I am in Vadodara with 22% battery, AC on, driving to Mumbai at 80 km/h";
        AiTripParseRequest request = new AiTripParseRequest(prompt, driverVehicle.getId(), false);

        AiTripParseResponse response = aiTripAssistantService.parseAndProcessTrip(driver.getId(), request);

        assertThat(response).isNotNull();
        assertThat(response.intent()).isEqualTo("HIGHWAY_TRIP");
        assertThat(response.originCity()).isEqualTo("Vadodara");
        assertThat(response.startLat()).isEqualByComparingTo("22.307200");
        assertThat(response.startLng()).isEqualByComparingTo("73.181200");
        assertThat(response.destinationCity()).isEqualTo("Mumbai");
        assertThat(response.destLat()).isEqualByComparingTo("19.076000");
        assertThat(response.destLng()).isEqualByComparingTo("72.877700");
        assertThat(response.currentBatteryPercent()).isEqualByComparingTo("22.00");
        assertThat(response.acOn()).isTrue();
        assertThat(response.speedKmh()).isEqualTo(80);
        assertThat(response.terrain()).isEqualTo("HIGHWAY");
        assertThat(response.summary()).contains("Vadodara ➔ Mumbai");
    }

    @Test
    @DisplayName("Local charger search intent detected when no destination is provided")
    void testExtractLocalChargerSearchParameters() {
        String prompt = "Find chargers in Vadodara with 15% battery and AC off";
        AiTripParseRequest request = new AiTripParseRequest(prompt, driverVehicle.getId(), false);

        AiTripParseResponse response = aiTripAssistantService.parseAndProcessTrip(driver.getId(), request);

        assertThat(response).isNotNull();
        assertThat(response.intent()).isEqualTo("LOCAL_CHARGER_SEARCH");
        assertThat(response.originCity()).isEqualTo("Vadodara");
        assertThat(response.destinationCity()).isNull();
        assertThat(response.currentBatteryPercent()).isEqualByComparingTo("15.00");
        assertThat(response.acOn()).isFalse();
        assertThat(response.summary()).contains("Local Search at Vadodara");
    }

    @Test
    @DisplayName("Hilly terrain, speed, and battery boundaries clamped safely without exceptions")
    void testBoundaryAndTerrainExtraction() {
        String prompt = "From Pune heading to Mumbai hilly terrain at 145 km/h with 150% battery";
        AiTripParseRequest request = new AiTripParseRequest(prompt, driverVehicle.getId(), false);

        AiTripParseResponse response = aiTripAssistantService.parseAndProcessTrip(driver.getId(), request);

        assertThat(response).isNotNull();
        assertThat(response.intent()).isEqualTo("HIGHWAY_TRIP");
        assertThat(response.originCity()).isEqualTo("Pune");
        assertThat(response.destinationCity()).isEqualTo("Mumbai");
        // Battery clamped to 100 max
        assertThat(response.currentBatteryPercent()).isEqualByComparingTo("100.00");
        // Terrain parsed as HILLY
        assertThat(response.terrain()).isEqualTo("HILLY");
    }

    @Test
    @DisplayName("Auto-plan flag seamlessly executes Dijkstra TripPlannerService for reachable route")
    void testAutoPlanningIntegration() {
        // Vadodara to Ahmedabad (~110 km), starting with 60% battery in 75 kWh car (~45 kWh = ~280 km range) -> reachable!
        String prompt = "Drive from Vadodara to Ahmedabad with 60% battery, AC on, at 80 km/h";
        AiTripParseRequest request = new AiTripParseRequest(prompt, driverVehicle.getId(), true);

        AiTripParseResponse response = aiTripAssistantService.parseAndProcessTrip(driver.getId(), request);

        assertThat(response).isNotNull();
        assertThat(response.intent()).isEqualTo("HIGHWAY_TRIP");
        assertThat(response.originCity()).isEqualTo("Vadodara");
        assertThat(response.destinationCity()).isEqualTo("Ahmedabad");
        assertThat(response.plannedTrip()).isNotNull();
        assertThat(response.plannedTrip().totalDistanceKm()).isGreaterThan(90.0);
        assertThat(response.plannedTrip().summary()).isNotEmpty();
    }
}
