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
 * Wykorzystuje bezpieczne współbieżnie mapy (ConcurrentHashMap) dla wątków tła i Swing EDT.
 */
public class ScheduleRepository {

    // Indeksy w pamięci RAM
    private final Map<String, Stop> stopsById = new ConcurrentHashMap<>();
    private final Map<String, Line> linesByNumber = new ConcurrentHashMap<>();
    private final Map<String, List<Departure>> departuresByStopId = new ConcurrentHashMap<>();

    /**
     * Dodaje przystanek do magazynu.
     */
    public void addStop(Stop stop) {
        if (stop != null && stop.getId() != null) {
            stopsById.put(stop.getId(), stop);
        }
    }

    /**
     * Wyszukuje przystanek po unikalnym identyfikatorze
     */
    public Stop findStopById(String id) {
        return stopsById.get(id);
    }

    /**
     * Wyszukuje przystanki pasujące nazwą zespołu przystankowego (np. "Centrum")
     */
    public List<Stop> findStopsByName(String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        String search = name.toLowerCase().trim();
        return stopsById.values().stream()
                .filter(s -> s.getName() != null && s.getName().toLowerCase().contains(search))
                .collect(Collectors.toList());
    }

    /**
     * Dodaje linię transportową do magazynu.
     */
    public void addLine(Line line) {
        if (line != null && line.getLineNumber() != null) {
            linesByNumber.put(line.getLineNumber(), line);
        }
    }

    /**
     * Wyszukuje linię po numerze (np. "507", "17")
     */
    public Line findLineByNumber(String lineNumber) {
        return linesByNumber.get(lineNumber);
    }

    /**
     * Rejestruje odjazd rozkładowy w magazynie.
     */
    public void addDeparture(Departure departure) {
        if (departure != null && departure.getStop() != null) {
            String stopId = departure.getStop().getId();
            departuresByStopId.computeIfAbsent(stopId, k -> new ArrayList<>()).add(departure);
        }
    }

    /**
     * Zwraca listę odjazdów dla wskazanego przystanku.
     */
    public List<Departure> getDeparturesForStop(String stopId) {
        return departuresByStopId.getOrDefault(stopId, List.of());
    }

    /**
     * Zwraca całkowitą liczbę załadowanych przystanków.
     */
    public int getStopsCount() {
        return stopsById.size();
    }

    /**
     * Zwraca całkowitą liczbę załadowanych linii.
     */
    public int getLinesCount() {
        return linesByNumber.size();
    }
}