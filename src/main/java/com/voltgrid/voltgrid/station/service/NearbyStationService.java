package com.voltgrid.voltgrid.station.service;

import com.voltgrid.voltgrid.charger.entity.Charger;
import com.voltgrid.voltgrid.charger.entity.ChargerStatus;
import com.voltgrid.voltgrid.charger.repository.ChargerRepository;
import com.voltgrid.voltgrid.enums.ConnectorType;
import com.voltgrid.voltgrid.exception.BadRequestException;
import com.voltgrid.voltgrid.station.dto.NearbyStationResponse;
import com.voltgrid.voltgrid.station.entity.Station;
import com.voltgrid.voltgrid.station.entity.StationStatus;
import com.voltgrid.voltgrid.station.repository.StationRepository;
import com.voltgrid.voltgrid.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NearbyStationService {

    private static final double MAX_RADIUS_KM = 200.0;
    private static final int MAX_LIMIT = 50;

    private final StationRepository stationRepository;
    private final ChargerRepository chargerRepository;

    private record Candidate(Station station, double distanceKm) {
    }

    @Transactional(readOnly = true)
    public List<NearbyStationResponse> findNearby(double lat,
                                                  double lng,
                                                  double radiusKm,
                                                  ConnectorType connectorType,
                                                  BigDecimal minPowerKw,
                                                  int limit) {
        validate(lat, lng, radiusKm, minPowerKw);
        int safeLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);

        // Stage 1: cheap DB filter using a bounding box
        GeoUtils.BoundingBox box = GeoUtils.boundingBox(lat, lng, radiusKm);
        List<Station> inBox = stationRepository.findInBox(
                StationStatus.ACTIVE,
                BigDecimal.valueOf(box.minLat()),
                BigDecimal.valueOf(box.maxLat()),
                BigDecimal.valueOf(box.minLng()),
                BigDecimal.valueOf(box.maxLng()));

        // Stage 2: exact distance, drop anything outside the circle, nearest first
        List<Candidate> candidates = inBox.stream()
                .map(s -> new Candidate(s, GeoUtils.distanceKm(
                        lat, lng, s.getLatitude().doubleValue(), s.getLongitude().doubleValue())))
                .filter(c -> c.distanceKm() <= radiusKm)
                .sorted(Comparator.comparingDouble(Candidate::distanceKm))
                .toList();

        if (candidates.isEmpty()) {
            return List.of();
        }

        // One query for the chargers of all candidate stations (no N+1)
        List<Long> stationIds = candidates.stream().map(c -> c.station().getId()).toList();
        Map<Long, List<Charger>> chargersByStation = chargerRepository
                .findByStationIdInAndStatusNot(stationIds, ChargerStatus.OUT_OF_SERVICE).stream()
                .collect(Collectors.groupingBy(c -> c.getStation().getId()));

        boolean hasChargerFilter = connectorType != null || minPowerKw != null;

        return candidates.stream()
                .map(c -> toResponse(c, chargersByStation, connectorType, minPowerKw))
                .filter(r -> !hasChargerFilter || r.matchingChargers() > 0)
                .limit(safeLimit)
                .toList();
    }

    private NearbyStationResponse toResponse(Candidate candidate,
                                             Map<Long, List<Charger>> chargersByStation,
                                             ConnectorType connectorType,
                                             BigDecimal minPowerKw) {
        Station s = candidate.station();

        List<Charger> matching = chargersByStation.getOrDefault(s.getId(), List.of()).stream()
                .filter(c -> connectorType == null || c.getConnectorType() == connectorType)
                .filter(c -> minPowerKw == null || c.getPowerKw().compareTo(minPowerKw) >= 0)
                .toList();

        Optional<BigDecimal> maxPower = matching.stream()
                .map(Charger::getPowerKw)
                .max(Comparator.naturalOrder());

        return new NearbyStationResponse(
                s.getId(),
                s.getName(),
                s.getAddress(),
                s.getCity(),
                s.getLatitude(),
                s.getLongitude(),
                Math.round(candidate.distanceKm() * 100.0) / 100.0,
                matching.size(),
                maxPower.orElse(null)
        );
    }

    private void validate(double lat, double lng, double radiusKm, BigDecimal minPowerKw) {
        if (!Double.isFinite(lat) || lat < -90 || lat > 90) {
            throw new BadRequestException("lat must be between -90 and 90");
        }
        if (!Double.isFinite(lng) || lng < -180 || lng > 180) {
            throw new BadRequestException("lng must be between -180 and 180");
        }
        if (!Double.isFinite(radiusKm) || radiusKm <= 0 || radiusKm > MAX_RADIUS_KM) {
            throw new BadRequestException("radiusKm must be greater than 0 and at most " + (int) MAX_RADIUS_KM);
        }
        if (minPowerKw != null && minPowerKw.signum() < 0) {
            throw new BadRequestException("minPowerKw cannot be negative");
        }
    }
}