package com.voltgrid.voltgrid.station.service;


import com.voltgrid.voltgrid.dto.PageResponse;
import com.voltgrid.voltgrid.exception.BadRequestException;
import com.voltgrid.voltgrid.exception.ResourceNotFoundException;
import com.voltgrid.voltgrid.repository.UserRepository;
import com.voltgrid.voltgrid.station.dto.StationRequest;
import com.voltgrid.voltgrid.station.dto.StationResponse;
import com.voltgrid.voltgrid.station.entity.Station;
import com.voltgrid.voltgrid.station.entity.StationStatus;
import com.voltgrid.voltgrid.station.repository.StationRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StationService {

    private static final int MAX_STATIONS_PER_OWNER = 20;

    private final StationRepository stationRepository;
    private final UserRepository userRepository;
    private final NearbyStationService nearbyStationService;

    // ---------- Owner side ----------

    @Transactional
    public StationResponse create(Long ownerId, StationRequest request) {
        if (stationRepository.countByOwnerId(ownerId) >= MAX_STATIONS_PER_OWNER) {
            throw new BadRequestException("You can register at most " + MAX_STATIONS_PER_OWNER + " stations");
        }

        Station station = Station.builder()
                .owner(userRepository.getReferenceById(ownerId))
                .name(request.name().trim())
                .address(request.address().trim())
                .city(request.city().trim())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .build();

        return StationResponse.from(stationRepository.save(station));
    }

    @Transactional(readOnly = true)
    public PageResponse<StationResponse> listOwned(Long ownerId, Pageable pageable) {
        return PageResponse.from(stationRepository.findByOwnerId(ownerId, pageable)
                .map(StationResponse::from));
    }

    @Transactional(readOnly = true)
    public StationResponse getOwned(Long ownerId, Long stationId) {
        return StationResponse.from(findOwned(ownerId, stationId));
    }

    @Transactional
    public StationResponse update(Long ownerId, Long stationId, StationRequest request) {
        Station station = findOwned(ownerId, stationId);
        station.setName(request.name().trim());
        station.setAddress(request.address().trim());
        station.setCity(request.city().trim());
        station.setLatitude(request.latitude());
        station.setLongitude(request.longitude());
        return StationResponse.from(station);
    }

    @Transactional
    public StationResponse changeStatus(Long ownerId, Long stationId, StationStatus status) {
        Station station = findOwned(ownerId, stationId);
        station.setStatus(status);
        return StationResponse.from(station);
    }

    // ---------- Public side (only ACTIVE stations) ----------

    @Transactional(readOnly = true)
    public PageResponse<StationResponse> listPublic(String city, Pageable pageable) {
        Page<Station> page = (city == null || city.isBlank())
                ? stationRepository.findByStatus(StationStatus.ACTIVE, pageable)
                : stationRepository.findByStatusAndCityIgnoreCase(StationStatus.ACTIVE, city.trim(), pageable);
        return PageResponse.from(page.map(StationResponse::from));
    }

    @Transactional(readOnly = true)
    public StationResponse getPublic(Long stationId) {
        return stationRepository.findByIdAndStatus(stationId, StationStatus.ACTIVE)
                .map(StationResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found"));
    }

    /**
     * Ownership check: someone else's station is reported as "not found" (IDOR protection).
     */
    private Station findOwned(Long ownerId, Long stationId) {
        return stationRepository.findByIdAndOwnerId(stationId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found"));
    }
}