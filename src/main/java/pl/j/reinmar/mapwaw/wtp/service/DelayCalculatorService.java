package pl.j.reinmar.mapwaw.wtp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.j.reinmar.mapwaw.wtp.model.Departure;
import pl.j.reinmar.mapwaw.wtp.model.LiveVehiclePosition;
import pl.j.reinmar.mapwaw.wtp.model.Stop;
import pl.j.reinmar.mapwaw.wtp.repository.ScheduleRepository;
import pl.j.reinmar.mapwaw.wtp.util.GeoUtils;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Serwis domenowy odpowiedzialny za szacowanie opóźnienia pojazdu (w sekundach)
 * na podstawie aktualnej pozycji GPS oraz statycznego rozkładu jazdy dla danej brygady.
 */
public class DelayCalculatorService {

    private static final Logger logger = LoggerFactory.getLogger(DelayCalculatorService.class);
    private static final double MAXIMUM_STOP_PROXIMITY_METERS = 500.0; // Promień szukania najbliższego przystanku

    private final ScheduleRepository scheduleRepository;

    public DelayCalculatorService(ScheduleRepository scheduleRepository) {
        this.scheduleRepository = scheduleRepository;
    }

    /**
     * Oblicza opóźnienie w sekundach dla podanej pozycji pojazdu i aktualizuje pole delaySeconds.
     *
     * @param vehiclePosition pozycja pojazdu z API GPS
     * @return obliczone opóźnienie w sekundach (wartość dodatnia = opóźnienie, ujemna = przyspieszenie)
     */
    public int calculateAndUpdateDelay(LiveVehiclePosition vehiclePosition) {
        if (vehiclePosition == null || scheduleRepository == null) {
            return 0;
        }

        String lineNumber = vehiclePosition.getLineNumber();
        String brigade = vehiclePosition.getBrigade();

        if (lineNumber == null || lineNumber.isBlank()) {
            return 0;
        }

        // 1. Znalezienie najbliższego przystanku dla bieżących współrzędnych pojazdu
        Stop nearestStop = findNearestStopForVehicle(vehiclePosition);
        if (nearestStop == null) {
            return 0;
        }

        // 2. Pobranie rozkładowych odjazdów dla pary (linia + przystanek)
        List<Departure> departures = scheduleRepository.getDeparturesForLineAndStop(lineNumber, nearestStop.getId());
        if (departures.isEmpty()) {
            return 0;
        }

        // 3. Ustalenie czasu odebrania ramki GPS (czas lokalny w strefie Europe/Warsaw)
        LocalTime gpsTime = LocalTime.ofInstant(vehiclePosition.getLastUpdate(), ZoneId.of("Europe/Warsaw"));

        // 4. Dopasowanie odjazdu według numeru brygady i najbliższego czasu
        Departure matchedDeparture = findBestMatchingDeparture(departures, brigade, gpsTime);
        if (matchedDeparture == null) {
            return 0;
        }

        // 5. Obliczenie różnicy w sekundach: (Czas GPS - Czas Rozkładowy)
        int delaySeconds = (int) Duration.between(matchedDeparture.getDepartureTime(), gpsTime).getSeconds();

        // Aktualizacja obiektu domenowego
        vehiclePosition.setDelaySeconds(delaySeconds);

        logger.debug("Pojazd [linia: {}, brygada: {}, id: {}] przy przystanku {} -> opóźnienie: {} s",
                lineNumber, brigade, vehiclePosition.getVehicleId(), nearestStop.getName(), delaySeconds);

        return delaySeconds;
    }

    /**
     * Wyszukuje najbliższy przystanek w promieniu 500m dla danej pozycji GPS.
     */
    private Stop findNearestStopForVehicle(LiveVehiclePosition position) {
        double vehicleLat = position.getLatitude();
        double vehicleLon = position.getLongitude();

        // Odpytujemy indeks rozkładów o odjazdy dla danej linii, aby ograniczyć zestaw przystanków
        List<Departure> lineDepartures = scheduleRepository.getDeparturesForLine(position.getLineNumber());
        if (lineDepartures.isEmpty()) {
            return null;
        }

        Stop nearest = null;
        double minDistance = MAXIMUM_STOP_PROXIMITY_METERS;

        for (Departure dep : lineDepartures) {
            Stop stop = dep.getStop();
            if (stop != null) {
                double dist = GeoUtils.calculateDistanceMeters(vehicleLat, vehicleLon, stop.getLatitude(), stop.getLongitude());
                if (dist < minDistance) {
                    minDistance = dist;
                    nearest = stop;
                }
            }
        }
        return nearest;
    }

    /**
     * Wyszukuje odjazd najbardziej odpowiadający danej brygadzie oraz aktualnej godzinie.
     */
    private Departure findBestMatchingDeparture(List<Departure> departures, String brigade, LocalTime gpsTime) {
        Departure bestMatch = null;
        long minTimeDiffSeconds = Long.MAX_VALUE;

        for (Departure dep : departures) {
            // Filtrowanie po brygadzie (jeśli dostępna)
            if (brigade != null && !brigade.isBlank() && dep.getBrigade() != null) {
                if (!brigade.trim().equalsIgnoreCase(dep.getBrigade().trim())) {
                    continue;
                }
            }

            long diff = Math.abs(Duration.between(dep.getDepartureTime(), gpsTime).getSeconds());
            if (diff < minTimeDiffSeconds) {
                minTimeDiffSeconds = diff;
                bestMatch = dep;
            }
        }

        // Jeśli nie znaleziono dopasowania po brygadzie, szukamy najbliższego czasowo dowolnego odjazdu danej linii
        if (bestMatch == null && !departures.isEmpty()) {
            for (Departure dep : departures) {
                long diff = Math.abs(Duration.between(dep.getDepartureTime(), gpsTime).getSeconds());
                if (diff < minTimeDiffSeconds) {
                    minTimeDiffSeconds = diff;
                    bestMatch = dep;
                }
            }
        }

        return bestMatch;
    }
}