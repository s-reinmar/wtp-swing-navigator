package pl.j.reinmar.mapwaw.wtp.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.j.reinmar.mapwaw.wtp.model.DayType;
import pl.j.reinmar.mapwaw.wtp.model.Departure;
import pl.j.reinmar.mapwaw.wtp.model.Line;
import pl.j.reinmar.mapwaw.wtp.model.Stop;
import pl.j.reinmar.mapwaw.wtp.model.TransportType;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleRepositoryTest {

    private ScheduleRepository repository;

    private Stop stop(String id, String name, String code) {
        return new Stop(id, name, code, 52.229712, 21.012234, true);
    }


    private Line line(String number) {
        return new Line(number, TransportType.BUS, "MZA");
    }

    @BeforeEach
    void setUp() {
        repository = new ScheduleRepository();
    }

    @Test
    @DisplayName("Dodaje i wyszukuje przystanki po ID, kodzie i nazwie")
    void shouldAddAndFindStopsByIdCodeAndName() {
        Stop centrum = stop("700901", "Centrum", "01");
        Stop centrumNorth = stop("700902", "Centrum Północ", "02");

        repository.addStop(centrum);
        repository.addStop(centrumNorth);

        assertSame(centrum, repository.findStopById("700901"));
        assertSame(centrum, repository.findStopByCode("01"));
        assertEquals(List.of(centrum), repository.findStopsByName("  CENTRUM "));
        assertTrue(repository.findStopsByName("centrum pół").contains(centrumNorth));
        assertEquals(2, repository.getStopsCount());
    }

    @Test
    @DisplayName("Zwraca pustą listę dla pustego lub nieznalezionego wyszukiwania przystanków")
    void shouldReturnNoStopsForBlankOrUnknownName() {
        repository.addStop(stop("700901", "Centrum", "01"));

        assertTrue(repository.findStopsByName(null).isEmpty());
        assertTrue(repository.findStopsByName("  ").isEmpty());
        assertTrue(repository.findStopsByName("nieznana nazwa").isEmpty());
    }

    @Test
    @DisplayName("Dodaje i wyszukuje linię po numerze")
    void shouldAddAndFindLineByNumber() {
        Line bus = line("507");

        repository.addLine(bus);

        assertSame(bus, repository.findLineByNumber("507"));
        assertEquals(1, repository.getLinesCount());
        assertTrue(repository.getDeparturesForLine("unknown").isEmpty());
    }

    @Test
    @DisplayName("Indeksuje odjazdy po przystanku, linii i parze linii z przystankiem")
    void shouldIndexDeparturesByStopLineAndTheirPair() {
        Stop centrum = stop("700901", "Centrum", "01");
        Stop dworzec = stop("700902", "Dworzec", "02");
        Line bus = line("507");
        Line tram = new Line("9", TransportType.TRAM, "TW");
        Departure first = new Departure(bus, centrum, LocalTime.of(8, 30), DayType.WEEKDAY, "04");
        Departure second = new Departure(bus, centrum, LocalTime.of(8, 45), DayType.WEEKDAY, "05");
        Departure otherStop = new Departure(bus, dworzec, LocalTime.of(9, 0), DayType.SATURDAY, "06");
        Departure otherLine = new Departure(tram, centrum, LocalTime.of(9, 15), DayType.SUNDAY, "07");

        repository.addDeparture(first);
        repository.addDeparture(second);
        repository.addDeparture(otherStop);
        repository.addDeparture(otherLine);

        assertEquals(List.of(first, second, otherLine), repository.getDeparturesForStop("700901"));
        assertEquals(List.of(first, second, otherStop), repository.getDeparturesForLine("507"));
        assertEquals(List.of(first, second), repository.getDeparturesForLineAndStop("507", "700901"));
        assertTrue(repository.getDeparturesForStop("unknown").isEmpty());
        assertTrue(repository.getDeparturesForLineAndStop("unknown", "unknown").isEmpty());
    }

    @Test
    @DisplayName("Ignoruje null przy dodawaniu obiektów")
    void shouldIgnoreNullEntities() {
        repository.addStop(null);
        repository.addLine(null);
        repository.addDeparture(null);

        assertEquals(0, repository.getStopsCount());
        assertEquals(0, repository.getLinesCount());
        assertTrue(repository.getDeparturesForStop("700901").isEmpty());
    }
}