package pl.j.reinmar.mapwaw.wtp.util;

import pl.j.reinmar.mapwaw.wtp.model.Stop;

import java.util.Collection;

/**
 * Klasa narzędziowa zawierająca algorytmy geodezyjne i geolokalizacyjne.
 * Główną funkcjonalnością jest obliczanie odległości między współrzędnymi GPS
 * za pomocą wzoru Haversine'a.
 */
public class GeoUtils {

    // Średni promień Ziemi w metrach (Model sferyczny WGS-84)
    private static final double EARTH_RADIUS_METERS = 6371000.0;

    /**
     * Oblicza odległość w metrach między dwoma punktami geograficznymi
     * na podstawie ich współrzędnych (lat, lon) przy użyciu wzoru Haversine'a.
     *
     * @param lat1 szerokość geograficzna punktu 1 (w stopniach)
     * @param lon1 długość geograficzna punktu 1 (w stopniach)
     * @param lat2 szerokość geograficzna punktu 2 (w stopniach)
     * @param lon2 długość geograficzna punktu 2 (w stopniach)
     * @return odległość w metrach
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
     * Wyszukuje najbliższy przystanek względem podanych współrzędnych geograficznych.
     *
     * @param latitude  szerokość geograficzna punktu odniesienia
     * @param longitude długość geograficzna punktu odniesienia
     * @param stops     kolekcja przystanków do przeszukania
     * @return najbliższy obiekt Stop lub null, jeśli kolekcja jest pusta
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