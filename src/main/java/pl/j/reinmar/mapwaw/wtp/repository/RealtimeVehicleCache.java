package pl.j.reinmar.mapwaw.wtp.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.j.reinmar.mapwaw.wtp.model.LiveVehiclePosition;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Współbieżna pamięć podręczna (Cache) dla pozycji GPS pojazdów w czasie rzeczywistym.
 * Wykorzystuje ConcurrentHashMap oraz mechanizm wygasania (TTL - Time To Live)
 * do automatycznego usuwania przestarzałych i nieaktywnych pozycji z pamięci RAM.
 */
public class RealtimeVehicleCache {

    private static final Logger logger = LoggerFactory.getLogger(RealtimeVehicleCache.class);

    // Domyślny czas wygasania nieaktywnego pojazdu (np. 180 sekund / 3 minuty)
    private static final Duration DEFAULT_TTL = Duration.ofSeconds(180);

    // Mapowanie: vehicleId -> LiveVehiclePosition
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
     * Zwraca listę wszystkich aktywnych pozycji pojazdów dla wskazanej linii.
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
     * KROK 46: Czyszczenie przestarzałych wpisów (TTL Expiration).
     * Usuwa z pamięci podręcznej pojazdy, od których nie odebrano ramki GPS
     * przez czas dłuższy niż zdefiniowany TTL względem bieżącego czasu.
     *
     * @return liczba usuniętych nieaktywnych pojazdów
     */
    public int cleanExpiredPositions() {
        return cleanExpiredPositions(DEFAULT_TTL, Instant.now());
    }

    /**
     * Czyszczenie przestarzałych wpisów z własnym czasem TTL oraz czasem odniesienia (przydatne do testów).
     *
     * @param ttl           maksymalny dopuszczalny wiek ramki danych
     * @param referenceTime czas odniesienia (np. Instant.now())
     * @return liczba usuniętych nieaktywnych pojazdów
     */
    public int cleanExpiredPositions(Duration ttl, Instant referenceTime) {
        if (ttl == null || referenceTime == null) {
            return 0;
        }

        int removedCount = 0;
        for (Map.Entry<String, LiveVehiclePosition> entry : positionsByVehicleId.entrySet()) {
            LiveVehiclePosition position = entry.getValue();
            if (position == null || position.getLastUpdate() == null) {
                positionsByVehicleId.remove(entry.getKey());
                removedCount++;
                continue;
            }

            // Obliczenie różnicy wieku ramki GPS
            Duration age = Duration.between(position.getLastUpdate(), referenceTime);
            if (age.compareTo(ttl) > 0) {
                positionsByVehicleId.remove(entry.getKey());
                removedCount++;
                logger.debug("Usunięto z cache wygasły pojazd {} (ostatnia aktualizacja: {} s temu)",
                        entry.getKey(), age.getSeconds());
            }
        }

        if (removedCount > 0) {
            logger.info("Czyszczenie TTL: Usunięto {} nieaktywnych/wygasłych pojazdów z cache.", removedCount);
        }

        return removedCount;
    }

    /**
     * Usuwa pojedynczą pozycję pojazdu z cache.
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