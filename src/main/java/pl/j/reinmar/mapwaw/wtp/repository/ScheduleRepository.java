package pl.j.reinmar.mapwaw.wtp.repository;

import pl.j.reinmar.mapwaw.wtp.model.Departure;
import pl.j.reinmar.mapwaw.wtp.model.Line;
import pl.j.reinmar.mapwaw.wtp.model.Stop;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Pamięciowy magazyn danych (Repository) dla przystanków, linii oraz rozkładowych odjazdów.
 * Zawiera zaawansowane indeksy do wiązania odjazdów z liniami i przystankami.
 */
public class ScheduleRepository {

    // Indeksy w pamięci RAM
    private final Map<String, Stop> stopsById = new ConcurrentHashMap<>();
    private final Map<String, Stop> stopsByCode = new ConcurrentHashMap<>();
    private final Map<String, List<Stop>> stopsByName = new ConcurrentHashMap<>();

    private final Map<String, Line> linesByNumber = new ConcurrentHashMap<>();

    // Indeksy odjazdów
    private final Map<String, List<Departure>> departuresByStopId = new ConcurrentHashMap<>();
    private final Map<String, List<Departure>> departuresByLineNumber = new ConcurrentHashMap<>();     // Indeks po linii
    private final Map<String, List<Departure>> departuresByLineAndStop = new ConcurrentHashMap<>();   // Indeks złożony (linia + przystanek)

    public void addStop(Stop stop) {
        if (stop != null) {
            if (stop.getId() != null) {
                stopsById.put(stop.getId(), stop);
            }
            if (stop.getCode() != null) {
                stopsByCode.put(stop.getCode(), stop);
            }
            if (stop.getName() != null) {
                String normalizedName = stop.getName().toLowerCase().trim();
                stopsByName.computeIfAbsent(normalizedName, k -> new ArrayList<>()).add(stop);
            }
        }
    }

    public Stop findStopById(String id) {
        return stopsById.get(id);
    }

    public Stop findStopByCode(String code) {
        return stopsByCode.get(code);
    }

    public List<Stop> findStopsByName(String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        String search = name.toLowerCase().trim();
        List<Stop> exactMatches = stopsByName.get(search);
        if (exactMatches != null && !exactMatches.isEmpty()) {
            return new ArrayList<>(exactMatches);
        }

        return stopsById.values().stream()
                .filter(s -> s.getName() != null && s.getName().toLowerCase().contains(search))
                .collect(Collectors.toList());
    }

    public void addLine(Line line) {
        if (line != null && line.getLineNumber() != null) {
            linesByNumber.put(line.getLineNumber(), line);
        }
    }

    public Line findLineByNumber(String lineNumber) {
        return linesByNumber.get(lineNumber);
    }

    /**
     * Rejestruje odjazd rozkładowy i automatycznie buduje indeksy powiązań z liniami i przystankami.
     */
    public void addDeparture(Departure departure) {
        if (departure != null && departure.getStop() != null && departure.getLine() != null) {
            String stopId = departure.getStop().getId();
            String lineNumber = departure.getLine().getLineNumber();

            // 1. Indeks po samym przystanku
            departuresByStopId.computeIfAbsent(stopId, k -> new ArrayList<>()).add(departure);

            // 2. Indeks po numerze linii
            departuresByLineNumber.computeIfAbsent(lineNumber, k -> new ArrayList<>()).add(departure);

            // 3. Indeks złożony: linia + przystanek
            String compositeKey = lineNumber + "_" + stopId;
            departuresByLineAndStop.computeIfAbsent(compositeKey, k -> new ArrayList<>()).add(departure);
        }
    }

    /**
     * Zwraca listę odjazdów dla wskazanego przystanku.
     */
    public List<Departure> getDeparturesForStop(String stopId) {
        return departuresByStopId.getOrDefault(stopId, List.of());
    }

    /**
     * Zwraca listę odjazdów dla wskazanej linii transportowej.
     */
    public List<Departure> getDeparturesForLine(String lineNumber) {
        return departuresByLineNumber.getOrDefault(lineNumber, List.of());
    }

    /**
     * Zwraca listę odjazdów dla konkretnej linii z wybranego przystanku.
     */
    public List<Departure> getDeparturesForLineAndStop(String lineNumber, String stopId) {
        String compositeKey = lineNumber + "_" + stopId;
        return departuresByLineAndStop.getOrDefault(compositeKey, List.of());
    }

    public int getStopsCount() {
        return stopsById.size();
    }

    public int getLinesCount() {
        return linesByNumber.size();
    }
}