package com.voltgrid.voltgrid.station.controller;

import com.voltgrid.voltgrid.security.AuthenticatedUser;
import com.voltgrid.voltgrid.station.dto.OwnerAnalyticsResponse;
import com.voltgrid.voltgrid.station.service.OwnerAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/owner/analytics")
@PreAuthorize("hasRole('OWNER')")
@RequiredArgsConstructor
@Tag(name = "Owner Analytics", description = "B2B Station Owner revenue, penalty, and charger utilization metrics")
public class OwnerAnalyticsController {

    private final OwnerAnalyticsService ownerAnalyticsService;

    @GetMapping
    @Operation(summary = "Get comprehensive owner revenue, overstay penalties, and utilization metrics")
    public OwnerAnalyticsResponse getAnalytics(@AuthenticationPrincipal AuthenticatedUser user) {
        return ownerAnalyticsService.getOwnerAnalytics(user.id());
    }
}
