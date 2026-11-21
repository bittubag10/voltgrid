package com.voltgrid.voltgrid.ai.service;

import com.voltgrid.voltgrid.ai.dto.AiTripParseRequest;
import com.voltgrid.voltgrid.ai.dto.AiTripParseResponse;
import com.voltgrid.voltgrid.ai.dto.GeminiExtractedParams;
import com.voltgrid.voltgrid.ai.util.IndianCityCoordinates;
import com.voltgrid.voltgrid.ai.util.IndianCityCoordinates.CityGeo;
import com.voltgrid.voltgrid.exception.BadRequestException;
import com.voltgrid.voltgrid.exception.ResourceNotFoundException;
import com.voltgrid.voltgrid.trip.dto.TripPlanRequest;
import com.voltgrid.voltgrid.trip.dto.TripPlanResponse;
import com.voltgrid.voltgrid.trip.service.TripPlannerService;
import com.voltgrid.voltgrid.vehicle.entity.Vehicle;
import com.voltgrid.voltgrid.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiTripAssistantService {

    private final GeminiTripParserClient geminiClient;
    private final TripPlannerService tripPlannerService;
    private final VehicleRepository vehicleRepository;

    private static final CityGeo DEFAULT_ORIGIN =
            new CityGeo("Vadodara", new BigDecimal("22.307200"), new BigDecimal("73.181200"));
    private static final CityGeo DEFAULT_DESTINATION =
            new CityGeo("Mumbai", new BigDecimal("19.076000"), new BigDecimal("72.877700"));

    @Transactional(readOnly = true)
    public AiTripParseResponse parseAndProcessTrip(Long driverId, AiTripParseRequest request) {
        String prompt = request.prompt();
        if (prompt == null || prompt.isBlank()) {
            throw new BadRequestException("Trip prompt cannot be empty.");
        }

        // 1. Attempt AI parameter extraction with fallback to deterministic regex
        Optional<GeminiExtractedParams> aiParams = geminiClient.parsePrompt(prompt);
        boolean aiParsed = aiParams.isPresent();

        GeminiExtractedParams params = aiParams.orElseGet(() -> parseDeterministically(prompt));

        // 2. Ground and resolve Geographic coordinates against authoritative Indian cities table
        CityGeo originGeo = resolveOrigin(params.originCity(), prompt);
        CityGeo destGeo = resolveDestination(params.destinationCity(), prompt, originGeo);

        // 3. Normalize and clamp telemetry numbers
        BigDecimal batteryPercent = clampBattery(params.currentBatteryPercent(), new BigDecimal("25.00"));
        BigDecimal targetPercent = clampBattery(params.targetBatteryPercent(), new BigDecimal("80.00"));
        boolean acOn = params.acOn() != null ? params.acOn() : !prompt.toLowerCase().contains("ac off");
        int speedKmh = (params.speedKmh() != null && params.speedKmh() >= 30 && params.speedKmh() <= 130)
                ? params.speedKmh() : extractSpeed(prompt, 80);
        String terrain = resolveTerrain(params.terrain(), prompt);

        // 4. Determine intent
        boolean hasValidDestination = destGeo != null && !destGeo.canonicalName().equalsIgnoreCase(originGeo.canonicalName());
        String intent = hasValidDestination ? "HIGHWAY_TRIP" : "LOCAL_CHARGER_SEARCH";

        // 5. Construct summary
        String summary = buildSummary(intent, originGeo, destGeo, batteryPercent, acOn, speedKmh, terrain, aiParsed);

        // 6. If highway trip and autoPlan is requested, pass directly to TripPlannerService
        TripPlanResponse autoPlannedTrip = null;
        if (hasValidDestination && request.shouldAutoPlan()) {
            Vehicle vehicle = resolveVehicle(driverId, request.vehicleId());
            if (vehicle != null) {
                try {
                    TripPlanRequest tripReq = new TripPlanRequest(
                            vehicle.getId(),
                            originGeo.lat(),
                            originGeo.lng(),
                            destGeo.lat(),
                            destGeo.lng(),
                            batteryPercent,
                            acOn,
                            (double) speedKmh,
                            terrain
                    );
                    autoPlannedTrip = tripPlannerService.planTrip(driverId, tripReq);
                } catch (Exception ex) {
                    log.info("Auto-planning highway trip resulted in: {}", ex.getMessage());
                    // Keep response clean so frontend can still display parsed params and route error
                }
            }
        }

        return new AiTripParseResponse(
                prompt,
                intent,
                originGeo.canonicalName(),
                originGeo.lat(),
                originGeo.lng(),
                destGeo != null ? destGeo.canonicalName() : null,
                destGeo != null ? destGeo.lat() : null,
                destGeo != null ? destGeo.lng() : null,
                batteryPercent,
                targetPercent,
                acOn,
                speedKmh,
                terrain,
                summary,
                aiParsed,
                autoPlannedTrip
        );
    }

    private Vehicle resolveVehicle(Long driverId, Long requestedVehicleId) {
        if (requestedVehicleId != null) {
            return vehicleRepository.findByIdAndDriverIdAndActiveTrue(requestedVehicleId, driverId)
                    .orElseThrow(() -> new ResourceNotFoundException("Selected vehicle not found for driver."));
        }
        List<Vehicle> driverVehicles = vehicleRepository.findByDriverIdAndActiveTrue(driverId);
        return driverVehicles.isEmpty() ? null : driverVehicles.get(0);
    }

    private CityGeo resolveOrigin(String rawOrigin, String fullPrompt) {
        if (rawOrigin != null) {
            Optional<CityGeo> direct = IndianCityCoordinates.find(rawOrigin);
            if (direct.isPresent()) return direct.get();
        }

        // Look for patterns like "in vadodara", "from vadodara", "at vadodara"
        Pattern p = Pattern.compile("(?i)(?:in|from|at|near)\\s+([a-zA-Z]+)");
        Matcher m = p.matcher(fullPrompt);
        while (m.find()) {
            Optional<CityGeo> match = IndianCityCoordinates.find(m.group(1));
            if (match.isPresent()) return match.get();
        }

        return IndianCityCoordinates.findInText(fullPrompt).orElse(DEFAULT_ORIGIN);
    }

    private CityGeo resolveDestination(String rawDest, String fullPrompt, CityGeo origin) {
        if (rawDest != null) {
            Optional<CityGeo> direct = IndianCityCoordinates.find(rawDest);
            if (direct.isPresent() && !direct.get().canonicalName().equalsIgnoreCase(origin.canonicalName())) {
                return direct.get();
            }
        }

        // Look for patterns like "to mumbai", "driving to mumbai", "heading to pune"
        Pattern p = Pattern.compile("(?i)(?:to|towards|into)\\s+([a-zA-Z]+)");
        Matcher m = p.matcher(fullPrompt);
        while (m.find()) {
            Optional<CityGeo> match = IndianCityCoordinates.find(m.group(1));
            if (match.isPresent() && !match.get().canonicalName().equalsIgnoreCase(origin.canonicalName())) {
                return match.get();
            }
        }

        // If no explicit destination found in prompt, return null (meaning local charger search)
        return null;
    }

    private GeminiExtractedParams parseDeterministically(String prompt) {
        String lower = prompt.toLowerCase();

        // 1. Extract Battery SOC
        Double battery = null;
        Matcher mBat = Pattern.compile("(\\d{1,3})\\s*%(?:\\s*battery|\\s*soc)?").matcher(lower);
        if (mBat.find()) {
            battery = Double.parseDouble(mBat.group(1));
        } else {
            Matcher mBat2 = Pattern.compile("(?:battery|soc)\\s*(?:is|at|of)?\\s*(\\d{1,3})").matcher(lower);
            if (mBat2.find()) {
                battery = Double.parseDouble(mBat2.group(1));
            }
        }

        // 2. Extract Speed
        Integer speed = null;
        Matcher mSpeed = Pattern.compile("(\\d{2,3})\\s*(?:km/?h|kmph)").matcher(lower);
        if (mSpeed.find()) {
            speed = Integer.parseInt(mSpeed.group(1));
        }

        // 3. Extract AC
        Boolean acOn = null;
        if (lower.contains("ac on") || lower.contains("ac active") || lower.contains("with ac")) {
            acOn = true;
        } else if (lower.contains("ac off") || lower.contains("without ac") || lower.contains("no ac")) {
            acOn = false;
        }

        // 4. Extract Terrain
        String terrain = "HIGHWAY";
        if (lower.contains("hilly") || lower.contains("mountain") || lower.contains("incline")) {
            terrain = "HILLY";
        } else if (lower.contains("city") || lower.contains("flat") || lower.contains("urban")) {
            terrain = "FLAT";
        }

        return new GeminiExtractedParams(
                null,
                null,
                battery,
                80.0,
                acOn,
                speed,
                terrain,
                "Deterministic regex rule extractor applied"
        );
    }

    private BigDecimal clampBattery(Double val, BigDecimal fallback) {
        if (val == null) return fallback;
        double clamped = Math.max(5.0, Math.min(100.0, val));
        return BigDecimal.valueOf(clamped).setScale(2, RoundingMode.HALF_UP);
    }

    private int extractSpeed(String prompt, int fallback) {
        Matcher m = Pattern.compile("(\\d{2,3})\\s*(?:km/?h|kmph)").matcher(prompt.toLowerCase());
        if (m.find()) {
            try {
                int s = Integer.parseInt(m.group(1));
                if (s >= 30 && s <= 130) return s;
            } catch (Exception ignored) {}
        }
        return fallback;
    }

    private String resolveTerrain(String terrain, String prompt) {
        if (terrain != null) {
            String u = terrain.toUpperCase().trim();
            if (u.equals("HIGHWAY") || u.equals("FLAT") || u.equals("HILLY")) {
                return u;
            }
        }
        String p = prompt.toLowerCase();
        if (p.contains("hilly") || p.contains("mountain") || p.contains("ghat")) return "HILLY";
        if (p.contains("flat") || p.contains("city")) return "FLAT";
        return "HIGHWAY";
    }

    private String buildSummary(String intent, CityGeo origin, CityGeo dest,
                                BigDecimal battery, boolean acOn, int speed,
                                String terrain, boolean aiParsed) {
        String engine = aiParsed ? "Gemini NLP" : "Deterministic Rule Engine";
        if ("HIGHWAY_TRIP".equals(intent) && dest != null) {
            return String.format("[%s] Corridor: %s ➔ %s | Battery: %s%% | AC: %s | Speed: %d km/h | %s Terrain",
                    engine, origin.canonicalName(), dest.canonicalName(),
                    battery.stripTrailingZeros().toPlainString(),
                    acOn ? "ON" : "OFF", speed, terrain);
        }
        return String.format("[%s] Local Search at %s | Battery: %s%% | AC: %s",
                engine, origin.canonicalName(), battery.stripTrailingZeros().toPlainString(),
                acOn ? "ON" : "OFF");
    }
}
