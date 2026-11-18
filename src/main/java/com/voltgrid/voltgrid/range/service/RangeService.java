package com.voltgrid.voltgrid.range.service;

import com.voltgrid.voltgrid.exception.ResourceNotFoundException;
import com.voltgrid.voltgrid.range.ai.RangeAiClient;
import com.voltgrid.voltgrid.range.ai.RangeAiContext;
import com.voltgrid.voltgrid.range.calculator.RangeAdjuster;
import com.voltgrid.voltgrid.range.calculator.RangeCalculator;
import com.voltgrid.voltgrid.range.dto.RangeRequest;
import com.voltgrid.voltgrid.range.dto.RangeResponse;
import com.voltgrid.voltgrid.vehicle.entity.Vehicle;
import com.voltgrid.voltgrid.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RangeService {

    private final VehicleRepository vehicleRepository;
    private final RangeAiClient rangeAiClient;

    // Deliberately NOT @Transactional: the AI call can take seconds and must not hold a DB connection.
    // The repository call below runs in its own short read-only transaction.
    public RangeResponse predict(Long driverId, RangeRequest request) {

        Vehicle vehicle = vehicleRepository
                .findByIdAndDriverIdAndActiveTrue(request.vehicleId(), driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));

        int speed = request.speedKmph() == null
                ? (int) RangeCalculator.DEFAULT_SPEED_KMPH
                : request.speedKmph();

        double batteryPercent = request.batteryPercent().doubleValue();

        RangeCalculator.Result result = RangeCalculator.calculate(
                vehicle.getUsableBatteryKwh().doubleValue(),
                vehicle.getBaseConsumptionKwhPerKm().doubleValue(),
                batteryPercent,
                request.acOn(),
                (double) speed
        );

        double formulaRangeKm = result.rangeKm();

        // The AI only adds information the formula lacks (temperature, terrain),
        // so we call it only when the driver provided at least one of them
        boolean hasExtraContext = request.temperatureCelsius() != null || request.terrain() != null;

        Optional<Double> aiFactor = (hasExtraContext && formulaRangeKm > 0)
                ? rangeAiClient.suggestFactor(new RangeAiContext(
                batteryPercent,
                request.acOn(),
                speed,
                request.temperatureCelsius(),
                request.terrain(),
                formulaRangeKm))
                : Optional.empty();

        RangeAdjuster.Adjusted adjusted = RangeAdjuster.adjust(formulaRangeKm, aiFactor);

        return new RangeResponse(
                vehicle.getId(),
                vehicle.getModel(),
                batteryPercent,
                request.acOn(),
                speed,
                RangeCalculator.RESERVE_PERCENT,
                round(result.usableEnergyKwh(), 2),
                round(result.effectiveConsumptionKwhPerKm(), 4),
                round(formulaRangeKm, 2),
                adjusted.aiApplied() ? round(adjusted.appliedFactor(), 2) : null,
                round(adjusted.rangeKm(), 2),
                adjusted.aiApplied() ? "AI_ADJUSTED" : "FORMULA"
        );
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}