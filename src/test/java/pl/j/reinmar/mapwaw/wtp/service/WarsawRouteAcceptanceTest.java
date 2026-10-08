package pl.j.reinmar.mapwaw.wtp.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.j.reinmar.mapwaw.wtp.model.DayType;
import pl.j.reinmar.mapwaw.wtp.model.Departure;
import pl.j.reinmar.mapwaw.wtp.model.Line;
import pl.j.reinmar.mapwaw.wtp.model.RouteStop;
import pl.j.reinmar.mapwaw.wtp.model.RouteVariant;
import pl.j.reinmar.mapwaw.wtp.model.Stop;
import pl.j.reinmar.mapwaw.wtp.model.TransportType;
import pl.j.reinmar.mapwaw.wtp.repository.ScheduleRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WarsawRouteAcceptanceTest {

    private static final LocalDateTime WEEKDAY_DEPARTURE =
            LocalDateTime.of(2026, 10, 5, 8, 0);

    @Test
    @DisplayName("Warszawa: planuje bezpośredni przejazd metrem M1 Centrum – Politechnika")
    void plansDirectM1JourneyFromCentrumToPolitechnika() {
        Stop centrum = stop("700101", "Centrum", "01", 52.2297, 21.0122);
        Stop politechnika = stop("700201", "Politechnika", "01", 52.2179, 21.0175);
        RoutingEngine engine = new RoutingEngine(List.of(
                variant("m1-centrum-politechnika", "M1", "Kabaty",
                        centrum, 0, politechnika, 240)
        ));
        ScheduleRepository schedule = new ScheduleRepository();
        addDeparture(schedule, "m1-centrum-politechnika", "M1", centrum,
                LocalTime.of(8, 5));
        addDeparture(schedule, "m1-centrum-politechnika", "M1", centrum,
                LocalTime.of(8, 10));

        List<RoutingEngine.ScheduledRoute> routes = engine.findDirectRoutes(
                centrum, politechnika, WEEKDAY_DEPARTURE.plusMinutes(6), schedule);

        assertEquals(1, routes.size());
        RoutingEngine.ScheduledRoute route = routes.getFirst();
        assertEquals(List.of("M1"), route.legs().stream()
                .map(leg -> leg.route().lineNumber()).toList());
        assertEquals(List.of("700101", "700201"),
                route.legs().getFirst().route().stops().stream().map(Stop::getId).toList());
        assertEquals(LocalDateTime.of(2026, 10, 5, 8, 10), route.departureDateTime());
        assertEquals(LocalDateTime.of(2026, 10, 5, 8, 14), route.arrivalDateTime());
        assertEquals(Duration.ofMinutes(4), route.totalTravelTime());
        assertEquals(2, route.numberOfStops());
        assertEquals(0, route.transfers());
    }

    @Test
    @DisplayName("Warszawa: łączy M1 i M2 na stacji Świętokrzyska do Stadionu Narodowego")
    void plansM1ToM2TransferAtSwietokrzyska() {
        Stop dworzecGdanski = stop("700301", "Dworzec Gdański", "01", 52.2578, 20.9941);
        Stop swietokrzyska = stop("700401", "Świętokrzyska", "01", 52.2355, 21.0097);
        Stop stadionNarodowy = stop("700501", "Stadion Narodowy", "01", 52.2467, 21.0424);
        RoutingEngine engine = new RoutingEngine(List.of(
                variant("m1-dworzec-gdanski-swietokrzyska", "M1", "Kabaty",
                        dworzecGdanski, 0, swietokrzyska, 420),
                variant("m2-swietokrzyska-stadion", "M2", "Bródno",
                        swietokrzyska, 0, stadionNarodowy, 480)
        ));
        ScheduleRepository schedule = new ScheduleRepository();
        addDeparture(schedule, "m1-dworzec-gdanski-swietokrzyska", "M1",
                dworzecGdanski, LocalTime.of(8, 3));
        addDeparture(schedule, "m2-swietokrzyska-stadion", "M2",
                swietokrzyska, LocalTime.of(8, 11));
        addDeparture(schedule, "m2-swietokrzyska-stadion", "M2",
                swietokrzyska, LocalTime.of(8, 15));

        List<RoutingEngine.ScheduledRoute> routes = engine.findRoutesWithOneTransfer(
                dworzecGdanski, stadionNarodowy, WEEKDAY_DEPARTURE, schedule);

        assertEquals(1, routes.size());
        RoutingEngine.ScheduledRoute route = routes.getFirst();
        assertEquals(1, route.transfers());
        assertEquals(List.of("M1", "M2"), route.legs().stream()
                .map(leg -> leg.route().lineNumber()).toList());
        assertEquals("Świętokrzyska", route.legs().getFirst()
                .route().stops().getLast().getName());
        assertEquals("Świętokrzyska", route.legs().getLast()
                .route().stops().getFirst().getName());
        assertEquals(LocalDateTime.of(2026, 10, 5, 8, 3), route.departureDateTime());
        assertEquals(LocalDateTime.of(2026, 10, 5, 8, 11),
                route.legs().getLast().departureDateTime());
        assertEquals(LocalDateTime.of(2026, 10, 5, 8, 19), route.arrivalDateTime());
        assertEquals(Duration.ofMinutes(16), route.totalTravelTime());
        assertEquals(Duration.ZERO, route.totalWalkingTransferTime());
        assertEquals(Duration.ofMinutes(1), route.totalTransferWaitingTime());
        assertEquals(3, route.numberOfStops());
    }

    private static RouteVariant variant(String id, String line, String direction,
                                        Stop firstStop, int firstTravelSeconds,
                                        Stop secondStop, int secondTravelSeconds) {
        return new RouteVariant(id, line, direction, List.of(
                new RouteStop(firstStop, 1, firstTravelSeconds),
                new RouteStop(secondStop, 2, secondTravelSeconds)));
    }

    private static void addDeparture(ScheduleRepository schedule, String tripId,
                                     String lineNumber, Stop stop, LocalTime departureTime) {
        schedule.addDeparture(new Departure(tripId,
                new Line(lineNumber, TransportType.METRO, "Metro Warszawskie"),
                stop, departureTime, DayType.WEEKDAY, "01"));
    }

    private static Stop stop(String id, String name, String code,
                             double latitude, double longitude) {
        return new Stop(id, name, code, latitude, longitude, true);
    }
}
