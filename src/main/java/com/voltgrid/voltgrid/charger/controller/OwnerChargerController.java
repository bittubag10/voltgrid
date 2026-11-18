package com.voltgrid.voltgrid.charger.controller;

import com.voltgrid.voltgrid.charger.dto.ChargerRequest;
import com.voltgrid.voltgrid.charger.dto.ChargerResponse;
import com.voltgrid.voltgrid.charger.dto.ChargerStatusRequest;
import com.voltgrid.voltgrid.charger.service.ChargerService;
import com.voltgrid.voltgrid.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/owner/stations/{stationId}/chargers")
@PreAuthorize("hasRole('OWNER')")
@RequiredArgsConstructor
public class OwnerChargerController {

    private final ChargerService chargerService;

    @PostMapping
    public ResponseEntity<ChargerResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @PathVariable Long stationId,
                                                  @Valid @RequestBody ChargerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chargerService.create(user.id(), stationId, request));
    }

    @GetMapping
    public List<ChargerResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                      @PathVariable Long stationId) {
        return chargerService.listOwned(user.id(), stationId);
    }

    @GetMapping("/{id}")
    public ChargerResponse get(@AuthenticationPrincipal AuthenticatedUser user,
                               @PathVariable Long stationId,
                               @PathVariable Long id) {
        return chargerService.getOwned(user.id(), stationId, id);
    }

    @PutMapping("/{id}")
    public ChargerResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                                  @PathVariable Long stationId,
                                  @PathVariable Long id,
                                  @Valid @RequestBody ChargerRequest request) {
        return chargerService.update(user.id(), stationId, id, request);
    }

    @PatchMapping("/{id}/status")
    public ChargerResponse changeStatus(@AuthenticationPrincipal AuthenticatedUser user,
                                        @PathVariable Long stationId,
                                        @PathVariable Long id,
                                        @Valid @RequestBody ChargerStatusRequest request) {
        return chargerService.changeStatus(user.id(), stationId, id, request.status());
    }
}