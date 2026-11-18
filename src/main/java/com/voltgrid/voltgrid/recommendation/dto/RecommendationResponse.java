package com.voltgrid.voltgrid.recommendation.dto;

import java.util.List;

public record RecommendationResponse(
        Long vehicleId,
        String vehicleModel,
        double estimatedRangeKm,
        double searchRadiusKm,
        int chargersEvaluated,
        int incompatibleChargers,
        String message,
        List<ChargerRecommendation> recommendations
) {
}