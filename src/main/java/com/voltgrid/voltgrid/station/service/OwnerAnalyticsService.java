package com.voltgrid.voltgrid.station.service;

import com.voltgrid.voltgrid.booking.entity.Booking;
import com.voltgrid.voltgrid.booking.entity.BookingStatus;
import com.voltgrid.voltgrid.booking.repository.BookingRepository;
import com.voltgrid.voltgrid.charger.entity.Charger;
import com.voltgrid.voltgrid.charger.entity.ChargerStatus;
import com.voltgrid.voltgrid.charger.repository.ChargerRepository;
import com.voltgrid.voltgrid.station.dto.HourlyActivityDto;
import com.voltgrid.voltgrid.station.dto.OwnerAnalyticsResponse;
import com.voltgrid.voltgrid.station.dto.OwnerOverallMetrics;
import com.voltgrid.voltgrid.station.dto.StationAnalyticsDto;
import com.voltgrid.voltgrid.station.entity.Station;
import com.voltgrid.voltgrid.station.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerAnalyticsService {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Kolkata");
    private static final long REFERENCE_CAPACITY_MINUTES_PER_CHARGER = 30L * 24L * 60L; // 30-day baseline

    private final StationRepository stationRepository;
    private final ChargerRepository chargerRepository;
    private final BookingRepository bookingRepository;

    @Transactional(readOnly = true)
    public OwnerAnalyticsResponse getOwnerAnalytics(Long ownerId) {
        List<Station> stations = stationRepository.findByOwnerIdOrderByIdAsc(ownerId);
        List<Booking> allBookings = bookingRepository.findAllByStationOwnerId(ownerId);

        // Fetch chargers for all owner stations
        List<Long> stationIds = stations.stream().map(Station::getId).toList();
        List<Charger> allChargers = stationIds.isEmpty()
                ? List.of()
                : chargerRepository.findByStationIdIn(stationIds);

        Map<Long, List<Charger>> chargersByStation = allChargers.stream()
                .collect(Collectors.groupingBy(c -> c.getStation().getId()));

        Map<Long, List<Booking>> bookingsByStation = allBookings.stream()
                .collect(Collectors.groupingBy(b -> b.getCharger().getStation().getId()));

        // 1. Calculate per-station analytics
        List<StationAnalyticsDto> stationAnalyticsList = new ArrayList<>();
        for (Station station : stations) {
            List<Charger> stationChargers = chargersByStation.getOrDefault(station.getId(), List.of());
            List<Booking> stationBookings = bookingsByStation.getOrDefault(station.getId(), List.of());

            stationAnalyticsList.add(buildStationAnalytics(station, stationChargers, stationBookings));
        }

        // 2. Calculate overall metrics
        OwnerOverallMetrics overallMetrics = buildOverallMetrics(stations, allChargers, allBookings);

        return new OwnerAnalyticsResponse(overallMetrics, stationAnalyticsList);
    }

    private StationAnalyticsDto buildStationAnalytics(Station station,
                                                      List<Charger> chargers,
                                                      List<Booking> bookings) {
        int totalChargers = chargers.size();
        long completedSessions = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.COMPLETED)
                .count();
        long activeSessions = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.ACTIVE)
                .count();

        BigDecimal energyRevenue = BigDecimal.ZERO;
        BigDecimal penaltyRevenue = BigDecimal.ZERO;
        long totalChargingMinutes = 0L;

        for (Booking b : bookings) {
            if (isRevenueGeneratingStatus(b.getStatus())) {
                if (b.getBaseAmount() != null) {
                    energyRevenue = energyRevenue.add(b.getBaseAmount());
                }
            }
            if (b.getOverstayPenalty() != null) {
                penaltyRevenue = penaltyRevenue.add(b.getOverstayPenalty());
            }

            if (b.getStatus() == BookingStatus.COMPLETED || b.getStatus() == BookingStatus.ACTIVE) {
                totalChargingMinutes += calculateSessionDurationMinutes(b);
            }
        }

        BigDecimal totalRevenue = energyRevenue.add(penaltyRevenue);
        double utilizationPercent = computeUtilizationPercent(totalChargingMinutes, totalChargers);

        List<HourlyActivityDto> hourlyActivity = computeHourlyActivity(bookings);
        HourStats hourStats = findBusiestHour(hourlyActivity);

        return new StationAnalyticsDto(
                station.getId(),
                station.getName(),
                station.getAddress(),
                station.getCity(),
                totalChargers,
                completedSessions,
                activeSessions,
                energyRevenue,
                penaltyRevenue,
                totalRevenue,
                utilizationPercent,
                hourStats.hour(),
                hourStats.label(),
                hourlyActivity
        );
    }

    private OwnerOverallMetrics buildOverallMetrics(List<Station> stations,
                                                    List<Charger> chargers,
                                                    List<Booking> bookings) {
        int totalStations = stations.size();
        int totalChargers = chargers.size();

        long completedSessions = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.COMPLETED)
                .count();
        long activeSessions = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.ACTIVE)
                .count();

        BigDecimal energyRevenue = BigDecimal.ZERO;
        BigDecimal penaltyRevenue = BigDecimal.ZERO;
        long totalChargingMinutes = 0L;

        for (Booking b : bookings) {
            if (isRevenueGeneratingStatus(b.getStatus())) {
                if (b.getBaseAmount() != null) {
                    energyRevenue = energyRevenue.add(b.getBaseAmount());
                }
            }
            if (b.getOverstayPenalty() != null) {
                penaltyRevenue = penaltyRevenue.add(b.getOverstayPenalty());
            }

            if (b.getStatus() == BookingStatus.COMPLETED || b.getStatus() == BookingStatus.ACTIVE) {
                totalChargingMinutes += calculateSessionDurationMinutes(b);
            }
        }

        BigDecimal totalRevenue = energyRevenue.add(penaltyRevenue);
        double avgUtilizationPercent = computeUtilizationPercent(totalChargingMinutes, totalChargers);

        List<HourlyActivityDto> hourlyActivity = computeHourlyActivity(bookings);
        HourStats hourStats = findBusiestHour(hourlyActivity);

        return new OwnerOverallMetrics(
                totalStations,
                totalChargers,
                completedSessions,
                activeSessions,
                energyRevenue,
                penaltyRevenue,
                totalRevenue,
                avgUtilizationPercent,
                hourStats.hour(),
                hourStats.label(),
                hourlyActivity
        );
    }

    private boolean isRevenueGeneratingStatus(BookingStatus status) {
        return status == BookingStatus.COMPLETED
                || status == BookingStatus.ACTIVE
                || status == BookingStatus.CONFIRMED;
    }

    private long calculateSessionDurationMinutes(Booking b) {
        Instant start = b.getSessionStartTime() != null ? b.getSessionStartTime() : b.getStartTime();
        Instant end = b.getPlugOutAt() != null
                ? b.getPlugOutAt()
                : (b.getChargeCompletedAt() != null ? b.getChargeCompletedAt() : b.getEndTime());

        if (start != null && end != null && end.isAfter(start)) {
            return Duration.between(start, end).toMinutes();
        }
        return 0L;
    }

    private double computeUtilizationPercent(long chargingMinutes, int chargerCount) {
        if (chargerCount <= 0 || chargingMinutes <= 0) {
            return 0.0;
        }
        long capacityMinutes = (long) chargerCount * REFERENCE_CAPACITY_MINUTES_PER_CHARGER;
        double pct = ((double) chargingMinutes / capacityMinutes) * 100.0;
        return Math.min(100.0, Math.round(pct * 10.0) / 10.0);
    }

    private List<HourlyActivityDto> computeHourlyActivity(List<Booking> bookings) {
        long[] counts = new long[24];
        BigDecimal[] revenues = new BigDecimal[24];
        for (int i = 0; i < 24; i++) {
            revenues[i] = BigDecimal.ZERO;
        }

        for (Booking b : bookings) {
            if (b.getStartTime() != null) {
                int hour = b.getStartTime().atZone(DISPLAY_ZONE).getHour();
                counts[hour]++;

                BigDecimal rev = BigDecimal.ZERO;
                if (b.getBaseAmount() != null && isRevenueGeneratingStatus(b.getStatus())) {
                    rev = rev.add(b.getBaseAmount());
                }
                if (b.getOverstayPenalty() != null) {
                    rev = rev.add(b.getOverstayPenalty());
                }
                revenues[hour] = revenues[hour].add(rev);
            }
        }

        List<HourlyActivityDto> result = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            String label = String.format("%02d:00", h);
            result.add(new HourlyActivityDto(h, label, counts[h], revenues[h]));
        }
        return result;
    }

    private HourStats findBusiestHour(List<HourlyActivityDto> hourlyActivity) {
        HourlyActivityDto maxActivity = hourlyActivity.stream()
                .max(Comparator.comparingLong(HourlyActivityDto::sessionCount))
                .orElse(null);

        if (maxActivity == null || maxActivity.sessionCount() == 0) {
            return new HourStats(null, "No sessions yet");
        }

        int h = maxActivity.hour();
        String label = String.format("%02d:00 - %02d:00 (%d sessions)", h, (h + 1) % 24, maxActivity.sessionCount());
        return new HourStats(h, label);
    }

    private record HourStats(Integer hour, String label) {}
}
