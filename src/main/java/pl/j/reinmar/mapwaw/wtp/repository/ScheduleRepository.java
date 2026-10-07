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
 * Wykorzystuje struktury HashMap / ConcurrentHashMap dla szybkiego indeksowania i wyszukiwania po ID, kodzie i nazwie.
 */
public class ScheduleRepository {

    // Indeksy w pamięci RAM
    private final Map<String, Stop> stopsById = new ConcurrentHashMap<>();
    private final Map<String, Stop> stopsByCode = new ConcurrentHashMap<>();            // Indeksowanie po kodzie słupka (np. "01")
    private final Map<String, List<Stop>> stopsByName = new ConcurrentHashMap<>();    // Indeksowanie po nazwie zespołu (np. "Centrum")

    private final Map<String, Line> linesByNumber = new ConcurrentHashMap<>();
    private final Map<String, List<Departure>> departuresByStopId = new ConcurrentHashMap<>();

    /**
     * Dodaje przystanek do magazynu i automatycznie buduje indeksy wyszukiwania.
     */
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

    /**
     * Wyszukuje przystanek po unikalnym identyfikatorze[cite: 13].
     */
    public Stop findStopById(String id) {
        return stopsById.get(id);
    }

    /**
     * Wyszukuje przystanek bezpośrednio po kodzie słupka w strukturze HashMap.
     */
    public Stop findStopByCode(String code) {
        return stopsByCode.get(code);
    }

    /**
     * Wyszukuje przystanki pasujące nazwą zespołu przystankowego (np. "Centrum").
     * Wykorzystuje szybki lookup po mapie lub dopasowanie podciągów.
     */
    public List<Stop> findStopsByName(String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        String search = name.toLowerCase().trim();

        // Sprawdź dokładne trafienie w indeksie nazw
        List<Stop> exactMatches = stopsByName.get(search);
        if (exactMatches != null && !exactMatches.isEmpty()) {
            return new ArrayList<>(exactMatches);
        }

        // Wyszukiwanie częściowe (zawierające frazę)
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