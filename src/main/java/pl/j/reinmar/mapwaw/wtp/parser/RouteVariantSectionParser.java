package pl.j.reinmar.mapwaw.wtp.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.j.reinmar.mapwaw.wtp.model.Line;
import pl.j.reinmar.mapwaw.wtp.model.RouteStop;
import pl.j.reinmar.mapwaw.wtp.model.RouteVariant;
import pl.j.reinmar.mapwaw.wtp.model.Stop;
import pl.j.reinmar.mapwaw.wtp.repository.ScheduleRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser odpowiedzialny za przetwarzanie wariantów tras i kolejności przystanków
 * na podstawie plików trips.txt oraz stop_times.txt (standard GTFS).
 */
public class RouteVariantSectionParser {

    private static final Logger logger = LoggerFactory.getLogger(RouteVariantSectionParser.class);

    // Tymczasowe bufory do grupowania przystanków w ramach kursu/wariantu
    private final Map<String, List<RouteStopBuilderTemp>> tripStopsMap = new HashMap<>();
    private final Map<String, TripInfoTemp> tripInfoMap = new HashMap<>();
    private final Map<String, Integer> tripStartTimeSeconds = new HashMap<>();

    /**
     * Tymczasowa klasa pomocnicza do budowania kroku trasy.
     */
    public static class RouteStopBuilderTemp {
        public String stopId;
        public int sequenceOrder;
        public int travelTimeSec;

        public RouteStopBuilderTemp(String stopId, int sequenceOrder, int travelTimeSec) {
            this.stopId = stopId;
            this.sequenceOrder = sequenceOrder;
            this.travelTimeSec = travelTimeSec;
        }
    }

    /**
     * Tymczasowa klasa pomocnicza dla informacji o kursie/wariancie.
     */
    public static class TripInfoTemp {
        public String tripId;
        public String routeId;
        public String headsign;

        public TripInfoTemp(String tripId, String routeId, String headsign) {
            this.tripId = tripId;
            this.routeId = routeId;
            this.headsign = headsign;
        }
    }

    /**
     * Parsuje linię z pliku trips.txt.
     */
    public boolean parseTripLine(String line) {
        if (line == null || line.isBlank() || line.startsWith("trip_id") || line.startsWith("\"trip_id\"")) {
            return false;
        }
        try {
            String[] parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
            if (parts.length < 4) return false;

            // Standard GTFS trips.txt: route_id, service_id, trip_id, trip_headsign, ...
            String rId = cleanValue(parts[0]);
            String tripId = cleanValue(parts[2]);
            String headsign = cleanValue(parts[3]);

            tripInfoMap.put(tripId, new TripInfoTemp(tripId, rId, headsign));
            return true;
        } catch (Exception e) {
            logger.debug("Błąd parsowania trips.txt: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Parsuje linię z pliku stop_times.txt w celu ustalenia kolejności przystanków.
     */
    public boolean parseStopTimeLine(String line) {
        if (line == null || line.isBlank() || line.startsWith("trip_id") || line.startsWith("\"trip_id\"")) {
            return false;
        }
        try {
            String[] parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
            if (parts.length < 5) return false;

            // Standard GTFS stop_times.txt: trip_id, arrival_time, departure_time, stop_id, stop_sequence, ...
            String tripId = cleanValue(parts[0]);
            String arrivalTime = cleanValue(parts[1]);
            String departureTime = cleanValue(parts[2]);
            String stopId = cleanValue(parts[3]);
            int sequence = Integer.parseInt(cleanValue(parts[4]));
            int stopTimeSeconds = parseGtfsTimeSeconds(
                    !arrivalTime.isBlank() ? arrivalTime : departureTime);
            int tripStartSeconds = tripStartTimeSeconds.computeIfAbsent(tripId,
                    ignored -> parseGtfsTimeSeconds(
                            !departureTime.isBlank() ? departureTime : arrivalTime));
            int travelTimeSec = Math.max(0, stopTimeSeconds - tripStartSeconds);

            tripStopsMap.computeIfAbsent(tripId, k -> new ArrayList<>())
                    .add(new RouteStopBuilderTemp(stopId, sequence, travelTimeSec));
            return true;
        } catch (Exception e) {
            logger.debug("Błąd parsowania stop_times.txt: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Finalizuje budowanie wariantów tras i przypisuje je do repozytorium.
     */
    public void buildAndStoreVariants(ScheduleRepository repository) {
        buildVariants(repository);
    }

    /**
     * Buduje warianty tras i zwraca je do wykorzystania przez silnik routingu.
     */
    public List<RouteVariant> buildVariants(ScheduleRepository repository) {
        logger.info("Budowanie wariantów tras na podstawie {} kursów...", tripInfoMap.size());
        List<RouteVariant> variants = new ArrayList<>();

        for (Map.Entry<String, TripInfoTemp> entry : tripInfoMap.entrySet()) {
            String tripId = entry.getKey();
            TripInfoTemp trip = entry.getValue();

            List<RouteStopBuilderTemp> rawStops = tripStopsMap.get(tripId);
            if (rawStops == null || rawStops.isEmpty()) {
                continue;
            }

            // Sortowanie po kolejności (sequenceOrder)[cite: 12]
            rawStops.sort((a, b) -> Integer.compare(a.sequenceOrder, b.sequenceOrder));

            List<RouteStop> routeStops = new ArrayList<>();
            for (RouteStopBuilderTemp rsTemp : rawStops) {
                Stop stop = repository.findStopById(rsTemp.stopId);
                if (stop != null) {
                    routeStops.add(new RouteStop(stop, rsTemp.sequenceOrder, rsTemp.travelTimeSec));
                }
            }

            if (!routeStops.isEmpty()) {
                Line line = repository.findLineByRouteId(trip.routeId);
                String lineNumber = line != null ? line.getLineNumber() : trip.routeId;
                RouteVariant variant = new RouteVariant(tripId, lineNumber, trip.headsign, routeStops);
                variants.add(variant);
            }
        }
        logger.info("Zakończono budowanie wariantów tras.");
        return List.copyOf(variants);
    }

    private String cleanValue(String raw) {
        if (raw == null) return "";
        String cleaned = raw.trim();
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() >= 2) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned.trim();
    }

    private int parseGtfsTimeSeconds(String rawTime) {
        if (rawTime == null || rawTime.isBlank()) {
            return 0;
        }
        String[] parts = rawTime.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Nieprawidłowy czas GTFS: " + rawTime);
        }
        int hours = Integer.parseInt(parts[0]);
        int minutes = Integer.parseInt(parts[1]);
        int seconds = Integer.parseInt(parts[2]);
        if (hours < 0 || minutes < 0 || minutes > 59 || seconds < 0 || seconds > 59) {
            throw new IllegalArgumentException("Nieprawidłowy czas GTFS: " + rawTime);
        }
        return hours * 3600 + minutes * 60 + seconds;
    }
}