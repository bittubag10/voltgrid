package com.voltgrid.voltgrid.util;

/**
 * Geo helpers: great-circle distance (Haversine) and a bounding box for cheap DB pre-filtering.
 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private GeoUtils() {
    }

    public record BoundingBox(double minLat, double maxLat, double minLng, double maxLng) {
    }

    /** Great-circle distance between two points, in kilometres. */
    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad) * Math.pow(Math.sin(dLng / 2), 2);

        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /**
     * Smallest lat/lng rectangle that contains every point within radiusKm of the centre.
     * Limitation: does not handle wrap-around at the 180th meridian (fine for India).
     */
    public static BoundingBox boundingBox(double lat, double lng, double radiusKm) {
        double angularRadius = radiusKm / EARTH_RADIUS_KM;
        double latDelta = Math.toDegrees(angularRadius);

        double minLat = lat - latDelta;
        double maxLat = lat + latDelta;

        // Near a pole the longitude range covers everything
        if (minLat <= -90.0 || maxLat >= 90.0) {
            return new BoundingBox(Math.max(minLat, -90.0), Math.min(maxLat, 90.0), -180.0, 180.0);
        }

        double lngDelta = Math.toDegrees(
                Math.asin(Math.sin(angularRadius) / Math.cos(Math.toRadians(lat))));

        return new BoundingBox(
                minLat,
                maxLat,
                Math.max(lng - lngDelta, -180.0),
                Math.min(lng + lngDelta, 180.0)
        );
    }
}