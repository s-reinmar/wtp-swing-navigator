package pl.j.reinmar.mapwaw.wtp.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StopTest {

    @Test
    @DisplayName("Przechowuje i aktualizuje dane przystanku")
    void shouldStoreAndUpdateStopData() {
        Stop stop = new Stop("700901", "Centrum", "01", 52.23, 21.01, true);

        assertEquals("700901", stop.getId());
        assertEquals("Centrum", stop.getName());
        assertEquals("01", stop.getCode());
        assertEquals(52.23, stop.getLatitude(), 0.000001);
        assertEquals(21.01, stop.getLongitude(), 0.000001);
        assertTrue(stop.isWheelchairAccessible());

        stop.setId("700902");
        stop.setName("Dworzec");
        stop.setCode("02");
        stop.setLatitude(52.24);
        stop.setLongitude(21.02);
        stop.setWheelchairAccessible(false);

        assertEquals("700902", stop.getId());
        assertEquals("Dworzec", stop.getName());
        assertEquals("02", stop.getCode());
        assertEquals(52.24, stop.getLatitude(), 0.000001);
        assertEquals(21.02, stop.getLongitude(), 0.000001);
        assertFalse(stop.isWheelchairAccessible());
    }
}
