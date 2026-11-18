package com.voltgrid.voltgrid.range.ai;

import java.util.Optional;

public interface RangeAiClient {

    /**
     * Returns the raw multiplier suggested by the AI, or empty if the AI is unavailable
     * or its answer is unusable. Implementations must never throw.
     */
    Optional<Double> suggestFactor(RangeAiContext context);
}