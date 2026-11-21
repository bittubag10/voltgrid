package com.voltgrid.voltgrid.recommendation.scoring;

import com.voltgrid.voltgrid.enums.ConnectorType;

import java.util.Comparator;
import java.util.Objects;

/**
 * Pure scoring logic (no Spring, no database): total time = drive + wait + charge.
 * All constants are simplified approximations and can be tuned in one place.
 */
public final class ChargerScorer {

    public static final double ROAD_DISTANCE_FACTOR = 1.3;
    public static final double AVERAGE_SPEED_KMPH = 50.0;
    public static final double MINUTES_PER_QUEUED_VEHICLE = 15.0;
    public static final double CHARGING_EFFICIENCY = 0.90;

    private ChargerScorer() {
    }

    public enum Eligibility {
        ELIGIBLE,
        CONNECTOR_MISMATCH,
        OUT_OF_RANGE
    }

    /** What we know about the driver's vehicle and its current state. */
    public record DriverState(
            ConnectorType connectorType,
            double vehicleMaxChargeKw,
            double usableBatteryKwh,
            double batteryPercent,
            double targetPercent,
            double effectiveConsumptionKwhPerKm,
            double rangeKm
    ) {
        public DriverState {
            Objects.requireNonNull(connectorType, "connectorType");
            requirePositive(vehicleMaxChargeKw, "vehicleMaxChargeKw");
            requirePositive(usableBatteryKwh, "usableBatteryKwh");
            requirePositive(effectiveConsumptionKwhPerKm, "effectiveConsumptionKwhPerKm");
            if (!Double.isFinite(batteryPercent) || batteryPercent < 0 || batteryPercent > 100) {
                throw new IllegalArgumentException("batteryPercent must be between 0 and 100");
            }
            if (!Double.isFinite(targetPercent) || targetPercent <= 0 || targetPercent > 100) {
                throw new IllegalArgumentException("targetPercent must be greater than 0 and at most 100");
            }
            if (!Double.isFinite(rangeKm) || rangeKm < 0) {
                throw new IllegalArgumentException("rangeKm cannot be negative");
            }
        }
    }

    /** One candidate charger, as seen from the driver's location. */
    public record ChargerOption(
            Long chargerId,
            ConnectorType connectorType,
            double powerKw,
            double straightLineDistanceKm,
            int queuedVehicles,
            double dynamicWaitMinutes
    ) {
        public ChargerOption(
                Long chargerId,
                ConnectorType connectorType,
                double powerKw,
                double straightLineDistanceKm,
                int queuedVehicles
        ) {
            this(chargerId, connectorType, powerKw, straightLineDistanceKm, queuedVehicles, queuedVehicles * MINUTES_PER_QUEUED_VEHICLE);
        }

        public ChargerOption {
            Objects.requireNonNull(chargerId, "chargerId");
            Objects.requireNonNull(connectorType, "connectorType");
            requirePositive(powerKw, "powerKw");
            if (!Double.isFinite(straightLineDistanceKm) || straightLineDistanceKm < 0) {
                throw new IllegalArgumentException("straightLineDistanceKm cannot be negative");
            }
            if (queuedVehicles < 0) {
                throw new IllegalArgumentException("queuedVehicles cannot be negative");
            }
            if (!Double.isFinite(dynamicWaitMinutes) || dynamicWaitMinutes < 0) {
                throw new IllegalArgumentException("dynamicWaitMinutes cannot be negative");
            }
        }
    }

    /** Time numbers are meaningful only when eligibility is ELIGIBLE. */
    public record Score(
            Long chargerId,
            Eligibility eligibility,
            double roadDistanceKm,
            double driveMinutes,
            double waitMinutes,
            double chargeMinutes,
            double totalMinutes,
            double effectivePowerKw,
            double energyToChargeKwh
    ) {
        public boolean isEligible() {
            return eligibility == Eligibility.ELIGIBLE;
        }
    }

    /** Ranking order: least total time first, then nearer, then lower id (stable tie-break). */
    public static final Comparator<Score> BY_TOTAL_TIME = Comparator
            .comparingDouble(Score::totalMinutes)
            .thenComparingDouble(Score::roadDistanceKm)
            .thenComparing(Score::chargerId);

    public static Score score(DriverState driver, ChargerOption charger) {
        double roadKm = charger.straightLineDistanceKm() * ROAD_DISTANCE_FACTOR;

        if (charger.connectorType() != driver.connectorType()) {
            return ineligible(charger, Eligibility.CONNECTOR_MISMATCH, roadKm);
        }
        if (roadKm > driver.rangeKm()) {
            return ineligible(charger, Eligibility.OUT_OF_RANGE, roadKm);
        }

        // Battery drains on the way, so a farther charger means more energy to add
        double currentEnergyKwh = driver.batteryPercent() / 100.0 * driver.usableBatteryKwh();
        double arrivalEnergyKwh = Math.max(0.0,
                currentEnergyKwh - roadKm * driver.effectiveConsumptionKwhPerKm());
        double targetEnergyKwh = driver.targetPercent() / 100.0 * driver.usableBatteryKwh();
        double energyToChargeKwh = Math.max(0.0, targetEnergyKwh - arrivalEnergyKwh);

        // The car cannot charge faster than its own limit, however strong the charger is
        double effectivePowerKw = Math.min(charger.powerKw(), driver.vehicleMaxChargeKw());

        double chargeMinutes = energyToChargeKwh / (effectivePowerKw * CHARGING_EFFICIENCY) * 60.0;
        double driveMinutes = roadKm / AVERAGE_SPEED_KMPH * 60.0;
        double waitMinutes = charger.dynamicWaitMinutes();

        return new Score(
                charger.chargerId(),
                Eligibility.ELIGIBLE,
                roadKm,
                driveMinutes,
                waitMinutes,
                chargeMinutes,
                driveMinutes + waitMinutes + chargeMinutes,
                effectivePowerKw,
                energyToChargeKwh
        );
    }

    private static Score ineligible(ChargerOption charger, Eligibility reason, double roadKm) {
        return new Score(charger.chargerId(), reason, roadKm, 0, 0, 0, 0, 0, 0);
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(name + " must be greater than 0");
        }
    }
}