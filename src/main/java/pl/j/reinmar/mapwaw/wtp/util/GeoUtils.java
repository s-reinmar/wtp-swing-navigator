package pl.j.reinmar.mapwaw.wtp.util;

import pl.j.reinmar.mapwaw.wtp.model.Stop;

import java.util.Collection;

/**
 * Klasa narzędziowa zawierająca algorytmy geodezyjne i geolokalizacyjne.
 */
public class GeoUtils {

    // Średni promień Ziemi w metrach (Model sferyczny WGS-84)
    private static final double EARTH_RADIUS_METERS = 6371000.0;

    /**
     * Oblicza odległość w metrach między dwoma punktami geograficznymi wzorem Haversine'a.
     */
    public static double calculateDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Oblicza azymut / kąt kierunku ruchu (bearing) w stopniach (0° - 360°).
     * Kąt wyliczany jest od północy zgodnie z ruchem wskazówek zegara.
     *
     * @param lat1 szerokość geograficzna punktu początkowego
     * @param lon1 długość geograficzna punktu początkowego
     * @param lat2 szerokość geograficzna punktu docelowego
     * @param lon2 długość geograficzna punktu docelowego
     * @return kąt kierunku jazdy w stopniach w przedziale [0.0, 360.0)
     */
    public static float calculateBearing(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaLambda = Math.toRadians(lon2 - lon1);

        double y = Math.sin(deltaLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) -
                Math.sin(phi1) * Math.cos(phi2) * Math.cos(deltaLambda);

        double theta = Math.atan2(y, x);
        double bearing = (Math.toDegrees(theta) + 360.0) % 360.0;

        return (float) bearing;
    }

    /**
     * Wyszukuje najbliższy przystanek względem podanych współrzędnych.
     */
    public static Stop findNearestStop(double latitude, double longitude, Collection<Stop> stops) {
        if (stops == null || stops.isEmpty()) {
            return null;
        }

        Stop nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (Stop stop : stops) {
            if (stop != null) {
                double distance = calculateDistanceMeters(latitude, longitude, stop.getLatitude(), stop.getLongitude());
                if (distance < minDistance) {
                    minDistance = distance;
                    nearest = stop;
                }
            }
        }
        return nearest;
    }
}