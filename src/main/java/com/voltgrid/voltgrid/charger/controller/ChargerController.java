package com.voltgrid.voltgrid.charger.controller;

import com.voltgrid.voltgrid.charger.dto.ChargerResponse;
import com.voltgrid.voltgrid.charger.service.ChargerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stations/{stationId}/chargers")
@RequiredArgsConstructor
public class ChargerController {

    private final ChargerService chargerService;

    @GetMapping
    public List<ChargerResponse> list(@PathVariable Long stationId) {
        return chargerService.listPublic(stationId);
    }
}