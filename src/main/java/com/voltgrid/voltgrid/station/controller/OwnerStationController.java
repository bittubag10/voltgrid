package com.voltgrid.voltgrid.station.controller;

import com.voltgrid.voltgrid.dto.PageResponse;
import com.voltgrid.voltgrid.security.AuthenticatedUser;
import com.voltgrid.voltgrid.station.dto.StationRequest;
import com.voltgrid.voltgrid.station.dto.StationResponse;
import com.voltgrid.voltgrid.station.dto.StationStatusRequest;
import com.voltgrid.voltgrid.station.service.StationService;
import com.voltgrid.voltgrid.util.Pagination;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/owner/stations")
@PreAuthorize("hasRole('OWNER')")
@RequiredArgsConstructor
public class OwnerStationController {

    private final StationService stationService;

    @PostMapping
    public ResponseEntity<StationResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @Valid @RequestBody StationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(stationService.create(user.id(), request));
    }

    @GetMapping
    public PageResponse<StationResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return stationService.listOwned(user.id(), Pagination.of(page, size, Sort.by("id")));
    }

    @GetMapping("/{id}")
    public StationResponse get(@AuthenticationPrincipal AuthenticatedUser user,
                               @PathVariable Long id) {
        return stationService.getOwned(user.id(), id);
    }

    @PutMapping("/{id}")
    public StationResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                                  @PathVariable Long id,
                                  @Valid @RequestBody StationRequest request) {
        return stationService.update(user.id(), id, request);
    }

    @PatchMapping("/{id}/status")
    public StationResponse changeStatus(@AuthenticationPrincipal AuthenticatedUser user,
                                        @PathVariable Long id,
                                        @Valid @RequestBody StationStatusRequest request) {
        return stationService.changeStatus(user.id(), id, request.status());
    }
}