package com.voltgrid.voltgrid.ai.util;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class IndianCityCoordinates {

    public record CityGeo(String canonicalName, BigDecimal lat, BigDecimal lng) {}

    private static final Map<String, CityGeo> CITIES;

    static {
        Map<String, CityGeo> map = new HashMap<>();

        // Major hubs and charging corridor nodes
        put(map, "vadodara", "Vadodara", "22.307200", "73.181200");
        put(map, "baroda", "Vadodara", "22.307200", "73.181200");

        put(map, "mumbai", "Mumbai", "19.076000", "72.877700");
        put(map, "bombay", "Mumbai", "19.076000", "72.877700");

        put(map, "ahmedabad", "Ahmedabad", "23.022500", "72.571400");
        put(map, "amdavad", "Ahmedabad", "23.022500", "72.571400");

        put(map, "pune", "Pune", "18.520400", "73.856700");
        put(map, "poona", "Pune", "18.520400", "73.856700");

        put(map, "surat", "Surat", "21.170200", "72.831100");
        put(map, "bharuch", "Bharuch", "21.705100", "72.995900");
        put(map, "vapi", "Vapi", "20.389300", "72.910600");
        put(map, "lonavala", "Lonavala", "18.755700", "73.409100");

        put(map, "delhi", "Delhi", "28.613900", "77.209000");
        put(map, "new delhi", "Delhi", "28.613900", "77.209000");

        put(map, "bengaluru", "Bengaluru", "12.971600", "77.594600");
        put(map, "bangalore", "Bengaluru", "12.971600", "77.594600");

        put(map, "leh", "Leh", "34.152600", "77.577100");
        put(map, "ladakh", "Leh", "34.152600", "77.577100");

        CITIES = Collections.unmodifiableMap(map);
    }

    private static void put(Map<String, CityGeo> map, String key, String name, String lat, String lng) {
        map.put(key.toLowerCase().trim(), new CityGeo(name, new BigDecimal(lat), new BigDecimal(lng)));
    }

    private IndianCityCoordinates() {}

    public static Optional<CityGeo> find(String cityName) {
        if (cityName == null || cityName.isBlank()) {
            return Optional.empty();
        }
        String clean = cityName.toLowerCase().trim();
        return Optional.ofNullable(CITIES.get(clean));
    }

    public static Optional<CityGeo> findInText(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String lower = text.toLowerCase();
        for (Map.Entry<String, CityGeo> entry : CITIES.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }
}
