package com.voltgrid.voltgrid.bootstrap;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Idempotently seeds 12+ realistic EV charging hubs across Vadodara, Ahmedabad,
 * Pune, and Mumbai with varying kW power, connector types, and tariffs.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class NetworkDataSeeder implements ApplicationRunner {

    private final StationRepository stationRepository;
    private final ChargerRepository chargerRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    record ChargerSeed(String name, BigDecimal powerKw, ConnectorType connectorType, BigDecimal pricePerKwh) {}
    record StationSeed(String name, String address, String city, BigDecimal lat, BigDecimal lng, List<ChargerSeed> chargers) {}

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("NetworkDataSeeder: Checking EV charging network across key Indian EV corridors...");

        // Ensure a dedicated infra operator owner account exists
        String ownerEmail = "owner.network@voltgrid.com";
        User networkOwner = userRepository.findByEmail(ownerEmail).orElseGet(() ->
                userRepository.save(User.builder()
                        .name("VoltGrid Infra Operator")
                        .email(ownerEmail)
                        .passwordHash(passwordEncoder.encode("Password123!"))
                        .role(Role.OWNER)
                        .enabled(true)
                        .build())
        );

        List<StationSeed> seeds = List.of(
                // 1. Vadodara - Alkapuri
                new StationSeed(
                        "Alkapuri Supercharge Hub",
                        "RC Dutt Road, Near Railway Station",
                        "Vadodara",
                        new BigDecimal("22.307200"),
                        new BigDecimal("73.181200"),
                        List.of(
                                new ChargerSeed("Bay 1 - DC HyperFast", new BigDecimal("120.00"), ConnectorType.CCS2, new BigDecimal("19.50")),
                                new ChargerSeed("Bay 2 - DC Rapid", new BigDecimal("60.00"), ConnectorType.CCS2, new BigDecimal("17.50")),
                                new ChargerSeed("Bay 3 - AC Destination", new BigDecimal("22.00"), ConnectorType.TYPE2, new BigDecimal("12.00"))
                        )
                ),
                // 2. Vadodara - Sayajigunj
                new StationSeed(
                        "Sayajigunj Express Bay",
                        "Station Road, Sayajigunj",
                        "Vadodara",
                        new BigDecimal("22.312500"),
                        new BigDecimal("73.187800"),
                        List.of(
                                new ChargerSeed("Sayaji Ultra 1", new BigDecimal("150.00"), ConnectorType.CCS2, new BigDecimal("21.00")),
                                new ChargerSeed("Sayaji Chademo 2", new BigDecimal("50.00"), ConnectorType.CHADEMO, new BigDecimal("16.00"))
                        )
                ),
                // 3. Vadodara - Fatehgunj
                new StationSeed(
                        "Fatehgunj Metro Point",
                        "Main Highway Circle, Fatehgunj",
                        "Vadodara",
                        new BigDecimal("22.324200"),
                        new BigDecimal("73.190100"),
                        List.of(
                                new ChargerSeed("Metro Rapid 1", new BigDecimal("100.00"), ConnectorType.CCS2, new BigDecimal("18.50")),
                                new ChargerSeed("Metro Standard 2", new BigDecimal("22.00"), ConnectorType.TYPE2, new BigDecimal("11.50"))
                        )
                ),
                // 4. Vadodara - Waghodia Road
                new StationSeed(
                        "Waghodia Highway Superhub",
                        "Waghodia Road, Near Ring Road Junction",
                        "Vadodara",
                        new BigDecimal("22.298500"),
                        new BigDecimal("73.225600"),
                        List.of(
                                new ChargerSeed("Waghodia Fast 1", new BigDecimal("60.00"), ConnectorType.CCS2, new BigDecimal("16.50")),
                                new ChargerSeed("Waghodia AC 2", new BigDecimal("22.00"), ConnectorType.TYPE2, new BigDecimal("12.00"))
                        )
                ),
                // 5. Vadodara - Gotri
                new StationSeed(
                        "Gotri Power Hub",
                        "Gotri-Sevasi Road, Gotri",
                        "Vadodara",
                        new BigDecimal("22.318900"),
                        new BigDecimal("73.149200"),
                        List.of(
                                new ChargerSeed("Gotri Ultra 1", new BigDecimal("180.00"), ConnectorType.CCS2, new BigDecimal("22.50")),
                                new ChargerSeed("Gotri Fast 2", new BigDecimal("60.00"), ConnectorType.CCS2, new BigDecimal("17.00"))
                        )
                ),
                // 6. Ahmedabad - SG Highway
                new StationSeed(
                        "SG Highway Supercharge Plaza",
                        "SG Highway, Near Iscon Cross Road",
                        "Ahmedabad",
                        new BigDecimal("23.033800"),
                        new BigDecimal("72.507400"),
                        List.of(
                                new ChargerSeed("SG Megawatt 1", new BigDecimal("240.00"), ConnectorType.CCS2, new BigDecimal("24.00")),
                                new ChargerSeed("SG Fast 2", new BigDecimal("120.00"), ConnectorType.CCS2, new BigDecimal("19.50")),
                                new ChargerSeed("SG AC 3", new BigDecimal("22.00"), ConnectorType.TYPE2, new BigDecimal("13.00"))
                        )
                ),
                // 7. Ahmedabad - Vastrapur
                new StationSeed(
                        "Vastrapur Lake EV Hub",
                        "AlphaOne Mall Ring Road, Vastrapur",
                        "Ahmedabad",
                        new BigDecimal("23.037200"),
                        new BigDecimal("72.529800"),
                        List.of(
                                new ChargerSeed("Vastrapur Rapid 1", new BigDecimal("80.00"), ConnectorType.CCS2, new BigDecimal("18.00")),
                                new ChargerSeed("Vastrapur Chademo 2", new BigDecimal("50.00"), ConnectorType.CHADEMO, new BigDecimal("15.50"))
                        )
                ),
                // 8. Ahmedabad - Sabarmati Riverfront
                new StationSeed(
                        "Sabarmati Riverfront Point",
                        "West Riverfront Promenade, Usmanpura",
                        "Ahmedabad",
                        new BigDecimal("23.045600"),
                        new BigDecimal("72.576500"),
                        List.of(
                                new ChargerSeed("Riverfront DC 1", new BigDecimal("100.00"), ConnectorType.CCS2, new BigDecimal("19.00")),
                                new ChargerSeed("Riverfront AC 2", new BigDecimal("22.00"), ConnectorType.TYPE2, new BigDecimal("12.50"))
                        )
                ),
                // 9. Pune - Hinjewadi
                new StationSeed(
                        "Hinjewadi Tech Park Superhub",
                        "Phase 1, Rajiv Gandhi Infotech Park",
                        "Pune",
                        new BigDecimal("18.591200"),
                        new BigDecimal("73.738900"),
                        List.of(
                                new ChargerSeed("Tech DC 1", new BigDecimal("150.00"), ConnectorType.CCS2, new BigDecimal("21.00")),
                                new ChargerSeed("Tech DC 2", new BigDecimal("120.00"), ConnectorType.CCS2, new BigDecimal("19.50")),
                                new ChargerSeed("Tech AC 3", new BigDecimal("22.00"), ConnectorType.TYPE2, new BigDecimal("12.00"))
                        )
                ),
                // 10. Pune - Koregaon Park
                new StationSeed(
                        "Koregaon Park EV Lounge",
                        "North Main Road, Koregaon Park",
                        "Pune",
                        new BigDecimal("18.536200"),
                        new BigDecimal("73.894000"),
                        List.of(
                                new ChargerSeed("Plaza DC 1", new BigDecimal("120.00"), ConnectorType.CCS2, new BigDecimal("20.00")),
                                new ChargerSeed("Plaza Chademo 2", new BigDecimal("50.00"), ConnectorType.CHADEMO, new BigDecimal("16.00"))
                        )
                ),
                // 11. Pune - Baner
                new StationSeed(
                        "Baner Highway Oasis",
                        "Mumbai-Bengaluru Highway, Baner",
                        "Pune",
                        new BigDecimal("18.559000"),
                        new BigDecimal("73.786800"),
                        List.of(
                                new ChargerSeed("Highway Ultra 1", new BigDecimal("200.00"), ConnectorType.CCS2, new BigDecimal("23.00")),
                                new ChargerSeed("Highway DC 2", new BigDecimal("60.00"), ConnectorType.CCS2, new BigDecimal("17.50"))
                        )
                ),
                // 12. Mumbai - BKC
                new StationSeed(
                        "Bandra-Kurla Complex (BKC) EV Hub",
                        "G Block, BKC, Bandra East",
                        "Mumbai",
                        new BigDecimal("19.065700"),
                        new BigDecimal("72.868700"),
                        List.of(
                                new ChargerSeed("BKC Hyper 1", new BigDecimal("180.00"), ConnectorType.CCS2, new BigDecimal("25.00")),
                                new ChargerSeed("BKC Rapid 2", new BigDecimal("120.00"), ConnectorType.CCS2, new BigDecimal("21.00")),
                                new ChargerSeed("BKC Executive AC 3", new BigDecimal("22.00"), ConnectorType.TYPE2, new BigDecimal("14.00"))
                        )
                ),
                // 13. Mumbai - Navi Mumbai
                new StationSeed(
                        "Navi Mumbai Expressway Oasis",
                        "Palm Beach Road, Vashi",
                        "Mumbai",
                        new BigDecimal("19.033000"),
                        new BigDecimal("73.029700"),
                        List.of(
                                new ChargerSeed("Expressway DC 1", new BigDecimal("150.00"), ConnectorType.CCS2, new BigDecimal("22.00")),
                                new ChargerSeed("Expressway Chademo 2", new BigDecimal("50.00"), ConnectorType.CHADEMO, new BigDecimal("16.50"))
                        )
                ),
                // 14. Bharuch - NH48 Highway Hub
                new StationSeed(
                        "Bharuch NH48 Supercharge Stop",
                        "NH 48 Highway Crossing, Near Narmada Bridge",
                        "Bharuch",
                        new BigDecimal("21.705100"),
                        new BigDecimal("72.995900"),
                        List.of(
                                new ChargerSeed("Bharuch Hyper DC 1", new BigDecimal("150.00"), ConnectorType.CCS2, new BigDecimal("21.00")),
                                new ChargerSeed("Bharuch Fast DC 2", new BigDecimal("60.00"), ConnectorType.CCS2, new BigDecimal("17.00"))
                        )
                ),
                // 15. Surat - Expressway Superhub
                new StationSeed(
                        "Surat Expressway Superhub",
                        "Varachha - Kamrej Corridor, Surat NH48",
                        "Surat",
                        new BigDecimal("21.170200"),
                        new BigDecimal("72.831100"),
                        List.of(
                                new ChargerSeed("Surat UltraBay 1", new BigDecimal("180.00"), ConnectorType.CCS2, new BigDecimal("22.00")),
                                new ChargerSeed("Surat Rapid 2", new BigDecimal("120.00"), ConnectorType.CCS2, new BigDecimal("19.50")),
                                new ChargerSeed("Surat Chademo 3", new BigDecimal("50.00"), ConnectorType.CHADEMO, new BigDecimal("16.00"))
                        )
                ),
                // 16. Vapi - Gateway Supercharge
                new StationSeed(
                        "Vapi Gateway Supercharge",
                        "NH 48 Industrial Bypass, Vapi",
                        "Vapi",
                        new BigDecimal("20.389300"),
                        new BigDecimal("72.910600"),
                        List.of(
                                new ChargerSeed("Vapi Hyper 1", new BigDecimal("150.00"), ConnectorType.CCS2, new BigDecimal("21.50")),
                                new ChargerSeed("Vapi Fast 2", new BigDecimal("60.00"), ConnectorType.CCS2, new BigDecimal("17.50"))
                        )
                ),
                // 17. Lonavala - Expressway Oasis
                new StationSeed(
                        "Lonavala Expressway Oasis",
                        "Old Mumbai-Pune Highway, Lonavala",
                        "Lonavala",
                        new BigDecimal("18.755700"),
                        new BigDecimal("73.409100"),
                        List.of(
                                new ChargerSeed("Lonavala Supercharge 1", new BigDecimal("120.00"), ConnectorType.CCS2, new BigDecimal("20.00")),
                                new ChargerSeed("Lonavala AC 2", new BigDecimal("22.00"), ConnectorType.TYPE2, new BigDecimal("13.00"))
                        )
                )
        );

        int newlySeeded = 0;
        for (StationSeed sSeed : seeds) {
            if (stationRepository.existsByName(sSeed.name())) {
                continue;
            }
            Station station = Station.builder()
                    .owner(networkOwner)
                    .name(sSeed.name())
                    .address(sSeed.address())
                    .city(sSeed.city())
                    .latitude(sSeed.lat())
                    .longitude(sSeed.lng())
                    .status(StationStatus.ACTIVE)
                    .build();
            Station savedStation = stationRepository.save(station);

            for (ChargerSeed cSeed : sSeed.chargers()) {
                Charger charger = Charger.builder()
                        .station(savedStation)
                        .name(cSeed.name())
                        .powerKw(cSeed.powerKw())
                        .connectorType(cSeed.connectorType())
                        .pricePerKwh(cSeed.pricePerKwh())
                        .status(ChargerStatus.AVAILABLE)
                        .build();
                chargerRepository.save(charger);
            }
            newlySeeded++;
        }

        log.info("NetworkDataSeeder: Successfully verified network. Seeded {} new corridor hubs.", newlySeeded);
    }
}
