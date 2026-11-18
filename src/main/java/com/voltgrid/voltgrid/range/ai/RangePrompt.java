package com.voltgrid.voltgrid.range.ai;

import com.voltgrid.voltgrid.range.calculator.RangeAdjuster;

import java.util.Locale;

public final class RangePrompt {

    private RangePrompt() {
    }

    public static String build(RangeAiContext c) {
        String temperature = c.temperatureCelsius() == null
                ? "unknown" : c.temperatureCelsius() + " degrees C";
        String terrain = c.terrain() == null ? "unknown" : c.terrain().name();

        return String.format(Locale.ROOT, """
                        You are an EV driving-range assistant. A formula has already estimated the range
                        for the conditions below. Estimate ONE multiplier for the conditions the formula
                        ignores (ambient temperature and terrain). 1.0 means no change. Cold weather and
                        hilly or mountain terrain reduce range. The multiplier must be between %.2f and %.2f.
                        Reply with JSON only, exactly in this shape: {"factor": <number>}

                        Battery: %.1f percent
                        Air conditioning: %s
                        Average speed: %.0f km/h
                        Ambient temperature: %s
                        Terrain: %s
                        Formula range: %.1f km
                        """,
                RangeAdjuster.MIN_FACTOR, RangeAdjuster.MAX_FACTOR,
                c.batteryPercent(), c.acOn() ? "on" : "off", c.speedKmph(),
                temperature, terrain, c.formulaRangeKm());
    }
}