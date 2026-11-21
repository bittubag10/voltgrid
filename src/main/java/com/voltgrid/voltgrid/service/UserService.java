package com.voltgrid.voltgrid.service;

import com.voltgrid.voltgrid.booking.dto.BookingResponse;
import com.voltgrid.voltgrid.booking.entity.Booking;
import com.voltgrid.voltgrid.booking.entity.BookingStatus;
import com.voltgrid.voltgrid.booking.repository.BookingRepository;
import com.voltgrid.voltgrid.charger.entity.ChargerStatus;
import com.voltgrid.voltgrid.charger.repository.ChargerRepository;
import com.voltgrid.voltgrid.dto.AdminOverviewResponse;
import com.voltgrid.voltgrid.dto.PageResponse;
import com.voltgrid.voltgrid.dto.UserResponse;
import com.voltgrid.voltgrid.entity.User;
import com.voltgrid.voltgrid.enums.Role;
import com.voltgrid.voltgrid.exception.ResourceNotFoundException;
import com.voltgrid.voltgrid.repository.UserRepository;
import com.voltgrid.voltgrid.station.dto.StationResponse;
import com.voltgrid.voltgrid.station.entity.Station;
import com.voltgrid.voltgrid.station.entity.StationStatus;
import com.voltgrid.voltgrid.station.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final StationRepository stationRepository;
    private final ChargerRepository chargerRepository;
    private final BookingRepository bookingRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return userRepository.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listAll(Pageable pageable) {
        return PageResponse.from(userRepository.findAll(pageable).map(UserResponse::from));
    }

    @Transactional(readOnly = true)
    public AdminOverviewResponse getAdminOverview() {
        long totalUsers = userRepository.count();
        long totalDrivers = userRepository.countByRole(Role.DRIVER);
        long totalOwners = userRepository.countByRole(Role.OWNER);

        List<Station> allStations = stationRepository.findAll();
        long totalStations = allStations.size();
        long activeStations = allStations.stream()
                .filter(s -> s.getStatus() == StationStatus.ACTIVE)
                .count();

        long totalChargers = chargerRepository.count();
        long availableChargers = chargerRepository.countByStatus(ChargerStatus.AVAILABLE);
        long occupiedChargers = chargerRepository.countByStatus(ChargerStatus.OCCUPIED);

        long totalBookings = bookingRepository.count();
        long activeSessions = bookingRepository.countByStatus(BookingStatus.ACTIVE);

        List<Booking> allBookings = bookingRepository.findAll();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalOverstayPenalties = BigDecimal.ZERO;

        for (Booking b : allBookings) {
            if (b.getTotalAmount() != null) {
                totalRevenue = totalRevenue.add(b.getTotalAmount());
            } else if (b.getBaseAmount() != null) {
                totalRevenue = totalRevenue.add(b.getBaseAmount());
            }
            if (b.getOverstayPenalty() != null) {
                totalOverstayPenalties = totalOverstayPenalties.add(b.getOverstayPenalty());
            }
        }

        Instant now = clock.instant();
        List<StationResponse> stationResponses = allStations.stream()
                .map(StationResponse::from)
                .toList();

        List<BookingResponse> recentBookings = bookingRepository.findTop50ByOrderByStartTimeDesc().stream()
                .map(b -> BookingResponse.from(b, now))
                .toList();

        return new AdminOverviewResponse(
                totalUsers,
                totalDrivers,
                totalOwners,
                totalStations,
                activeStations,
                totalChargers,
                availableChargers,
                occupiedChargers,
                totalBookings,
                activeSessions,
                totalRevenue,
                totalOverstayPenalties,
                stationResponses,
                recentBookings
        );
    }

    @Transactional
    public UserResponse toggleUserStatus(Long userId, boolean enabled) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setEnabled(enabled);
        return UserResponse.from(userRepository.save(user));
    }
}