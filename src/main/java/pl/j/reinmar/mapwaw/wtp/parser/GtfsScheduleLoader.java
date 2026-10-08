package pl.j.reinmar.mapwaw.wtp.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.j.reinmar.mapwaw.wtp.model.DayType;
import pl.j.reinmar.mapwaw.wtp.model.Departure;
import pl.j.reinmar.mapwaw.wtp.model.Line;
import pl.j.reinmar.mapwaw.wtp.model.RouteStop;
import pl.j.reinmar.mapwaw.wtp.model.RouteVariant;
import pl.j.reinmar.mapwaw.wtp.model.Stop;
import pl.j.reinmar.mapwaw.wtp.model.TransportType;
import pl.j.reinmar.mapwaw.wtp.repository.ScheduleRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Ładuje rozkład GTFS (stops, routes, trips, calendar, stop_times) do repozytorium
 * oraz buduje unikalne warianty tras dla silnika routingu. Uwzględniane są wyłącznie
 * kursy obowiązujące w najbliższym tygodniu, a kursy o identycznym przebiegu
 * są scalane w jeden wariant.
 */
public class GtfsScheduleLoader {

    private static final Logger logger = LoggerFactory.getLogger(GtfsScheduleLoader.class);
    private static final DateTimeFormatter GTFS_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int WINDOW_DAYS = 7;

    private record TripInfo(String routeId, String headsign, DayType dayType) {
    }

    private record Pattern(String id, RouteVariant variant) {
    }

    public List<RouteVariant> load(Path dataDir, ScheduleRepository repository,
                                   Consumer<String> status) throws IOException {
        status.accept("Wczytywanie przystanków...");
        loadStops(dataDir.resolve("stops.txt"), repository);

        status.accept("Wczytywanie linii...");
        Map<String, Line> linesByRoute = loadRoutes(dataDir.resolve("routes.txt"), repository);

        Map<String, DayType> serviceDayTypes = loadActiveServices(dataDir.resolve("calendar.txt"));
        status.accept("Wczytywanie kursów...");
        Map<String, TripInfo> trips = loadTrips(dataDir.resolve("trips.txt"), serviceDayTypes);

        status.accept("Wczytywanie rozkładu jazdy (może potrwać)...");
        List<RouteVariant> variants = loadStopTimes(dataDir.resolve("stop_times.txt"),
                repository, trips, linesByRoute);
        logger.info("Załadowano: przystanki={}, linie={}, kursy={}, warianty={}",
                repository.getStopsCount(), repository.getLinesCount(), trips.size(), variants.size());
        return variants;
    }

    private void loadStops(Path file, ScheduleRepository repository) throws IOException {
        forEachRow(file, (header, row) -> {
            String id = row(header, row, "stop_id");
            double lat = parseDouble(row(header, row, "stop_lat"));
            double lon = parseDouble(row(header, row, "stop_lon"));
            if (id.isEmpty() || !ScheduleFormatValidator.isValidWarsawCoordinates(lat, lon)) {
                return;
            }
            repository.addStop(new Stop(ScheduleFormatValidator.intern(id),
                    ScheduleFormatValidator.intern(row(header, row, "stop_name")),
                    ScheduleFormatValidator.intern(row(header, row, "stop_code")),
                    lat, lon, false));
        });
    }

    private Map<String, Line> loadRoutes(Path file, ScheduleRepository repository) throws IOException {
        Map<String, Line> lines = new HashMap<>();
        forEachRow(file, (header, row) -> {
            String routeId = row(header, row, "route_id");
            String number = row(header, row, "route_short_name");
            if (routeId.isEmpty() || number.isEmpty()) {
                return;
            }
            Line line = new Line(ScheduleFormatValidator.intern(number),
                    transportType(number, row(header, row, "route_type")), "WTP Warszawa");
            repository.addLine(routeId, line);
            lines.put(routeId, line);
        });
        return lines;
    }

    private static TransportType transportType(String number, String routeType) {
        if (number.toUpperCase().startsWith("M")) {
            return TransportType.METRO;
        }
        return switch (routeType) {
            case "0" -> TransportType.TRAM;
            case "1" -> TransportType.METRO;
            default -> TransportType.BUS;
        };
    }

    private Map<String, DayType> loadActiveServices(Path file) throws IOException {
        LocalDate today = LocalDate.now();
        Map<String, DayType> services = new HashMap<>();
        forEachRow(file, (header, row) -> {
            try {
                LocalDate start = LocalDate.parse(row(header, row, "start_date"), GTFS_DATE);
                LocalDate end = LocalDate.parse(row(header, row, "end_date"), GTFS_DATE);
                for (int offset = 0; offset < WINDOW_DAYS; offset++) {
                    LocalDate date = today.plusDays(offset);
                    if (!date.isBefore(start) && !date.isAfter(end)
                            && "1".equals(row(header, row, dayColumn(date)))) {
                        services.putIfAbsent(row(header, row, "service_id"), dayType(date));
                        return;
                    }
                }
            } catch (RuntimeException e) {
                logger.debug("Pominięto wpis kalendarza: {}", e.getMessage());
            }
        });
        if (services.isEmpty()) {
            logger.warn("Brak aktywnych serwisów w calendar.txt - używam wszystkich kursów jako dni powszednich.");
        }
        return services;
    }

