package pl.j.reinmar.mapwaw.wtp.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LineTest {

    @Test
    @DisplayName("Przechowuje i aktualizuje dane linii")
    void shouldStoreAndUpdateLineData() {
        Line line = new Line("507", TransportType.BUS, "MZA");

        assertEquals("507", line.getLineNumber());
        assertEquals(TransportType.BUS, line.getTransportType());
        assertEquals("MZA", line.getOperator());

        line.setLineNumber("9");
        line.setTransportType(TransportType.TRAM);
        line.setOperator("TW");

        assertEquals("9", line.getLineNumber());
        assertEquals(TransportType.TRAM, line.getTransportType());
        assertEquals("TW", line.getOperator());
    }
}
