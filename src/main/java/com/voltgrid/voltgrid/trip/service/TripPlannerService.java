package com.voltgrid.voltgrid.trip.service;

import com.voltgrid.voltgrid.charger.entity.Charger;
import com.voltgrid.voltgrid.charger.entity.ChargerStatus;
import com.voltgrid.voltgrid.charger.repository.ChargerRepository;
import com.voltgrid.voltgrid.exception.BadRequestException;
import com.voltgrid.voltgrid.exception.ResourceNotFoundException;
import com.voltgrid.voltgrid.exception.UnreachableTripException;
import com.voltgrid.voltgrid.range.calculator.RangeCalculator;
import com.voltgrid.voltgrid.recommendation.scoring.ChargerScorer;
import com.voltgrid.voltgrid.station.entity.Station;
import com.voltgrid.voltgrid.station.entity.StationStatus;
import com.voltgrid.voltgrid.station.repository.StationRepository;
import com.voltgrid.voltgrid.trip.dto.RouteCoordinate;
import com.voltgrid.voltgrid.trip.dto.TripLegDto;
import com.voltgrid.voltgrid.trip.dto.TripPlanRequest;
import com.voltgrid.voltgrid.trip.dto.TripPlanResponse;
import com.voltgrid.voltgrid.trip.dto.TripStopDto;
import com.voltgrid.voltgrid.util.GeoUtils;
import com.voltgrid.voltgrid.vehicle.entity.Vehicle;
import com.voltgrid.voltgrid.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripPlannerService {

    private final VehicleRepository vehicleRepository;
    private final StationRepository stationRepository;
    private final ChargerRepository chargerRepository;

    private record StationNode(
            int index,
            Station station,
            Charger bestCharger,
            double lat,
            double lng,
            double maxPowerKw
    ) {
    }

    private record DijkstraState(
            int nodeIndex,
            double batteryPercent,
            double totalTimeMinutes,
            double totalDriveMinutes,
            double totalChargeMinutes,
            double totalDistanceKm,
            BigDecimal totalCost,
            DijkstraState previous,
            TripStopDto stopInfo,
            double legDistanceKm,
            double legDriveMinutes,
            double legStartBattery,
            double legEndBattery
    ) implements Comparable<DijkstraState> {
        @Override
        public int compareTo(DijkstraState other) {
            return Double.compare(this.totalTimeMinutes, other.totalTimeMinutes);
        }
    }

    @Transactional(readOnly = true)
    public TripPlanResponse planTrip(Long driverId, TripPlanRequest request) {
        Vehicle vehicle = vehicleRepository
                .findByIdAndDriverIdAndActiveTrue(request.vehicleId(), driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));

        double currentBattery = request.currentBatteryPercent().doubleValue();
        if (currentBattery <= RangeCalculator.RESERVE_PERCENT) {
            throw new BadRequestException("Current battery level (" + currentBattery
                    + "%) is at or below the " + (int) RangeCalculator.RESERVE_PERCENT
                    + "% safety reserve. Cannot start trip.");
        }

        double startLat = request.startLat().doubleValue();
        double startLng = request.startLng().doubleValue();
        double destLat = request.destLat().doubleValue();
        double destLng = request.destLng().doubleValue();

        double directDistKm = GeoUtils.distanceKm(startLat, startLng, destLat, destLng);
        if (directDistKm < 0.2) {
            throw new BadRequestException("Start and destination coordinates are too close or identical.");
        }

        double speedKmh = (request.speedKmh() != null && request.speedKmh() > 0)
                ? request.speedKmh() : RangeCalculator.DEFAULT_SPEED_KMPH;
        boolean acOn = Boolean.TRUE.equals(request.acOn());
        double terrainMultiplier = getTerrainMultiplier(request.terrain());

        double usableBatteryKwh = vehicle.getUsableBatteryKwh().doubleValue();
        double baseConsumption = vehicle.getBaseConsumptionKwhPerKm().doubleValue();
        double maxChargeKw = vehicle.getMaxChargeKw().doubleValue();

        double acFactor = acOn ? RangeCalculator.AC_FACTOR : 1.0;
        double speedFactor = RangeCalculator.speedFactor(speedKmh);
        double effectiveConsumption = baseConsumption * acFactor * speedFactor * terrainMultiplier;

        // Query active stations and compatible chargers
        List<Station> activeStations = stationRepository.findByStatus(StationStatus.ACTIVE);
        List<Long> stationIds = activeStations.stream().map(Station::getId).toList();

        List<Charger> candidateChargers = stationIds.isEmpty() ? List.of()
                : chargerRepository.findByStationIdInAndStatusNot(stationIds, ChargerStatus.OUT_OF_SERVICE);

        // Filter chargers by vehicle connector compatibility and pick the best per station
        Map<Long, List<Charger>> chargersByStation = new HashMap<>();
        for (Charger c : candidateChargers) {
            if (c.getConnectorType() == vehicle.getConnectorType()) {
                chargersByStation.computeIfAbsent(c.getStation().getId(), k -> new ArrayList<>()).add(c);
            }
        }

        List<StationNode> stationNodes = new ArrayList<>();
        int stationIdx = 1;
        for (Station st : activeStations) {
            List<Charger> chargers = chargersByStation.get(st.getId());
            if (chargers != null && !chargers.isEmpty()) {
                Charger best = chargers.stream()
                        .max(Comparator.comparingDouble(c -> Math.min(c.getPowerKw().doubleValue(), maxChargeKw)))
                        .orElse(chargers.get(0));

                stationNodes.add(new StationNode(
                        stationIdx++,
                        st,
                        best,
                        st.getLatitude().doubleValue(),
                        st.getLongitude().doubleValue(),
                        best.getPowerKw().doubleValue()
                ));
            }
        }

        // Build Dijkstra state-space graph
        // Node 0 = Start
        // Node 1..N = Stations
        // Node N+1 = Destination
        int totalNodes = stationNodes.size() + 2;
        int destIndex = totalNodes - 1;

        // Discretize battery state space (0 to 100% in 5% steps -> 21 buckets)
        int numBuckets = 21;
        double[][] minTime = new double[totalNodes][numBuckets];
        for (int i = 0; i < totalNodes; i++) {
            for (int j = 0; j < numBuckets; j++) {
                minTime[i][j] = Double.POSITIVE_INFINITY;
            }
        }

        PriorityQueue<DijkstraState> pq = new PriorityQueue<>();

        DijkstraState initialState = new DijkstraState(
                0,
                currentBattery,
                0.0,
                0.0,
                0.0,
                0.0,
                BigDecimal.ZERO,
                null,
                null,
                0.0,
                0.0,
                currentBattery,
                currentBattery
        );

        int startBucket = toBatteryBucket(currentBattery);
        minTime[0][startBucket] = 0.0;
        pq.add(initialState);

        DijkstraState destState = null;

        while (!pq.isEmpty()) {
            DijkstraState curr = pq.poll();

            if (curr.nodeIndex == destIndex) {
                destState = curr;
                break;
            }

            int currBucket = toBatteryBucket(curr.batteryPercent);
            if (curr.totalTimeMinutes > minTime[curr.nodeIndex][currBucket]) {
                continue;
            }

            double fromLat = getNodeLat(curr.nodeIndex, startLat, destLat, stationNodes);
            double fromLng = getNodeLng(curr.nodeIndex, startLng, destLng, stationNodes);

            // Explore transitions to other nodes (stations or destination)
            for (int nextNode = 1; nextNode < totalNodes; nextNode++) {
                if (nextNode == curr.nodeIndex) {
                    continue;
                }

                double toLat = getNodeLat(nextNode, startLat, destLat, stationNodes);
                double toLng = getNodeLng(nextNode, startLng, destLng, stationNodes);

                double straightDistanceKm = GeoUtils.distanceKm(fromLat, fromLng, toLat, toLng);
                double roadDistanceKm = straightDistanceKm * ChargerScorer.ROAD_DISTANCE_FACTOR;

                double energyNeededKwh = roadDistanceKm * effectiveConsumption;
                double batteryConsumedPercent = (energyNeededKwh / usableBatteryKwh) * 100.0;
                double arrivalBattery = curr.batteryPercent - batteryConsumedPercent;

                // Driving edge is valid ONLY if remaining battery stays above 5% safety reserve
                if (arrivalBattery < RangeCalculator.RESERVE_PERCENT) {
                    continue;
                }

                double legDriveMinutes = (roadDistanceKm / speedKmh) * 60.0;

                if (nextNode == destIndex) {
                    // Reached destination: drive only, no charging
                    double newTotalTime = curr.totalTimeMinutes + legDriveMinutes;
                    int bIdx = toBatteryBucket(arrivalBattery);

                    if (newTotalTime < minTime[destIndex][bIdx]) {
                        minTime[destIndex][bIdx] = newTotalTime;
                        pq.add(new DijkstraState(
                                destIndex,
                                arrivalBattery,
                                newTotalTime,
                                curr.totalDriveMinutes + legDriveMinutes,
                                curr.totalChargeMinutes,
                                curr.totalDistanceKm + roadDistanceKm,
                                curr.totalCost,
                                curr,
                                null,
                                roadDistanceKm,
                                legDriveMinutes,
                                curr.batteryPercent,
                                arrivalBattery
                        ));
                    }
                } else {
                    // Reached intermediate station: recharge battery!
                    StationNode targetStation = stationNodes.get(nextNode - 1);
                    Charger bestCharger = targetStation.bestCharger();
                    double effectiveChargeKw = Math.min(bestCharger.getPowerKw().doubleValue(), maxChargeKw);

                    // Candidate recharge target battery levels
                    List<Double> candidateTargets = new ArrayList<>();
                    if (80.0 > arrivalBattery + 2.0) {
                        candidateTargets.add(80.0);
                    }
                    if (90.0 > arrivalBattery + 5.0) {
                        candidateTargets.add(90.0);
                    }

                    // Check if charging to just enough to reach destination with buffer is faster
                    double distToDest = GeoUtils.distanceKm(targetStation.lat(), targetStation.lng(), destLat, destLng)
                            * ChargerScorer.ROAD_DISTANCE_FACTOR;
                    double batteryToDest = (distToDest * effectiveConsumption / usableBatteryKwh) * 100.0;
                    double neededForDest = Math.min(85.0, batteryToDest + 10.0);
                    if (neededForDest > arrivalBattery + 3.0 && neededForDest < 80.0) {
                        candidateTargets.add(neededForDest);
                    }

                    for (double targetBattery : candidateTargets) {
                        double energyToChargeKwh = ((targetBattery - arrivalBattery) / 100.0) * usableBatteryKwh;
                        double chargeDurationMinutes = (energyToChargeKwh
                                / (effectiveChargeKw * ChargerScorer.CHARGING_EFFICIENCY)) * 60.0;

                        BigDecimal legCost = BigDecimal.valueOf(energyToChargeKwh)
                                .multiply(bestCharger.getPricePerKwh())
                                .setScale(2, RoundingMode.HALF_UP);

                        double newTotalTime = curr.totalTimeMinutes + legDriveMinutes + chargeDurationMinutes;
                        int bIdx = toBatteryBucket(targetBattery);

                        if (newTotalTime < minTime[nextNode][bIdx]) {
                            minTime[nextNode][bIdx] = newTotalTime;

                            TripStopDto stopDto = new TripStopDto(
                                    0, // assigned during path reconstruction
                                    targetStation.station().getId(),
                                    targetStation.station().getName(),
                                    targetStation.station().getAddress(),
                                    targetStation.station().getCity(),
                                    targetStation.station().getLatitude(),
                                    targetStation.station().getLongitude(),
                                    bestCharger.getId(),
                                    bestCharger.getName(),
                                    bestCharger.getConnectorType(),
                                    bestCharger.getPowerKw(),
                                    round(effectiveChargeKw, 1),
                                    round(arrivalBattery, 1),
                                    round(targetBattery, 1),
                                    round(energyToChargeKwh, 2),
                                    round(chargeDurationMinutes, 1),
                                    legCost
                            );

                            pq.add(new DijkstraState(
                                    nextNode,
                                    targetBattery,
                                    newTotalTime,
                                    curr.totalDriveMinutes + legDriveMinutes,
                                    curr.totalChargeMinutes + chargeDurationMinutes,
                                    curr.totalDistanceKm + roadDistanceKm,
                                    curr.totalCost.add(legCost),
                                    curr,
                                    stopDto,
                                    roadDistanceKm,
                                    legDriveMinutes,
                                    curr.batteryPercent,
                                    arrivalBattery
                            ));
                        }
                    }
                }
            }
        }

        if (destState == null) {
            throw new UnreachableTripException("Destination is unreachable: distance between charging stations "
                    + "exceeds maximum vehicle range (" + round(usableBatteryKwh / effectiveConsumption, 1)
                    + " km safe capacity) or battery reserve.");
        }

        // Reconstruct path
        List<DijkstraState> states = new ArrayList<>();
        for (DijkstraState s = destState; s != null; s = s.previous) {
            states.add(s);
        }
        Collections.reverse(states);

        List<TripStopDto> stops = new ArrayList<>();
        List<TripLegDto> legs = new ArrayList<>();
        List<RouteCoordinate> routeCoordinates = new ArrayList<>();

        routeCoordinates.add(new RouteCoordinate(
                request.startLat(),
                request.startLng(),
                "Trip Origin",
                "START"
        ));

        int stopNum = 1;
        int legNum = 1;

        for (int i = 1; i < states.size(); i++) {
            DijkstraState legState = states.get(i);
            DijkstraState prevState = states.get(i - 1);

            String fromName = getEntityName(prevState.nodeIndex, stationNodes, "Trip Origin");
            String toName = getEntityName(legState.nodeIndex, stationNodes, "Destination");

            legs.add(new TripLegDto(
                    legNum++,
                    fromName,
                    toName,
                    round(legState.legDistanceKm, 2),
                    round(legState.legDriveMinutes, 1),
                    round(legState.legStartBattery, 1),
                    round(legState.legEndBattery, 1)
            ));

            if (legState.stopInfo != null) {
                TripStopDto origStop = legState.stopInfo;
                stops.add(new TripStopDto(
                        stopNum++,
                        origStop.stationId(),
                        origStop.stationName(),
                        origStop.address(),
                        origStop.city(),
                        origStop.latitude(),
                        origStop.longitude(),
                        origStop.recommendedChargerId(),
                        origStop.chargerName(),
                        origStop.connectorType(),
                        origStop.powerKw(),
                        origStop.effectivePowerKw(),
                        origStop.arrivalBatteryPercent(),
                        origStop.targetBatteryPercent(),
                        origStop.energyChargedKwh(),
                        origStop.chargeDurationMinutes(),
                        origStop.estimatedCost()
                ));

                routeCoordinates.add(new RouteCoordinate(
                        origStop.latitude(),
                        origStop.longitude(),
                        origStop.stationName() + " (Stop " + (stopNum - 1) + ")",
                        "STOP"
                ));
            }
        }

        routeCoordinates.add(new RouteCoordinate(
                request.destLat(),
                request.destLng(),
                "Destination",
                "DESTINATION"
        ));

        String summary = String.format("Highway trip planned: %.1f km in %dh %02dm (%.0fm drive + %.0fm charging across %d stops)",
                destState.totalDistanceKm,
                (int) (destState.totalTimeMinutes / 60),
                (int) (destState.totalTimeMinutes % 60),
                destState.totalDriveMinutes,
                destState.totalChargeMinutes,
                stops.size());

        return new TripPlanResponse(
                vehicle.getId(),
                vehicle.getModel(),
                round(destState.totalDistanceKm, 2),
                round(destState.totalDriveMinutes, 1),
                round(destState.totalChargeMinutes, 1),
                round(destState.totalTimeMinutes, 1),
                destState.totalCost.setScale(2, RoundingMode.HALF_UP),
                stops.size(),
                stops,
                legs,
                routeCoordinates,
                summary
        );
    }

    private double getNodeLat(int nodeIndex, double startLat, double destLat, List<StationNode> stations) {
        if (nodeIndex == 0) return startLat;
        if (nodeIndex == stations.size() + 1) return destLat;
        return stations.get(nodeIndex - 1).lat();
    }

    private double getNodeLng(int nodeIndex, double startLng, double destLng, List<StationNode> stations) {
        if (nodeIndex == 0) return startLng;
        if (nodeIndex == stations.size() + 1) return destLng;
        return stations.get(nodeIndex - 1).lng();
    }

    private String getEntityName(int nodeIndex, List<StationNode> stations, String fallback) {
        if (nodeIndex == 0) return "Trip Origin";
        if (nodeIndex == stations.size() + 1) return "Destination";
        return stations.get(nodeIndex - 1).station().getName();
    }

    private int toBatteryBucket(double batteryPercent) {
        int bucket = (int) Math.round(batteryPercent / 5.0);
        return Math.max(0, Math.min(20, bucket));
    }

    private double getTerrainMultiplier(String terrain) {
        if (terrain == null) return 1.0;
        return switch (terrain.trim().toUpperCase()) {
            case "HILLY" -> 1.25;
            case "HIGHWAY" -> 1.15;
            default -> 1.0;
        };
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
