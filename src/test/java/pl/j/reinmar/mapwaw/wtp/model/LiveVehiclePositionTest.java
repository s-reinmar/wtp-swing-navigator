package pl.j.reinmar.mapwaw.wtp.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LiveVehiclePositionTest {

    @Test
    @DisplayName("Przechowuje i aktualizuje pozycję pojazdu w czasie rzeczywistym")
    void shouldStoreAndUpdateVehiclePosition() {
        Instant initialTime = Instant.parse("2024-01-01T08:30:00Z");
        LiveVehiclePosition position = new LiveVehiclePosition(
                "1234", "507", "04", 52.23, 21.01, 90.0f, initialTime, 30);

        assertEquals("1234", position.getVehicleId());
        assertEquals("507", position.getLineNumber());
        assertEquals("04", position.getBrigade());
        assertEquals(52.23, position.getLatitude(), 0.000001);
        assertEquals(21.01, position.getLongitude(), 0.000001);
        assertEquals(90.0f, position.getBearing(), 0.0001f);
        assertEquals(initialTime, position.getLastUpdate());
        assertEquals(30, position.getDelaySeconds());

        Instant updatedTime = Instant.parse("2024-01-01T08:31:00Z");
        position.setVehicleId("5678");
        position.setLineNumber("509");
        position.setBrigade("05");
        position.setLatitude(52.24);
        position.setLongitude(21.02);
        position.setBearing(180.0f);
        position.setLastUpdate(updatedTime);
        position.setDelaySeconds(-10);

        assertEquals("5678", position.getVehicleId());
        assertEquals("509", position.getLineNumber());
        assertEquals("05", position.getBrigade());
        assertEquals(52.24, position.getLatitude(), 0.000001);
        assertEquals(21.02, position.getLongitude(), 0.000001);
        assertEquals(180.0f, position.getBearing(), 0.0001f);
        assertEquals(updatedTime, position.getLastUpdate());
        assertEquals(-10, position.getDelaySeconds());
    }
}
