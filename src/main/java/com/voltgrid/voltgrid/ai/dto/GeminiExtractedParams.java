package com.voltgrid.voltgrid.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiExtractedParams(
        String originCity,
        String destinationCity,
        Double currentBatteryPercent,
        Double targetBatteryPercent,
        Boolean acOn,
        Integer speedKmh,
        String terrain,
        String rawReasoning
) {
}
