package pl.j.reinmar.mapwaw.wtp.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.j.reinmar.mapwaw.wtp.model.RouteStop;
import pl.j.reinmar.mapwaw.wtp.model.RouteVariant;
import pl.j.reinmar.mapwaw.wtp.model.Stop;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RoutingEngineTest {

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

    private static Stop stop(String id) {
        return new Stop(id, "Przystanek " + id, "01", 52.0, 21.0, true);
    }

    private static List<String> stopIds(List<Stop> stops) {
        return stops.stream().map(Stop::getId).toList();
    }
}
