package pl.j.reinmar.mapwaw.wtp.service;

import pl.j.reinmar.mapwaw.wtp.model.RouteStop;
import pl.j.reinmar.mapwaw.wtp.model.RouteVariant;
import pl.j.reinmar.mapwaw.wtp.model.Stop;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * Wyznacza trasę komunikacją publiczną, minimalizując kolejno liczbę przesiadek
 * i liczbę przejechanych odcinków między przystankami.
 */
public class RoutingEngine {

    private final List<RouteVariant> routeVariants = new ArrayList<>();

    public RoutingEngine() {
    }

    public RoutingEngine(List<RouteVariant> routeVariants) {
        if (routeVariants != null) {
            routeVariants.forEach(this::addRouteVariant);
        }
    }

    public void addRouteVariant(RouteVariant routeVariant) {
        if (routeVariant != null) {
            routeVariants.add(routeVariant);
        }
    }

    /**
     * Finds the best route. Stops are matched by their GTFS stop ID, not object identity.
     *
     * @return an empty result when either stop is invalid or no directed route connects them
     */
    public Optional<RouteResult> findRoute(Stop origin, Stop destination) {
        if (!hasStopId(origin) || !hasStopId(destination)) {
            return Optional.empty();
        }
        if (origin.getId().equals(destination.getId())) {
            return Optional.of(new RouteResult(0, List.of()));
        }

        Map<String, List<Connection>> connectionsByStop = buildConnections();
        State start = new State(origin.getId(), null, null);
        Map<State, Cost> bestCosts = new HashMap<>();
        Map<State, Previous> previousStates = new HashMap<>();
        PriorityQueue<SearchEntry> pending = new PriorityQueue<>(
                Comparator.comparing(SearchEntry::cost)
                        .thenComparing(entry -> entry.state().stopId())
                        .thenComparing(entry -> entry.state().lineNumber() == null
                                ? "" : entry.state().lineNumber())
                        .thenComparing(entry -> entry.state().directionName() == null
                                ? "" : entry.state().directionName())
        );
        bestCosts.put(start, new Cost(0, 0));
        pending.add(new SearchEntry(start, new Cost(0, 0)));

        State target = null;
        while (!pending.isEmpty()) {
            SearchEntry current = pending.remove();
            if (!current.cost().equals(bestCosts.get(current.state()))) {
                continue;
            }
            if (current.state().stopId().equals(destination.getId())) {
                target = current.state();
                break;
            }

            for (Connection connection : connectionsByStop.getOrDefault(
                    current.state().stopId(), List.of())) {
                String lineNumber = connection.routeVariant().getLineNumber();
                String directionName = connection.routeVariant().getDirectionName();
                int transfer = current.state().lineNumber() == null
                        || (current.state().lineNumber().equals(lineNumber)
                        && java.util.Objects.equals(current.state().directionName(), directionName))
                        ? 0 : 1;
                State next = new State(connection.to().getId(), lineNumber, directionName);
                Cost nextCost = new Cost(current.cost().transfers() + transfer,
                        current.cost().hops() + 1);
                Cost existingCost = bestCosts.get(next);
                if (existingCost == null || nextCost.compareTo(existingCost) < 0) {
                    bestCosts.put(next, nextCost);
                    previousStates.put(next, new Previous(current.state(), connection));
                    pending.add(new SearchEntry(next, nextCost));
                }
            }
        }

        if (target == null) {
            return Optional.empty();
        }
        List<Connection> path = reconstructPath(start, target, previousStates);
        return Optional.of(new RouteResult(bestCosts.get(target).transfers(),
                buildLegs(origin, path)));
    }

    private Map<String, List<Connection>> buildConnections() {
        Map<String, List<Connection>> connectionsByStop = new HashMap<>();
        for (RouteVariant variant : routeVariants) {
            if (variant == null || variant.getLineNumber() == null
                    || variant.getLineNumber().isBlank() || variant.getRouteStops() == null) {
                continue;
            }

            List<RouteStop> routeStops = variant.getRouteStops().stream()
                    .filter(routeStop -> routeStop != null && hasStopId(routeStop.getStop()))
                    .sorted(Comparator.comparingInt(RouteStop::getSequenceOrder))
                    .toList();
            for (int i = 1; i < routeStops.size(); i++) {
                Stop from = routeStops.get(i - 1).getStop();
                Stop to = routeStops.get(i).getStop();
                if (!from.getId().equals(to.getId())) {
                    connectionsByStop.computeIfAbsent(from.getId(), ignored -> new ArrayList<>())
                            .add(new Connection(from, to, variant));
                }
            }
        }
        return connectionsByStop;
    }

    private List<Connection> reconstructPath(State start, State target,
                                              Map<State, Previous> previousStates) {
        List<Connection> path = new ArrayList<>();
        State current = target;
        while (!current.equals(start)) {
            Previous previous = previousStates.get(current);
            if (previous == null) {
                throw new IllegalStateException("Trasa nie ma kompletnego poprzednika.");
            }
            path.add(previous.connection());
            current = previous.state();
        }
        java.util.Collections.reverse(path);
        return path;
    }

    private List<RouteLeg> buildLegs(Stop origin, List<Connection> path) {
        List<RouteLeg> legs = new ArrayList<>();
        String currentLine = null;
        String currentDirection = null;
        List<Stop> legStops = null;

        for (Connection connection : path) {
            RouteVariant variant = connection.routeVariant();
            String line = variant.getLineNumber();
            String direction = variant.getDirectionName();
            if (legStops == null || !java.util.Objects.equals(currentLine, line)
                    || !java.util.Objects.equals(currentDirection, direction)) {
                if (legStops != null) {
                    legs.add(new RouteLeg(currentLine, currentDirection, legStops));
                }
                currentLine = line;
                currentDirection = direction;
                legStops = new ArrayList<>();
                legStops.add(legs.isEmpty() ? origin : connection.from());
            }
            legStops.add(connection.to());
        }
        if (legStops != null) {
            legs.add(new RouteLeg(currentLine, currentDirection, legStops));
        }
        return legs;
    }

    private static boolean hasStopId(Stop stop) {
        return stop != null && stop.getId() != null && !stop.getId().isBlank();
    }

    public record RouteResult(int transfers, List<RouteLeg> legs) {
        public RouteResult {
            legs = List.copyOf(legs);
        }
    }

    public record RouteLeg(String lineNumber, String directionName, List<Stop> stops) {
        public RouteLeg {
            stops = List.copyOf(stops);
        }
    }

    private record Connection(Stop from, Stop to, RouteVariant routeVariant) {
    }

    private record State(String stopId, String lineNumber, String directionName) {
    }

    private record Cost(int transfers, int hops) implements Comparable<Cost> {
        @Override
        public int compareTo(Cost other) {
            int transferOrder = Integer.compare(transfers, other.transfers);
            return transferOrder != 0 ? transferOrder : Integer.compare(hops, other.hops);
        }
    }

    private record Previous(State state, Connection connection) {
    }

    private record SearchEntry(State state, Cost cost) {
    }
}
