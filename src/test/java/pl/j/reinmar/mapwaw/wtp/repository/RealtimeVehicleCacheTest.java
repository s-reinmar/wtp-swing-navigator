package pl.j.reinmar.mapwaw.wtp.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.j.reinmar.mapwaw.wtp.model.LiveVehiclePosition;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealtimeVehicleCacheTest {

    private RealtimeVehicleCache cache;

    private LiveVehiclePosition position(String vehicleId, String lineNumber) {
        return new LiveVehiclePosition(vehicleId, lineNumber, "04",
                52.229712, 21.012234, 90.0f, Instant.parse("2024-01-01T08:30:00Z"), 0);
    }

    @BeforeEach
    void setUp() {
        cache = new RealtimeVehicleCache();
    }

    @Test
    @DisplayName("Dodaje i aktualizuje pozycję pod tym samym identyfikatorem pojazdu")
    void shouldAddAndReplacePosition() {
        LiveVehiclePosition initialPosition = position("1234", "507");
        LiveVehiclePosition updatedPosition = position("1234", "509");

        cache.updatePosition(initialPosition);
        cache.updatePosition(updatedPosition);

        assertSame(updatedPosition, cache.getPosition("1234"));
        assertEquals(1, cache.size());
        assertEquals(List.of(updatedPosition), List.copyOf(cache.getAllPositions()));
    }

    @Test
    @DisplayName("Zwraca pozycje linii bez rozróżniania wielkości liter")
    void shouldFindPositionsForLineCaseInsensitively() {
        LiveVehiclePosition first = position("1234", "M1");
        LiveVehiclePosition second = position("5678", "M1");
        cache.updatePosition(first);
        cache.updatePosition(second);
        cache.updatePosition(position("9999", "509"));

        List<LiveVehiclePosition> positions = cache.getPositionsForLine("m1");

        assertEquals(2, positions.size());
        assertTrue(positions.contains(first));
        assertTrue(positions.contains(second));
        assertTrue(cache.getPositionsForLine(null).isEmpty());
        assertTrue(cache.getPositionsForLine(" ").isEmpty());
    }

    @Test
    @DisplayName("Usuwa pojedynczą pozycję i czyści cache")
    void shouldRemovePositionAndClearCache() {
        cache.updatePosition(position("1234", "507"));
        cache.updatePosition(position("5678", "509"));

        cache.removePosition("1234");
        assertEquals(1, cache.size());
        assertNull(cache.getPosition("1234"));

        cache.clear();
        assertEquals(0, cache.size());
        assertTrue(cache.getAllPositions().isEmpty());
    }

    @Test
    @DisplayName("Ignoruje pozycję null")
    void shouldIgnoreNullPosition() {
        cache.updatePosition(null);

        assertEquals(0, cache.size());
    }
}
