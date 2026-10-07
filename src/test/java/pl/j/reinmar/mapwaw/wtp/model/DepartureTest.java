package pl.j.reinmar.mapwaw.wtp.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepartureTest {

    @Test
    @DisplayName("Przechowuje i aktualizuje dane odjazdu")
    void shouldStoreAndUpdateDepartureData() {
        Line line = new Line("507", TransportType.BUS, "MZA");
        Stop stop = new Stop("700901", "Centrum", "01", 52.23, 21.01, true);
        Departure departure = new Departure(line, stop, LocalTime.of(8, 30), DayType.WEEKDAY, "04");

        assertSame(line, departure.getLine());
        assertSame(stop, departure.getStop());
        assertEquals(LocalTime.of(8, 30), departure.getDepartureTime());
        assertEquals(DayType.WEEKDAY, departure.getDayType());
        assertEquals("04", departure.getBrigade());

        Line updatedLine = new Line("9", TransportType.TRAM, "TW");
        Stop updatedStop = new Stop("700902", "Dworzec", "02", 52.24, 21.02, false);
        departure.setLine(updatedLine);
        departure.setStop(updatedStop);
        departure.setDepartureTime(LocalTime.of(9, 15));
        departure.setDayType(DayType.SATURDAY);
        departure.setBrigade("05");

        assertSame(updatedLine, departure.getLine());
        assertSame(updatedStop, departure.getStop());
        assertEquals(LocalTime.of(9, 15), departure.getDepartureTime());
        assertEquals(DayType.SATURDAY, departure.getDayType());
        assertEquals("05", departure.getBrigade());
        assertTrue(new Departure(null, null, null, null, null).toString().contains("line=null"));
    }
}