    private static String dayColumn(LocalDate date) {
        return date.getDayOfWeek().name().toLowerCase();
    }

    private static DayType dayType(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case SATURDAY -> DayType.SATURDAY;
            case SUNDAY -> DayType.SUNDAY;
            default -> DayType.WEEKDAY;
        };
    }

    private Map<String, TripInfo> loadTrips(Path file, Map<String, DayType> services) throws IOException {
        Map<String, TripInfo> trips = new HashMap<>();
        forEachRow(file, (header, row) -> {
            DayType type = services.get(row(header, row, "service_id"));
            if (type != null || services.isEmpty()) {
                trips.put(row(header, row, "trip_id"), new TripInfo(
                        row(header, row, "route_id"),
                        ScheduleFormatValidator.intern(row(header, row, "trip_headsign")),
                        type != null ? type : DayType.WEEKDAY));
            }
        });
        return trips;
    }

    private List<RouteVariant> loadStopTimes(Path file, ScheduleRepository repository,
                                             Map<String, TripInfo> trips,
                                             Map<String, Line> linesByRoute) throws IOException {
        Map<String, Pattern> patterns = new HashMap<>();
        List<String> stopIds = new ArrayList<>();
        List<Integer> times = new ArrayList<>();
        String currentTrip = null;

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String[] header = splitCsv(ScheduleFormatValidator.sanitizeLine(reader.readLine()));
            int tripCol = indexOf(header, "trip_id");
            int depCol = indexOf(header, "departure_time");
            int arrCol = indexOf(header, "arrival_time");
            int stopCol = indexOf(header, "stop_id");
            String line;
            while ((line = reader.readLine()) != null) {
                String[] row = splitCsv(line);
                if (row.length <= Math.max(Math.max(tripCol, stopCol), Math.max(depCol, arrCol))) {
                    continue;
                }
                String tripId = row[tripCol];
                if (!tripId.equals(currentTrip)) {
                    finishTrip(currentTrip, trips, linesByRoute, repository, patterns, stopIds, times);
                    currentTrip = trips.containsKey(tripId) ? tripId : null;
                    stopIds.clear();
                    times.clear();
                }
                if (currentTrip == null) {
                    continue;
                }
                String time = !row[depCol].isEmpty() ? row[depCol] : row[arrCol];
                stopIds.add(row[stopCol]);
                times.add(parseSeconds(time));
            }
            finishTrip(currentTrip, trips, linesByRoute, repository, patterns, stopIds, times);
        }

        List<RouteVariant> variants = new ArrayList<>(patterns.size());
        patterns.values().forEach(pattern -> variants.add(pattern.variant()));
        return variants;
    }

    private void finishTrip(String tripId, Map<String, TripInfo> trips, Map<String, Line> linesByRoute,
                            ScheduleRepository repository, Map<String, Pattern> patterns,
                            List<String> stopIds, List<Integer> times) {
        if (tripId == null || stopIds.size() < 2) {
            return;
        }
        TripInfo trip = trips.get(tripId);
        Line line = linesByRoute.get(trip.routeId());
        if (line == null) {
            return;
        }

        String key = trip.routeId() + '|' + trip.headsign() + '|' + String.join(",", stopIds);
        Pattern pattern = patterns.get(key);
        if (pattern == null) {
            int start = times.get(0);
            List<RouteStop> routeStops = new ArrayList<>(stopIds.size());
            for (int i = 0; i < stopIds.size(); i++) {
                Stop stop = repository.findStopById(stopIds.get(i));
                if (stop != null) {
                    routeStops.add(new RouteStop(stop, i, Math.max(0, times.get(i) - start)));
                }
            }
            pattern = new Pattern(tripId, new RouteVariant(tripId, line.getLineNumber(),
                    trip.headsign(), routeStops));
            patterns.put(key, pattern);
        }

        for (int i = 0; i < stopIds.size(); i++) {
            Stop stop = repository.findStopById(stopIds.get(i));
            if (stop != null) {
                repository.addDeparture(new Departure(pattern.id(), line, stop,
                        LocalTime.ofSecondOfDay(Math.floorMod(times.get(i), 86400)),
                        trip.dayType(), "01"));
            }
        }
    }

    private interface RowHandler {
        void accept(String[] header, String[] row);
    }

    private void forEachRow(Path file, RowHandler handler) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return;
            }
            String[] header = splitCsv(ScheduleFormatValidator.sanitizeLine(headerLine));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    handler.accept(header, splitCsv(line));
                }
            }
        }
    }

    private static String row(String[] header, String[] row, String column) {
        int index = indexOf(header, column);
        return index >= 0 && index < row.length ? row[index] : "";
    }

    private static int indexOf(String[] header, String column) {
        for (int i = 0; i < header.length; i++) {
            if (header[i].equals(column)) {
                return i;
            }
        }
        return -1;
    }

    private static String[] splitCsv(String line) {
        List<String> result = new ArrayList<>(12);
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString().trim());
        return result.toArray(new String[0]);
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static int parseSeconds(String time) {
        String[] parts = time.split(":");
        if (parts.length != 3) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[0].trim()) * 3600
                    + Integer.parseInt(parts[1].trim()) * 60 + Integer.parseInt(parts[2].trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
