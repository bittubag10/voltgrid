package com.voltgrid.voltgrid.trip.controller;

import com.voltgrid.voltgrid.security.AuthenticatedUser;
import com.voltgrid.voltgrid.trip.dto.TripPlanRequest;
import com.voltgrid.voltgrid.trip.dto.TripPlanResponse;
import com.voltgrid.voltgrid.trip.service.TripPlannerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips")
@PreAuthorize("hasRole('DRIVER')")
@RequiredArgsConstructor
public class TripController {

    private final TripPlannerService tripPlannerService;

    @PostMapping("/plan")
    public TripPlanResponse planTrip(@AuthenticationPrincipal AuthenticatedUser user,
                                     @Valid @RequestBody TripPlanRequest request) {
        return tripPlannerService.planTrip(user.id(), request);
    }
}
