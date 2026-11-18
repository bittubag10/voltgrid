package com.voltgrid.voltgrid.station.controller;


import com.voltgrid.voltgrid.dto.PageResponse;
import com.voltgrid.voltgrid.enums.ConnectorType;
import com.voltgrid.voltgrid.station.dto.NearbyStationResponse;
import com.voltgrid.voltgrid.station.dto.StationResponse;
import com.voltgrid.voltgrid.station.service.NearbyStationService;
import com.voltgrid.voltgrid.station.service.StationService;
import com.voltgrid.voltgrid.util.Pagination;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/stations")
@RequiredArgsConstructor
public class StationController {

    private final StationService stationService;
    private final NearbyStationService nearbyStationService;

    @GetMapping
    public PageResponse<StationResponse> list(@RequestParam(required = false) String city,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return stationService.listPublic(city, Pagination.of(page, size, Sort.by("id")));
    }
    @GetMapping("/nearby")
    public List<NearbyStationResponse> nearby(@RequestParam double lat,
                                              @RequestParam double lng,
                                              @RequestParam(defaultValue = "25") double radiusKm,
                                              @RequestParam(required = false) ConnectorType connectorType,
                                              @RequestParam(required = false) BigDecimal minPowerKw,
                                              @RequestParam(defaultValue = "20") int limit) {
        return nearbyStationService.findNearby(lat, lng, radiusKm, connectorType, minPowerKw, limit);
    }

    @GetMapping("/{id}")
    public StationResponse get(@PathVariable Long id) {
        return stationService.getPublic(id);
    }
}