package pl.j.reinmar.mapwaw.wtp.repository;

import pl.j.reinmar.mapwaw.wtp.model.LiveVehiclePosition;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Współbieżna pamięć podręczna (Cache) dla pozycji GPS pojazdów w czasie rzeczywistym.
 * Wykorzystuje ConcurrentHashMap w celu zapewnienia bezpiecznego odczytu i zapisu z wielu wątków.
 */
public class RealtimeVehicleCache {

    // Klucz: numer boczny pojazdu (vehicleId), Wartość: encja LiveVehiclePosition
    private final Map<String, LiveVehiclePosition> positionsByVehicleId = new ConcurrentHashMap<>();

    /**
     * Aktualizuje lub dodaje pozycję pojazdu w cache.
     */
    public void updatePosition(LiveVehiclePosition position) {
        if (position != null && position.getVehicleId() != null) {
            positionsByVehicleId.put(position.getVehicleId(), position);
        }
    }

    /**
     * Zwraca pozycję konkretnego pojazdu po jego numerze bocznym.
     */
    public LiveVehiclePosition getPosition(String vehicleId) {
        return positionsByVehicleId.get(vehicleId);
    }

    /**
     * Zwraca listę wszystkich aktywnych pozycji pojazdów dla wskazanej linii transportowej.
     */
    public List<LiveVehiclePosition> getPositionsForLine(String lineNumber) {
        if (lineNumber == null || lineNumber.isBlank()) {
            return List.of();
        }
        return positionsByVehicleId.values().stream()
                .filter(v -> lineNumber.equalsIgnoreCase(v.getLineNumber()))
                .collect(Collectors.toList());
    }

    /**
     * Zwraca kolekcję wszystkich aktywnych pozycji pojazdów w systemie.
     */
    public Collection<LiveVehiclePosition> getAllPositions() {
        return positionsByVehicleId.values();
    }

    /**
     * Usuwa pozycję pojazdu z cache.
     */
    public void removePosition(String vehicleId) {
        positionsByVehicleId.remove(vehicleId);
    }

    /**
     * Czyści całą zawartość cache.
     */
    public void clear() {
        positionsByVehicleId.clear();
    }

    /**
     * Zwraca liczbę aktualnie śledzonych pojazdów.
     */
    public int size() {
        return positionsByVehicleId.size();
    }
}