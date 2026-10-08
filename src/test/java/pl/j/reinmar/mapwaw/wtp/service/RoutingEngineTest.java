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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RoutingEngineTest {

    @Test
    @DisplayName("Dobiera najbliższy odjazd po czasie wskazanym dla trasy bezpośredniej")
    void schedulesDirectRouteFromUserDepartureTime() {
        Stop origin = stop("A");
        Stop destination = stop("B");
        RoutingEngine engine = new RoutingEngine(List.of(
                variantWithTravelTime("line", "17", "Centrum", 0, origin, 600, destination)
        ));
        ScheduleRepository schedule = new ScheduleRepository();
        addDeparture(schedule, "line", "17", origin, LocalTime.of(8, 30), DayType.WEEKDAY);
        addDeparture(schedule, "line", "17", origin, LocalTime.of(8, 45), DayType.WEEKDAY);
        addDeparture(schedule, "line", "17", origin, LocalTime.of(8, 40), DayType.SATURDAY);

        List<RoutingEngine.ScheduledRoute> routes = engine.findDirectRoutes(
                origin, destination, LocalDateTime.of(2026, 10, 5, 8, 32), schedule);

        assertEquals(1, routes.size());
        assertEquals(LocalDateTime.of(2026, 10, 5, 8, 45),
                routes.getFirst().departureDateTime());
        assertEquals(LocalDateTime.of(2026, 10, 5, 8, 55),
                routes.getFirst().arrivalDateTime());
    }

    @Test
    @DisplayName("Uwzględnia czas dojazdu i oczekiwanie na drugi kurs przy jednej przesiadce")
    void schedulesOneTransferAfterArrivalAtInterchange() {
        Stop origin = stop("A");
        Stop interchange = stop("B");
        Stop destination = stop("C");
        RoutingEngine engine = new RoutingEngine(List.of(
                variantWithTravelTime("first", "17", "Dworzec", 0, origin, 600, interchange),
                variantWithTravelTime("second", "9", "Centrum", 0, interchange, 600, destination)
        ));
        ScheduleRepository schedule = new ScheduleRepository();
        addDeparture(schedule, "first", "17", origin, LocalTime.of(8, 45), DayType.WEEKDAY);
        addDeparture(schedule, "second", "9", interchange, LocalTime.of(8, 50), DayType.WEEKDAY);
        addDeparture(schedule, "second", "9", interchange, LocalTime.of(9, 5), DayType.WEEKDAY);

        List<RoutingEngine.ScheduledRoute> routes = engine.findRoutesWithOneTransfer(
                origin, destination, LocalDateTime.of(2026, 10, 5, 8, 32), schedule);

        assertEquals(1, routes.size());
        RoutingEngine.ScheduledRoute route = routes.getFirst();
        assertEquals(1, route.transfers());
        assertEquals(LocalDateTime.of(2026, 10, 5, 8, 45), route.departureDateTime());
        assertEquals(LocalDateTime.of(2026, 10, 5, 8, 55),
                route.legs().getFirst().arrivalDateTime());
        assertEquals(LocalDateTime.of(2026, 10, 5, 9, 5),
                route.legs().getLast().departureDateTime());
        assertEquals(LocalDateTime.of(2026, 10, 5, 9, 15), route.arrivalDateTime());
        assertEquals(Duration.ofMinutes(10), route.totalTransferWaitingTime());
    }

    @Test
    @DisplayName("Sortuje połączenia według czasu przyjazdu wynikającego z oczekiwania na przesiadkę")
    void sortsTransferRoutesByTimetableWaitingTime() {
        Stop origin = stop("A");
        Stop firstInterchange = stop("B");
        Stop secondInterchange = stop("X");
        Stop destination = stop("C");
        RoutingEngine engine = new RoutingEngine(List.of(
                variantWithTravelTime("first-slower", "17", "Dworzec", 0,
                        origin, 600, firstInterchange),
                variantWithTravelTime("second-slower", "9", "Centrum", 0,
                        firstInterchange, 600, destination),
                variantWithTravelTime("first-faster", "18", "Pętla", 0,
                        origin, 300, secondInterchange),
                variantWithTravelTime("second-faster", "10", "Centrum", 0,
                        secondInterchange, 600, destination)
        ));
        ScheduleRepository schedule = new ScheduleRepository();
        addDeparture(schedule, "first-slower", "17", origin,
                LocalTime.of(8, 45), DayType.WEEKDAY);
        addDeparture(schedule, "second-slower", "9", firstInterchange,
                LocalTime.of(9, 5), DayType.WEEKDAY);
        addDeparture(schedule, "first-faster", "18", origin,
                LocalTime.of(8, 50), DayType.WEEKDAY);
        addDeparture(schedule, "second-faster", "10", secondInterchange,
                LocalTime.of(9, 0), DayType.WEEKDAY);

        List<RoutingEngine.ScheduledRoute> routes = engine.findRoutesWithOneTransfer(
                origin, destination, LocalDateTime.of(2026, 10, 5, 8, 30), schedule);

        assertEquals(2, routes.size());
        assertEquals("18", routes.getFirst().legs().getFirst().route().lineNumber());
        assertEquals(Duration.ofMinutes(5), routes.getFirst().totalTransferWaitingTime());
        assertEquals(LocalDateTime.of(2026, 10, 5, 9, 10),
                routes.getFirst().arrivalDateTime());
        assertEquals("17", routes.getLast().legs().getFirst().route().lineNumber());
        assertEquals(Duration.ofMinutes(10), routes.getLast().totalTransferWaitingTime());
        assertEquals(LocalDateTime.of(2026, 10, 5, 9, 15),
                routes.getLast().arrivalDateTime());
    }

    @Test
    @DisplayName("Znajduje wszystkie bezpośrednie warianty i zachowuje przystanki po drodze")
    void findsDirectRoutesInVariantOrder() {
        Stop origin = stop("A");
        Stop intermediate = stop("B");
        Stop destination = stop("C");
        RoutingEngine engine = new RoutingEngine(List.of(
                variant("second", "9", "Centrum", origin, destination),
                variant("first", "17", "Dworzec", origin, intermediate, destination),
                variant("reverse", "1", "Początek", destination, origin),
                variant("transfer-only-a", "2", "Cel", origin, intermediate),
                variant("transfer-only-b", "3", "Cel", intermediate, destination)
        ));

        List<RoutingEngine.RouteLeg> routes = engine.findDirectRoutes(origin, destination);

        assertEquals(2, routes.size());
        assertEquals(List.of("17", "9"), routes.stream()
                .map(RoutingEngine.RouteLeg::lineNumber)
                .toList());
        assertEquals(List.of("A", "B", "C"), stopIds(routes.getFirst().stops()));
        assertEquals("Dworzec", routes.getFirst().directionName());
    }

    @Test
    @DisplayName("Nie zwraca bezpośredniej trasy dla nieprawidłowych lub identycznych przystanków")
    void returnsNoDirectRoutesForInvalidOrSameStops() {
        RoutingEngine engine = new RoutingEngine(List.of(
                variant("line", "1", "Cel", stop("A"), stop("B"))
        ));

        assertTrue(engine.findDirectRoutes(null, stop("B")).isEmpty());
        assertTrue(engine.findDirectRoutes(stop(" "), stop("B")).isEmpty());
        assertTrue(engine.findDirectRoutes(stop("A"), stop("A")).isEmpty());
        assertTrue(engine.findDirectRoutes(stop("B"), stop("A")).isEmpty());
    }

    @Test
    @DisplayName("Znajduje i sortuje trasy wymagające dokładnie jednej przesiadki")
    void findsRoutesWithExactlyOneTransfer() {
        Stop origin = stop("A");
        Stop nearInterchange = stop("B");
        Stop interchange = stop("C");
        Stop destination = stop("D");
        Stop longerInterchange = stop("E");
        Stop threeTransferStart = stop("X");
        Stop threeTransferFirst = stop("Y");
        Stop threeTransferSecond = stop("Z");
        Stop threeTransferDestination = stop("W");
        RoutingEngine engine = new RoutingEngine(List.of(
                variant("direct", "1", "D", origin, destination),
                variant("first-short", "17", "Dworzec", origin, nearInterchange),
                variant("second-short", "9", "Centrum", nearInterchange, destination),
                variant("first-long", "2", "Pętla", origin, interchange, longerInterchange),
                variant("second-long", "3", "Centrum", longerInterchange, destination),
                variant("third-leg-a", "4", "Pośredni", threeTransferStart, threeTransferFirst),
                variant("third-leg-b", "5", "Pośredni", threeTransferFirst, threeTransferSecond),
                variant("third-leg-c", "6", "Cel", threeTransferSecond, threeTransferDestination)
        ));

        List<RoutingEngine.RouteResult> routes = engine.findRoutesWithOneTransfer(origin, destination);

        assertEquals(2, routes.size());
        assertTrue(routes.stream().allMatch(route -> route.transfers() == 1
                && route.legs().size() == 2));
        assertEquals(List.of("17", "9"), routes.getFirst().legs().stream()
                .map(RoutingEngine.RouteLeg::lineNumber)
                .toList());
        assertEquals(List.of("A", "B"), stopIds(routes.getFirst().legs().get(0).stops()));
        assertEquals(List.of("B", "D"), stopIds(routes.getFirst().legs().get(1).stops()));
        assertEquals(List.of("2", "3"), routes.get(1).legs().stream()
                .map(RoutingEngine.RouteLeg::lineNumber)
                .toList());
        assertEquals(List.of("A", "C", "E"), stopIds(routes.get(1).legs().get(0).stops()));
        assertEquals(List.of("E", "D"), stopIds(routes.get(1).legs().get(1).stops()));
    }

    @Test
    @DisplayName("Nie znajduje tras z przesiadką dla nieprawidłowych lub identycznych przystanków")
    void handlesInvalidEndpointsForOneTransferSearch() {
        RoutingEngine engine = new RoutingEngine(List.of(
                variant("first", "1", "Cel", stop("A"), stop("B")),
                variant("second", "2", "Cel", stop("B"), stop("C"))
        ));

        assertTrue(engine.findRoutesWithOneTransfer(null, stop("C")).isEmpty());
        assertTrue(engine.findRoutesWithOneTransfer(stop(" "), stop("C")).isEmpty());
        assertTrue(engine.findRoutesWithOneTransfer(stop("A"), stop("A")).isEmpty());
        assertTrue(engine.findRoutesWithOneTransfer(stop("C"), stop("A")).isEmpty());
    }

    @Test
    @DisplayName("Wybiera trasę bez przesiadek przed krótszą trasą wymagającą przesiadki")
    void prefersFewerTransfersBeforeFewerStops() {
        Stop start = stop("A");
        Stop middleOne = stop("B");
        Stop middleTwo = stop("C");
        Stop destination = stop("D");
        RoutingEngine engine = new RoutingEngine(List.of(
                variant("first-leg", "2", "D", start, middleOne),
                variant("second-leg", "3", "D", middleOne, destination),
                variant("no-transfer", "1", "D", start, middleOne, middleTwo, destination)
        ));

        RoutingEngine.RouteResult route = engine.findRoute(start, destination).orElseThrow();

        assertEquals(0, route.transfers());
        assertEquals(1, route.legs().size());
        assertEquals("1", route.legs().getFirst().lineNumber());
        assertEquals(List.of("A", "B", "C", "D"), stopIds(route.legs().getFirst().stops()));
    }

    @Test
    @DisplayName("Zwraca odcinki i przystanki dla trasy z jedną przesiadką")
    void returnsLegsForRouteWithTransfer() {
        Stop start = stop("A");
        Stop interchange = stop("B");
        Stop destination = stop("C");
        RoutingEngine engine = new RoutingEngine(List.of(
                variant("first", "17", "Dworzec", start, interchange),
                variant("second", "9", "Centrum", interchange, destination)
        ));

        RoutingEngine.RouteResult route = engine.findRoute(start, destination).orElseThrow();

        assertEquals(1, route.transfers());
        assertEquals(2, route.legs().size());
        assertEquals("17", route.legs().get(0).lineNumber());
        assertEquals(List.of("A", "B"), stopIds(route.legs().get(0).stops()));
        assertEquals("Dworzec", route.legs().get(0).directionName());
        assertEquals("9", route.legs().get(1).lineNumber());
        assertEquals(List.of("B", "C"), stopIds(route.legs().get(1).stops()));
    }

    @Test
    @DisplayName("Obsługuje brak trasy, ten sam przystanek i nieprawidłowe dane")
    void handlesMissingAndTrivialRoutes() {
        Stop start = stop("A");
        Stop destination = stop("B");
        RoutingEngine engine = new RoutingEngine(List.of(variant("outbound", "1", "B", start)));

        assertTrue(engine.findRoute(start, destination).isEmpty());
        Optional<RoutingEngine.RouteResult> sameStopRoute = engine.findRoute(start, stop("A"));
        assertTrue(sameStopRoute.isPresent());
        assertEquals(0, sameStopRoute.orElseThrow().transfers());
        assertTrue(sameStopRoute.orElseThrow().legs().isEmpty());
        assertTrue(engine.findRoute(null, destination).isEmpty());
        assertTrue(engine.findRoute(stop(" "), destination).isEmpty());
    }

    private static RouteVariant variant(String id, String line, String direction, Stop... stops) {
        List<RouteStop> routeStops = java.util.stream.IntStream.range(0, stops.length)
                .mapToObj(index -> new RouteStop(stops[index], index + 1, 0))
                .toList();
        return new RouteVariant(id, line, direction, routeStops);
    }

    private static RouteVariant variantWithTravelTime(String id, String line, String direction,
                                                       int firstTravelTime, Stop firstStop,
                                                       int secondTravelTime, Stop secondStop) {
        return new RouteVariant(id, line, direction, List.of(
                new RouteStop(firstStop, 1, firstTravelTime),
                new RouteStop(secondStop, 2, secondTravelTime)
        ));
    }

    private static void addDeparture(ScheduleRepository repository, String tripId,
                                     String lineNumber, Stop stop, LocalTime departureTime,
                                     DayType dayType) {
        repository.addDeparture(new Departure(tripId,
                new Line(lineNumber, TransportType.BUS, "MZA"),
                stop, departureTime, dayType, "01"));
    }

    private static Stop stop(String id) {
        return new Stop(id, "Przystanek " + id, "01", 52.0, 21.0, true);
    }

    private static List<String> stopIds(List<Stop> stops) {
        return stops.stream().map(Stop::getId).toList();
    }
}
