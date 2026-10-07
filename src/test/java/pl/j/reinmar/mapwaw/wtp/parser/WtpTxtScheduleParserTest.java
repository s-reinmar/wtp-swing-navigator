package pl.j.reinmar.mapwaw.wtp.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.j.reinmar.mapwaw.wtp.model.DayType;
import pl.j.reinmar.mapwaw.wtp.model.Departure;
import pl.j.reinmar.mapwaw.wtp.model.Line;
import pl.j.reinmar.mapwaw.wtp.model.Stop;
import pl.j.reinmar.mapwaw.wtp.model.TransportType;
import pl.j.reinmar.mapwaw.wtp.repository.ScheduleRepository;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WtpTxtScheduleParserTest {

    private ScheduleRepository repository;
    private StopSectionParser stopParser;
    private LineScheduleSectionParser lineParser;
    private DepartureScheduleSectionParser departureParser;

    @BeforeEach
    void setUp() {
        repository = new ScheduleRepository();
        stopParser = new StopSectionParser();
        lineParser = new LineScheduleSectionParser();
        departureParser = new DepartureScheduleSectionParser();
    }

    @Test
    @DisplayName("Parsowanie poprawnego wiersza z pliku stops.txt z ZTM Warszawa")
    void shouldParseValidStopRowFromStopsTxt() {
        // Próbka rzeczywistego wiersza z pliku stops.txt
        String line = "700901,\"Centrum 01\",52.229712,21.012234,\"01\",1";

        boolean parsed = stopParser.parseAndAddStop(line, repository);

        assertTrue(parsed, "Wiersz przystanku powinien zostać pomyślnie sparsowany.");
        assertEquals(1, repository.getStopsCount());

        Stop stop = repository.findStopById("700901");
        assertNotNull(stop);
        assertEquals("Centrum 01", stop.getName());
        assertEquals(52.229712, stop.getLatitude(), 0.000001);
        assertEquals(21.012234, stop.getLongitude(), 0.000001);
        assertTrue(stop.isWheelchairAccessible());
    }

    @Test
    @DisplayName("Odrzucanie przystanku o współrzędnych leżących poza obszarem Warszawy")
    void shouldRejectStopOutsideWarsawBounds() {
        // Przystanek ze współrzędnymi (0,0) lub poza aglomeracją warszawską
        String invalidLine = "999999,\"Invalid Stop\",10.000000,10.000000,\"01\",0";

        boolean parsed = stopParser.parseAndAddStop(invalidLine, repository);

        assertFalse(parsed, "Przystanek poza obszarem Warszawy powinien zostać odrzucony.");
        assertEquals(0, repository.getStopsCount());
        assertNull(repository.findStopById("999999"));
    }

    @Test
    @DisplayName("Parsowanie linii transportowych i klasyfikacja typów transportu (BUS, TRAM, METRO)")
    void shouldParseRoutesTxtAndClassifyTransportTypes() {
        String tramRow = "0_1,2,\"1\",\"\",0";
        String busRow = "0_507,2,\"507\",\"\",3";
        String metroRow = "0_M1,2,\"M1\",\"\",1";

        assertTrue(lineParser.parseAndAddLine(tramRow, repository));
        assertTrue(lineParser.parseAndAddLine(busRow, repository));
        assertTrue(lineParser.parseAndAddLine(metroRow, repository));

        assertEquals(3, repository.getLinesCount());

        Line tram = repository.findLineByNumber("1");
        assertNotNull(tram);
        assertEquals(TransportType.TRAM, tram.getTransportType());

        Line bus = repository.findLineByNumber("507");
        assertNotNull(bus);
        assertEquals(TransportType.BUS, bus.getTransportType());

        Line metro = repository.findLineByNumber("M1");
        assertNotNull(metro);
        assertEquals(TransportType.METRO, metro.getTransportType());
    }

    @Test
    @DisplayName("Parsowanie godzin nocnych przekraczających 24:00 (np. 25:15:00 -> 01:15:00)")
    void shouldParseOverMidnightDepartureTimes() {
        Stop stop = new Stop("700901", "Centrum 01", "01", 52.229712, 21.012234, true);
        Line line = new Line("N32", TransportType.BUS, "MZA");
        repository.addStop(stop);
        repository.addLine(line);

        Map<String, DayType> calendarMap = Map.of("0_2", DayType.WEEKDAY);
        Map<String, Line> lineMap = Map.of("TRIP_NIGHT_1", line);

        departureParser.parseAndAddDeparture(
                "TRIP_NIGHT_1", "25:15:00", "700901", "0_2",
                repository, calendarMap, lineMap
        );

        List<Departure> departures = repository.getDeparturesForStop("700901");
        assertEquals(1, departures.size());
        assertEquals(LocalTime.of(1, 15, 0), departures.get(0).getDepartureTime());
    }

    @Test
    @DisplayName("Czyszczenie znaków BOM i białych znaków przez ScheduleFormatValidator")
    void shouldSanitizeRawLinesWithBOM() {
        String rawWithBOM = "\uFEFF700901,\"Centrum 01\",52.229712,21.012234,\"01\",1";
        String sanitized = ScheduleFormatValidator.sanitizeLine(rawWithBOM);

        assertFalse(sanitized.startsWith("\uFEFF"));
        assertTrue(sanitized.startsWith("700901"));
    }
}