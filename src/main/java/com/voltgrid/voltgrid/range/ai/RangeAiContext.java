package com.voltgrid.voltgrid.range.ai;

import com.voltgrid.voltgrid.enums.Terrain;

/** Only numbers and fixed enums: no free text from users ever reaches the prompt. */
public record RangeAiContext(
        double batteryPercent,
        boolean acOn,
        double speedKmph,
        Integer temperatureCelsius,
        Terrain terrain,
        double formulaRangeKm
) {
}