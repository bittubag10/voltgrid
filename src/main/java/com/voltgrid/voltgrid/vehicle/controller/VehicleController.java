package com.voltgrid.voltgrid.vehicle.controller;


import com.voltgrid.voltgrid.dto.PageResponse;
import com.voltgrid.voltgrid.security.AuthenticatedUser;
import com.voltgrid.voltgrid.util.Pagination;
import com.voltgrid.voltgrid.vehicle.dto.VehicleRequest;
import com.voltgrid.voltgrid.vehicle.dto.VehicleResponse;
import com.voltgrid.voltgrid.vehicle.service.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vehicles")
@PreAuthorize("hasRole('DRIVER')")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    @PostMapping
    public ResponseEntity<VehicleResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @Valid @RequestBody VehicleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(vehicleService.create(user.id(), request));
    }

    @GetMapping
    public PageResponse<VehicleResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return vehicleService.list(user.id(), Pagination.of(page, size, Sort.by("id")));
    }

    @GetMapping("/{id}")
    public VehicleResponse get(@AuthenticationPrincipal AuthenticatedUser user,
                               @PathVariable Long id) {
        return vehicleService.get(user.id(), id);
    }

    @PutMapping("/{id}")
    public VehicleResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                                  @PathVariable Long id,
                                  @Valid @RequestBody VehicleRequest request) {
        return vehicleService.update(user.id(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable Long id) {
        vehicleService.delete(user.id(), id);
        return ResponseEntity.noContent().build();
    }
}