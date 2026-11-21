package com.voltgrid.voltgrid.recommendation.service;

import com.voltgrid.voltgrid.booking.entity.Booking;
import com.voltgrid.voltgrid.booking.entity.BookingStatus;
import com.voltgrid.voltgrid.booking.repository.BookingRepository;
import com.voltgrid.voltgrid.charger.entity.Charger;
import com.voltgrid.voltgrid.charger.entity.ChargerStatus;
import com.voltgrid.voltgrid.charger.repository.ChargerRepository;
import com.voltgrid.voltgrid.exception.BadRequestException;
import com.voltgrid.voltgrid.exception.ResourceNotFoundException;
import com.voltgrid.voltgrid.range.calculator.RangeCalculator;
import com.voltgrid.voltgrid.recommendation.dto.ChargerRecommendation;
import com.voltgrid.voltgrid.recommendation.dto.RecommendationRequest;
import com.voltgrid.voltgrid.recommendation.dto.RecommendationResponse;
import com.voltgrid.voltgrid.recommendation.scoring.ChargerScorer;
import com.voltgrid.voltgrid.recommendation.scoring.ChargerScorer.ChargerOption;
import com.voltgrid.voltgrid.recommendation.scoring.ChargerScorer.DriverState;
import com.voltgrid.voltgrid.recommendation.scoring.ChargerScorer.Eligibility;
import com.voltgrid.voltgrid.recommendation.scoring.ChargerScorer.Score;
import com.voltgrid.voltgrid.station.entity.Station;
import com.voltgrid.voltgrid.station.entity.StationStatus;
import com.voltgrid.voltgrid.station.repository.StationRepository;
import com.voltgrid.voltgrid.util.GeoUtils;
import com.voltgrid.voltgrid.util.TopKSelector;
import com.voltgrid.voltgrid.vehicle.entity.Vehicle;
import com.voltgrid.voltgrid.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final double MAX_SEARCH_RADIUS_KM = 200.0;
    private static final double DEFAULT_TARGET_PERCENT = 80.0;
    private static final int DEFAULT_TOP_K = 3;

    private static final String MSG_OK = "Chargers ranked by total time (drive + wait + charge)";
    private static final String MSG_NO_CHARGER = "No available charger found within your reachable range";

    private final VehicleRepository vehicleRepository;
    private final StationRepository stationRepository;
    private final ChargerRepository chargerRepository;
    private final BookingRepository bookingRepository;

    private record StationDistance(Station station, double distanceKm) {
    }

    public record WaitTimeInfo(double waitMinutes, int queuedVehicles) {
    }

    @Transactional(readOnly = true)
    public RecommendationResponse recommend(Long driverId, RecommendationRequest request) {

        // Same ownership check as every vehicle API: someone else's vehicle is "not found"
        Vehicle vehicle = vehicleRepository
                .findByIdAndDriverIdAndActiveTrue(request.vehicleId(), driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));

        double batteryPercent = request.batteryPercent().doubleValue();
        double targetPercent = request.targetPercent() == null
                ? DEFAULT_TARGET_PERCENT : request.targetPercent().doubleValue();
        if (targetPercent <= batteryPercent) {
            throw new BadRequestException("targetPercent must be greater than batteryPercent");
        }
        int topK = request.topK() == null ? DEFAULT_TOP_K : request.topK();
        double speedKmph = request.speedKmph() == null
                ? RangeCalculator.DEFAULT_SPEED_KMPH : request.speedKmph();

        // Step 11: how far can this car safely go?
        RangeCalculator.Result range = RangeCalculator.calculate(
                vehicle.getUsableBatteryKwh().doubleValue(),
                vehicle.getBaseConsumptionKwhPerKm().doubleValue(),
                batteryPercent,
                request.acOn(),
                speedKmph);

        double rangeKm = range.rangeKm();

        if (rangeKm <= 0) {
            return response(vehicle, rangeKm, 0, 0, 0, List.of(),
                    "Battery is at or below the " + (int) RangeCalculator.RESERVE_PERCENT
                            + "% safety reserve, so no charger is considered reachable");
        }

        // Road distance must stay within range, and road = straight line x factor
        double searchRadiusKm = Math.min(rangeKm / ChargerScorer.ROAD_DISTANCE_FACTOR, MAX_SEARCH_RADIUS_KM);

        // Step 10: nearby ACTIVE stations
        List<StationDistance> stations = findStationsWithin(
                request.lat().doubleValue(), request.lng().doubleValue(), searchRadiusKm);
        if (stations.isEmpty()) {
            return response(vehicle, rangeKm, searchRadiusKm, 0, 0, List.of(), MSG_NO_CHARGER);
        }

        Map<Long, Double> distanceByStation = new HashMap<>();
        stations.forEach(s -> distanceByStation.put(s.station().getId(), s.distanceKm()));

        // One query for all candidate chargers (no N+1)
        List<Charger> chargers = chargerRepository.findByStationIdInAndStatusNot(
                List.copyOf(distanceByStation.keySet()), ChargerStatus.OUT_OF_SERVICE);
        if (chargers.isEmpty()) {
            return response(vehicle, rangeKm, searchRadiusKm, 0, 0, List.of(), MSG_NO_CHARGER);
        }

        // Step 12: score every charger with real dynamic queue wait times
        DriverState driverState = new DriverState(
                vehicle.getConnectorType(),
                vehicle.getMaxChargeKw().doubleValue(),
                vehicle.getUsableBatteryKwh().doubleValue(),
                batteryPercent,
                targetPercent,
                range.effectiveConsumptionKwhPerKm(),
                rangeKm);

        Instant now = Instant.now();
        List<Long> chargerIds = chargers.stream().map(Charger::getId).toList();
        List<Booking> activeAndConfirmedBookings = bookingRepository.findRelevantBookingsForWaitTime(chargerIds, now);
        Map<Long, List<Booking>> bookingsByCharger = activeAndConfirmedBookings.stream()
                .collect(Collectors.groupingBy(b -> b.getCharger().getId()));

        Map<Long, Charger> chargerById = new HashMap<>();
        List<Score> eligible = new ArrayList<>();
        int incompatible = 0;

        for (Charger charger : chargers) {
            double straightDist = distanceByStation.get(charger.getStation().getId());
            double roadDist = straightDist * ChargerScorer.ROAD_DISTANCE_FACTOR;
            double driveMinutes = (roadDist / speedKmph) * 60.0;
            Instant arrivalInstant = now.plusSeconds((long) (driveMinutes * 60.0));

            List<Booking> chargerBookings = bookingsByCharger.getOrDefault(charger.getId(), List.of());
            WaitTimeInfo waitInfo = calculateWaitTime(chargerBookings, now, arrivalInstant);

            ChargerOption option = new ChargerOption(
                    charger.getId(),
                    charger.getConnectorType(),
                    charger.getPowerKw().doubleValue(),
                    straightDist,
                    waitInfo.queuedVehicles(),
                    waitInfo.waitMinutes()
            );

            Score score = ChargerScorer.score(driverState, option);

            if (score.isEligible()) {
                eligible.add(score);
                chargerById.put(charger.getId(), charger);
            } else if (score.eligibility() == Eligibility.CONNECTOR_MISMATCH) {
                incompatible++;
            }
        }

        if (eligible.isEmpty()) {
            String message = incompatible > 0
                    ? "Chargers were found nearby, but none match your vehicle's connector type ("
                    + vehicle.getConnectorType() + ")"
                    : MSG_NO_CHARGER;
            return response(vehicle, rangeKm, searchRadiusKm, chargers.size(), incompatible, List.of(), message);
        }

        // Top-K with a bounded max-heap
        List<Score> best = TopKSelector.topK(eligible, topK, ChargerScorer.BY_TOTAL_TIME);

        List<ChargerRecommendation> recommendations = IntStream.range(0, best.size())
                .mapToObj(i -> toRecommendation(i + 1, best.get(i), chargerById.get(best.get(i).chargerId())))
                .toList();

        return response(vehicle, rangeKm, searchRadiusKm, chargers.size(), incompatible, recommendations, MSG_OK);
    }

    // ---------- helpers ----------

    private List<StationDistance> findStationsWithin(double lat, double lng, double radiusKm) {
        GeoUtils.BoundingBox box = GeoUtils.boundingBox(lat, lng, radiusKm);

        return stationRepository.findInBox(
                        StationStatus.ACTIVE,
                        BigDecimal.valueOf(box.minLat()),
                        BigDecimal.valueOf(box.maxLat()),
                        BigDecimal.valueOf(box.minLng()),
                        BigDecimal.valueOf(box.maxLng())).stream()
                .map(s -> new StationDistance(s, GeoUtils.distanceKm(
                        lat, lng, s.getLatitude().doubleValue(), s.getLongitude().doubleValue())))
                .filter(sd -> sd.distanceKm() <= radiusKm)
                .sorted(Comparator.comparingDouble(StationDistance::distanceKm))
                .toList();
    }

    private ChargerRecommendation toRecommendation(int rank, Score score, Charger charger) {
        Station station = charger.getStation();

        // Indicative only: energy to add x price per kWh (ignores charging losses)
        BigDecimal estimatedCost = BigDecimal.valueOf(score.energyToChargeKwh())
                .multiply(charger.getPricePerKwh())
                .setScale(2, RoundingMode.HALF_UP);

        return new ChargerRecommendation(
                rank,
                charger.getId(),
                charger.getName(),
                charger.getConnectorType(),
                charger.getPowerKw(),
                round(score.effectivePowerKw(), 2),
                charger.getPricePerKwh(),
                station.getId(),
                station.getName(),
                station.getAddress(),
                station.getCity(),
                station.getLatitude(),
                station.getLongitude(),
                round(score.roadDistanceKm(), 2),
                round(score.driveMinutes(), 2),
                round(score.waitMinutes(), 2),
                round(score.chargeMinutes(), 2),
                round(score.totalMinutes(), 2),
                round(score.energyToChargeKwh(), 2),
                estimatedCost
        );
    }

    private RecommendationResponse response(Vehicle vehicle,
                                            double rangeKm,
                                            double searchRadiusKm,
                                            int chargersEvaluated,
                                            int incompatibleChargers,
                                            List<ChargerRecommendation> recommendations,
                                            String message) {
        return new RecommendationResponse(
                vehicle.getId(),
                vehicle.getModel(),
                round(rangeKm, 2),
                round(searchRadiusKm, 2),
                chargersEvaluated,
                incompatibleChargers,
                message,
                recommendations
        );
    }

    private WaitTimeInfo calculateWaitTime(List<Booking> bookings, Instant now, Instant arrivalInstant) {
        if (bookings == null || bookings.isEmpty()) {
            return new WaitTimeInfo(0.0, 0);
        }

        Instant busyUntil = now;
        int queuedCount = 0;

        // Check for any currently ACTIVE session on this charger
        Optional<Booking> activeOpt = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.ACTIVE)
                .findFirst();

        if (activeOpt.isPresent()) {
            Booking active = activeOpt.get();
            if (active.getChargeCompletedAt() != null) {
                // Charging has completed, vehicle is in grace period or pending plug-out
                Instant graceEnd = active.getChargeCompletedAt().plusSeconds(300);
                busyUntil = graceEnd.isAfter(now) ? graceEnd : now;
            } else {
                // Charging active, busy until scheduled slot end
                busyUntil = active.getEndTime().isAfter(now) ? active.getEndTime() : now;
            }
            if (busyUntil.isAfter(arrivalInstant)) {
                queuedCount++;
            }
        }

        // Check CONFIRMED bookings starting within driver arrival window or chained back-to-back
        List<Booking> confirmed = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                .sorted(Comparator.comparing(Booking::getStartTime))
                .toList();

        for (Booking b : confirmed) {
            Instant start = b.getStartTime();
            Instant end = b.getEndTime();

            // Extends busy block if back-to-back with previous session or starts before driver arrives
            if (!start.isAfter(busyUntil) || start.isBefore(arrivalInstant)) {
                busyUntil = end.isAfter(busyUntil) ? end : busyUntil;
                if (busyUntil.isAfter(arrivalInstant)) {
                    queuedCount++;
                }
            }
        }

        if (busyUntil.isAfter(arrivalInstant)) {
            long waitSeconds = Duration.between(arrivalInstant, busyUntil).getSeconds();
            double waitMinutes = Math.max(0.0, waitSeconds / 60.0);
            return new WaitTimeInfo(waitMinutes, Math.max(1, queuedCount));
        }

        return new WaitTimeInfo(0.0, 0);
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}