package com.voltgrid.voltgrid.range.calculator;

import java.util.Optional;

/**
 * Applies an AI-suggested multiplier to the formula range, safely.
 * Overestimating range is more dangerous than underestimating it, so the bounds are asymmetric.
 */
public final class RangeAdjuster {

    public static final double MIN_FACTOR = 0.70;
    public static final double MAX_FACTOR = 1.05;

    private RangeAdjuster() {
    }

    public record Adjusted(double rangeKm, double appliedFactor, boolean aiApplied) {
    }

    public static Adjusted adjust(double formulaRangeKm, Optional<Double> aiFactor) {
        if (aiFactor.isEmpty() || !Double.isFinite(aiFactor.get()) || aiFactor.get() <= 0) {
            return new Adjusted(formulaRangeKm, 1.0, false);
        }

        double applied = Math.min(Math.max(aiFactor.get(), MIN_FACTOR), MAX_FACTOR);
        return new Adjusted(formulaRangeKm * applied, applied, true);
    }
}