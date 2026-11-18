package com.voltgrid.voltgrid.charger.service;

import com.voltgrid.voltgrid.charger.dto.ChargerRequest;
import com.voltgrid.voltgrid.charger.dto.ChargerResponse;
import com.voltgrid.voltgrid.charger.entity.Charger;
import com.voltgrid.voltgrid.charger.entity.ChargerStatus;
import com.voltgrid.voltgrid.charger.repository.ChargerRepository;
import com.voltgrid.voltgrid.exception.BadRequestException;
import com.voltgrid.voltgrid.exception.ConflictException;
import com.voltgrid.voltgrid.exception.ResourceNotFoundException;
import com.voltgrid.voltgrid.station.entity.Station;
import com.voltgrid.voltgrid.station.entity.StationStatus;
import com.voltgrid.voltgrid.station.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChargerService {

    private static final int MAX_CHARGERS_PER_STATION = 30;

    private final ChargerRepository chargerRepository;
    private final StationRepository stationRepository;

    // ---------- Owner side ----------

    @Transactional
    public ChargerResponse create(Long ownerId, Long stationId, ChargerRequest request) {
        Station station = findOwnedStation(ownerId, stationId);
        String name = request.name().trim();

        if (chargerRepository.countByStationId(stationId) >= MAX_CHARGERS_PER_STATION) {
            throw new BadRequestException("A station can have at most " + MAX_CHARGERS_PER_STATION + " chargers");
        }
        if (chargerRepository.existsByStationIdAndNameIgnoreCase(stationId, name)) {
            throw new ConflictException("A charger with this name already exists in this station");
        }

        Charger charger = Charger.builder()
                .station(station)
                .name(name)
                .powerKw(request.powerKw())
                .connectorType(request.connectorType())
                .pricePerKwh(request.pricePerKwh())
                .build();

        return ChargerResponse.from(chargerRepository.save(charger));
    }

    @Transactional(readOnly = true)
    public List<ChargerResponse> listOwned(Long ownerId, Long stationId) {
        findOwnedStation(ownerId, stationId);
        return chargerRepository.findByStationIdOrderByIdAsc(stationId).stream()
                .map(ChargerResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChargerResponse getOwned(Long ownerId, Long stationId, Long chargerId) {
        return ChargerResponse.from(findOwnedCharger(ownerId, stationId, chargerId));
    }

    @Transactional
    public ChargerResponse update(Long ownerId, Long stationId, Long chargerId, ChargerRequest request) {
        Charger charger = findOwnedCharger(ownerId, stationId, chargerId);
        String name = request.name().trim();

        if (chargerRepository.existsByStationIdAndNameIgnoreCaseAndIdNot(stationId, name, chargerId)) {
            throw new ConflictException("A charger with this name already exists in this station");
        }

        charger.setName(name);
        charger.setPowerKw(request.powerKw());
        charger.setConnectorType(request.connectorType());
        charger.setPricePerKwh(request.pricePerKwh());
        return ChargerResponse.from(charger);
    }

    @Transactional
    public ChargerResponse changeStatus(Long ownerId, Long stationId, Long chargerId, ChargerStatus status) {
        if (status == ChargerStatus.OCCUPIED) {
            throw new BadRequestException("OCCUPIED is set automatically during charging sessions");
        }
        Charger charger = findOwnedCharger(ownerId, stationId, chargerId);
        charger.setStatus(status);
        return ChargerResponse.from(charger);
    }

    // ---------- Public side ----------

    @Transactional(readOnly = true)
    public List<ChargerResponse> listPublic(Long stationId) {
        stationRepository.findByIdAndStatus(stationId, StationStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found"));

        return chargerRepository
                .findByStationIdAndStatusNotOrderByIdAsc(stationId, ChargerStatus.OUT_OF_SERVICE).stream()
                .map(ChargerResponse::from)
                .toList();
    }

    // ---------- Ownership checks ----------

    private Station findOwnedStation(Long ownerId, Long stationId) {
        return stationRepository.findByIdAndOwnerId(stationId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found"));
    }

    private Charger findOwnedCharger(Long ownerId, Long stationId, Long chargerId) {
        return chargerRepository.findOwned(chargerId, stationId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Charger not found"));
    }
}