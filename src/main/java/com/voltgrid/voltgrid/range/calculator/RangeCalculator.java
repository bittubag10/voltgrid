package com.voltgrid.voltgrid.range.calculator;

/**
 * Pure range estimation logic (no Spring, no database), so it is trivial to unit test.
 * All constants are simplified approximations, not lab-calibrated values.
 */
public final class RangeCalculator {

    public static final double RESERVE_PERCENT = 5.0;
    public static final double AC_FACTOR = 1.15;
    public static final double DEFAULT_SPEED_KMPH = 60.0;

    private static final double SPEED_BASELINE_KMPH = 60.0;
    private static final double SPEED_COEFFICIENT = 0.35;

    private RangeCalculator() {
    }

    public record Result(
            double usableEnergyKwh,
            double acFactor,
            double speedFactor,
            double effectiveConsumptionKwhPerKm,
            double rangeKm
    ) {
    }

    /** Consumption multiplier for speed: 1.0 up to the baseline, then grows quadratically. */
    public static double speedFactor(double speedKmph) {
        if (speedKmph <= SPEED_BASELINE_KMPH) {
            return 1.0;
        }
        double ratio = (speedKmph - SPEED_BASELINE_KMPH) / SPEED_BASELINE_KMPH;
        return 1.0 + SPEED_COEFFICIENT * ratio * ratio;
    }

    public static Result calculate(double usableBatteryKwh,
                                   double baseConsumptionKwhPerKm,
                                   double batteryPercent,
                                   boolean acOn,
                                   Double speedKmph) {

        if (!Double.isFinite(usableBatteryKwh) || usableBatteryKwh <= 0) {
            throw new IllegalArgumentException("usableBatteryKwh must be greater than 0");
        }
        if (!Double.isFinite(baseConsumptionKwhPerKm) || baseConsumptionKwhPerKm <= 0) {
            throw new IllegalArgumentException("baseConsumptionKwhPerKm must be greater than 0");
        }
        if (!Double.isFinite(batteryPercent) || batteryPercent < 0 || batteryPercent > 100) {
            throw new IllegalArgumentException("batteryPercent must be between 0 and 100");
        }
        double speed = speedKmph == null ? DEFAULT_SPEED_KMPH : speedKmph;
        if (!Double.isFinite(speed) || speed <= 0) {
            throw new IllegalArgumentException("speedKmph must be greater than 0");
        }

        double usableFraction = Math.max(0.0, batteryPercent - RESERVE_PERCENT) / 100.0;
        double usableEnergyKwh = usableFraction * usableBatteryKwh;

        double acFactor = acOn ? AC_FACTOR : 1.0;
        double speedFactor = speedFactor(speed);
        double effectiveConsumption = baseConsumptionKwhPerKm * acFactor * speedFactor;

        double rangeKm = usableEnergyKwh / effectiveConsumption;

        return new Result(usableEnergyKwh, acFactor, speedFactor, effectiveConsumption, rangeKm);
    }
}