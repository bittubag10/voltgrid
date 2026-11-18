package com.voltgrid.voltgrid.recommendation.controller;

import com.voltgrid.voltgrid.recommendation.dto.RecommendationRequest;
import com.voltgrid.voltgrid.recommendation.dto.RecommendationResponse;
import com.voltgrid.voltgrid.recommendation.service.RecommendationService;
import com.voltgrid.voltgrid.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
@PreAuthorize("hasRole('DRIVER')")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @PostMapping("/chargers")
    public RecommendationResponse recommend(@AuthenticationPrincipal AuthenticatedUser user,
                                            @Valid @RequestBody RecommendationRequest request) {
        return recommendationService.recommend(user.id(), request);
    }
}