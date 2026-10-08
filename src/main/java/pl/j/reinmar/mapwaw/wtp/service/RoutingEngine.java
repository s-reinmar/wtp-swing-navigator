package pl.j.reinmar.mapwaw.wtp.service;

import pl.j.reinmar.mapwaw.wtp.model.DayType;
import pl.j.reinmar.mapwaw.wtp.model.Departure;
import pl.j.reinmar.mapwaw.wtp.model.RouteStop;
import pl.j.reinmar.mapwaw.wtp.model.RouteVariant;
import pl.j.reinmar.mapwaw.wtp.model.Stop;
import pl.j.reinmar.mapwaw.wtp.repository.ScheduleRepository;
import pl.j.reinmar.mapwaw.wtp.util.GeoUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * Wyznacza trasę komunikacją publiczną, minimalizując kolejno liczbę przesiadek
 * i liczbę przejechanych odcinków między przystankami.
 */
public class RoutingEngine {

    // Współczynnik przybliżający pieszą trasę do odległości w linii prostej.
    private static final double WALKING_DISTANCE_FACTOR = 1.3;
    private static final double WALKING_SPEED_METERS_PER_SECOND = 1.2;

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
     * Zwraca wszystkie warianty umożliwiające przejazd z przystanku początkowego
     * do końcowego bez przesiadki. Kolejność przystanków wariantu jest zachowana.
     */
    public List<RouteLeg> findDirectRoutes(Stop origin, Stop destination) {
        if (!hasStopId(origin) || !hasStopId(destination)
                || origin.getId().equals(destination.getId())) {
            return List.of();
        }

        List<RouteLeg> directRoutes = new ArrayList<>();
        for (RouteVariant variant : routeVariants) {
            if (variant == null || variant.getLineNumber() == null
                    || variant.getLineNumber().isBlank() || variant.getRouteStops() == null) {
                continue;
            }

            List<RouteStop> routeStops = variant.getRouteStops().stream()
                    .filter(routeStop -> routeStop != null && hasStopId(routeStop.getStop()))
                    .sorted(Comparator.comparingInt(RouteStop::getSequenceOrder))
                    .toList();
            int originIndex = -1;
            int destinationIndex = -1;
            for (int i = 0; i < routeStops.size(); i++) {
                String stopId = routeStops.get(i).getStop().getId();
                if (originIndex < 0 && origin.getId().equals(stopId)) {
                    originIndex = i;
                } else if (originIndex >= 0 && destination.getId().equals(stopId)) {
                    destinationIndex = i;
                    break;
                }
            }

            if (destinationIndex > originIndex && originIndex >= 0) {
                List<Stop> stops = routeStops.subList(originIndex, destinationIndex + 1).stream()
                        .map(RouteStop::getStop)
                        .toList();
                directRoutes.add(new RouteLeg(variant.getLineNumber(),
                        variant.getDirectionName(), stops));
            }
        }

        directRoutes.sort(Comparator.comparing(RouteLeg::lineNumber)
                .thenComparing(leg -> leg.directionName() == null ? "" : leg.directionName()));
        return List.copyOf(directRoutes);
    }

    /**
     * Zwraca dostępne kursy bezpośrednie, uwzględniając najbliższy odjazd po
     * wskazanej przez użytkownika dacie i godzinie.
     */
    public List<ScheduledRoute> findDirectRoutes(Stop origin, Stop destination,
                                                  LocalDateTime departureDateTime,
                                                  ScheduleRepository schedule) {
        validateScheduleQuery(departureDateTime, schedule);
        if (!hasDistinctStops(origin, destination)) {
            return List.of();
        }

        List<ScheduledRoute> routes = new ArrayList<>();
        for (RouteVariant variant : routeVariants) {
            List<Stop> stops = validOrderedStops(variant);
            int fromIndex = indexOfStop(stops, origin.getId(), 0);
            int toIndex = fromIndex < 0 ? -1 : indexOfStop(stops, destination.getId(), fromIndex + 1);
            if (toIndex < 0) {
                continue;
            }

            LocalDateTime departure = findNextDeparture(
                    schedule, variant, origin.getId(), departureDateTime);
            if (departure == null) {
                continue;
            }
            LocalDateTime arrival = departure.plusSeconds(
                    travelTimeBetween(variant, origin.getId(), destination.getId()));
            routes.add(new ScheduledRoute(0, List.of(new ScheduledLeg(
                    new RouteLeg(variant.getLineNumber(), variant.getDirectionName(),
                            stops.subList(fromIndex, toIndex + 1)),
                    departure, arrival))));
        }
        return sortedByArrival(routes);
    }

    /**
     * Zwraca trasy wymagające dokładnie jednej przesiadki, uporządkowane według
     * liczby przejechanych odcinków.
     */
    public List<RouteResult> findRoutesWithOneTransfer(Stop origin, Stop destination) {
        if (!hasStopId(origin) || !hasStopId(destination)
                || origin.getId().equals(destination.getId())) {
            return List.of();
        }

        List<VariantPath> variants = routeVariants.stream()
                .filter(variant -> variant != null && variant.getLineNumber() != null
                        && !variant.getLineNumber().isBlank() && variant.getRouteStops() != null)
                .map(variant -> new VariantPath(variant, orderedStops(variant)))
                .filter(variantPath -> variantPath.stops().size() >= 2)
                .toList();
        List<RouteResult> routes = new ArrayList<>();

        for (VariantPath first : variants) {
            for (int originIndex = 0; originIndex < first.stops().size() - 1; originIndex++) {
                if (!origin.getId().equals(first.stops().get(originIndex).getId())) {
                    continue;
                }
                for (int transferIndex = originIndex + 1;
                     transferIndex < first.stops().size(); transferIndex++) {
                    Stop interchange = first.stops().get(transferIndex);
                    for (VariantPath second : variants) {
                        if (sameService(first.variant(), second.variant())) {
                            continue;
                        }
                        for (int secondTransferIndex = 0;
                             secondTransferIndex < second.stops().size() - 1;
                             secondTransferIndex++) {
                            Stop boardingStop = second.stops().get(secondTransferIndex);
                            if (!sameStopComplex(interchange, boardingStop)) {
                                continue;
                            }
                            int destinationIndex = indexOfStop(
                                    second.stops(), destination.getId(), secondTransferIndex + 1);
                            if (destinationIndex < 0) {
                                continue;
                            }

                            List<Stop> firstLegStops = List.copyOf(
                                    first.stops().subList(originIndex, transferIndex + 1));
                            List<Stop> secondLegStops = List.copyOf(
                                    second.stops().subList(secondTransferIndex, destinationIndex + 1));
                            routes.add(new RouteResult(1, List.of(
                                    new RouteLeg(first.variant().getLineNumber(),
                                            first.variant().getDirectionName(), firstLegStops),
                                    new RouteLeg(second.variant().getLineNumber(),
                                            second.variant().getDirectionName(), secondLegStops)
                            )));
                        }
                    }
                }
            }
        }

        routes.sort(Comparator.comparingInt(RoutingEngine::countHops)
                .thenComparing(route -> route.legs().get(0).lineNumber())
                .thenComparing(route -> route.legs().get(1).lineNumber())
                .thenComparing(route -> route.legs().get(0).directionName() == null
                        ? "" : route.legs().get(0).directionName())
                .thenComparing(route -> route.legs().get(1).directionName() == null
                        ? "" : route.legs().get(1).directionName()));
        return List.copyOf(routes);
    }

    /**
     * Znajduje wykonalne połączenia z dokładnie jedną przesiadką. Odjazdy
     * dobierane są z rozkładu dla wskazanego dnia i czasu, a przesiadka musi
     * nastąpić nie wcześniej niż szacowany przyjazd pierwszego kursu.
     */
    public List<ScheduledRoute> findRoutesWithOneTransfer(Stop origin, Stop destination,
                                                           LocalDateTime departureDateTime,
                                                           ScheduleRepository schedule) {
        validateScheduleQuery(departureDateTime, schedule);
        if (!hasDistinctStops(origin, destination)) {
            return List.of();
        }

        List<VariantPath> variants = routeVariants.stream()
                .filter(variant -> variant != null && variant.getLineNumber() != null
                        && !variant.getLineNumber().isBlank() && variant.getRouteStops() != null)
                .map(variant -> new VariantPath(variant, orderedStops(variant)))
                .filter(variantPath -> variantPath.stops().size() >= 2)
                .toList();
        List<ScheduledRoute> routes = new ArrayList<>();

        for (VariantPath first : variants) {
            for (int originIndex = 0; originIndex < first.stops().size() - 1; originIndex++) {
                if (!origin.getId().equals(first.stops().get(originIndex).getId())) {
                    continue;
                }
                LocalDateTime firstDeparture = findNextDeparture(schedule,
                        first.variant(), origin.getId(), departureDateTime);
                if (firstDeparture == null) {
                    continue;
                }
                for (int transferIndex = originIndex + 1;
                     transferIndex < first.stops().size(); transferIndex++) {
                    Stop interchange = first.stops().get(transferIndex);
                    LocalDateTime interchangeArrival = firstDeparture.plusSeconds(
                            travelTimeBetween(first.variant(), origin.getId(), interchange.getId()));
                    for (VariantPath second : variants) {
                        if (sameService(first.variant(), second.variant())) {
                            continue;
                        }
                        for (int secondTransferIndex = 0;
                             secondTransferIndex < second.stops().size() - 1;
                             secondTransferIndex++) {
                            Stop boardingStop = second.stops().get(secondTransferIndex);
                            if (!sameStopComplex(interchange, boardingStop)) {
                                continue;
                            }
                            if (!interchange.getId().equals(boardingStop.getId())
                                    && (!hasValidCoordinates(interchange)
                                    || !hasValidCoordinates(boardingStop))) {
                                continue;
                            }
                            int destinationIndex = indexOfStop(
                                    second.stops(), destination.getId(), secondTransferIndex + 1);
                            if (destinationIndex < 0) {
                                continue;
                            }

                            Duration walkingTime = estimateWalkingTransferTime(
                                    interchange, boardingStop);
                            LocalDateTime earliestDeparture = interchangeArrival.plus(walkingTime);
                            LocalDateTime secondDeparture = findNextDeparture(schedule,
                                    second.variant(), boardingStop.getId(), earliestDeparture);
                            if (secondDeparture == null) {
                                continue;
                            }
                            LocalDateTime arrival = secondDeparture.plusSeconds(
                                    travelTimeBetween(second.variant(), boardingStop.getId(),
                                            destination.getId()));
                            List<Stop> firstLegStops = List.copyOf(
                                    first.stops().subList(originIndex, transferIndex + 1));
                            List<Stop> secondLegStops = List.copyOf(
                                    second.stops().subList(secondTransferIndex, destinationIndex + 1));
                            routes.add(new ScheduledRoute(1, List.of(
                                    new ScheduledLeg(new RouteLeg(first.variant().getLineNumber(),
                                            first.variant().getDirectionName(), firstLegStops),
                                            firstDeparture, interchangeArrival),
                                    new ScheduledLeg(new RouteLeg(second.variant().getLineNumber(),
                                            second.variant().getDirectionName(), secondLegStops),
                                            secondDeparture, arrival)
                            )));
                        }
                    }
                }
            }
        }
        return sortedByArrival(routes);
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

    private static List<Stop> orderedStops(RouteVariant variant) {
        return variant.getRouteStops().stream()
                .filter(routeStop -> routeStop != null && hasStopId(routeStop.getStop()))
                .sorted(Comparator.comparingInt(RouteStop::getSequenceOrder))
                .map(RouteStop::getStop)
                .toList();
    }

    private static List<Stop> validOrderedStops(RouteVariant variant) {
        if (variant == null || variant.getLineNumber() == null || variant.getLineNumber().isBlank()
                || variant.getRouteStops() == null) {
            return List.of();
        }
        return orderedStops(variant);
    }

    private static boolean hasDistinctStops(Stop origin, Stop destination) {
        return hasStopId(origin) && hasStopId(destination)
                && !origin.getId().equals(destination.getId());
    }

    private static void validateScheduleQuery(LocalDateTime departureDateTime,
                                              ScheduleRepository schedule) {
        java.util.Objects.requireNonNull(departureDateTime, "departureDateTime");
        java.util.Objects.requireNonNull(schedule, "schedule");
    }

    private static LocalDateTime findNextDeparture(ScheduleRepository schedule, RouteVariant variant,
                                                   String stopId, LocalDateTime earliest) {
        for (int dayOffset = 0; dayOffset <= 7; dayOffset++) {
            LocalDate date = earliest.toLocalDate().plusDays(dayOffset);
            DayType dayType = dayTypeFor(date);
            LocalTime earliestTime = dayOffset == 0 ? earliest.toLocalTime() : LocalTime.MIN;
            Optional<Departure> nextDeparture = schedule.getDeparturesForLineAndStop(
                            variant.getLineNumber(), stopId)
                    .stream()
                    .filter(departure -> departure.getDepartureTime() != null
                            && departure.getDayType() == dayType
                            && (departure.getTripId() == null
                            || departure.getTripId().equals(variant.getId()))
                            && !departure.getDepartureTime().isBefore(earliestTime))
                    .min(Comparator.comparing(Departure::getDepartureTime));
            if (nextDeparture.isPresent()) {
                return LocalDateTime.of(date, nextDeparture.get().getDepartureTime());
            }
        }
        return null;
    }

    private static DayType dayTypeFor(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case SATURDAY -> DayType.SATURDAY;
            case SUNDAY -> DayType.SUNDAY;
            default -> DayType.WEEKDAY;
        };
    }

    private static int travelTimeBetween(RouteVariant variant, String fromStopId, String toStopId) {
        return Math.max(0, travelTimeFromVariantStart(variant, toStopId)
                - travelTimeFromVariantStart(variant, fromStopId));
    }

    private static int travelTimeFromVariantStart(RouteVariant variant, String stopId) {
        return variant.getRouteStops().stream()
                .filter(routeStop -> routeStop != null && hasStopId(routeStop.getStop())
                        && stopId.equals(routeStop.getStop().getId()))
                .min(Comparator.comparingInt(RouteStop::getSequenceOrder))
                .map(RouteStop::getTravelTimeFromStartSec)
                .orElse(0);
    }

    private static boolean sameStopComplex(Stop first, Stop second) {
        if (!hasStopId(first) || !hasStopId(second)) {
            return false;
        }
        if (first.getId().equals(second.getId())) {
            return true;
        }
        return first.getName() != null && !first.getName().isBlank()
                && second.getName() != null && !second.getName().isBlank()
                && first.getName().trim().toLowerCase(Locale.ROOT)
                .equals(second.getName().trim().toLowerCase(Locale.ROOT));
    }

    private static Duration estimateWalkingTransferTime(Stop from, Stop to) {
        if (from.getId().equals(to.getId())) {
            return Duration.ZERO;
        }
        if (!hasValidCoordinates(from) || !hasValidCoordinates(to)) {
            throw new IllegalArgumentException("Przystanki muszą mieć poprawne współrzędne.");
        }
        double straightLineDistance = GeoUtils.calculateDistanceMeters(
                from.getLatitude(), from.getLongitude(), to.getLatitude(), to.getLongitude());
        long walkingSeconds = (long) Math.ceil(straightLineDistance
                * WALKING_DISTANCE_FACTOR / WALKING_SPEED_METERS_PER_SECOND);
        return Duration.ofSeconds(walkingSeconds);
    }

    private static boolean hasValidCoordinates(Stop stop) {
        return Double.isFinite(stop.getLatitude()) && stop.getLatitude() >= -90
                && stop.getLatitude() <= 90 && Double.isFinite(stop.getLongitude())
                && stop.getLongitude() >= -180 && stop.getLongitude() <= 180;
    }

    private static List<ScheduledRoute> sortedByArrival(List<ScheduledRoute> routes) {
        return routes.stream()
                .sorted(Comparator.comparing(ScheduledRoute::arrivalDateTime)
                .thenComparing(ScheduledRoute::totalTransferWaitingTime)
                .thenComparing(ScheduledRoute::departureDateTime)
                .thenComparingInt(ScheduledRoute::transfers))
                .distinct()
                .toList();
    }

    private static boolean sameService(RouteVariant first, RouteVariant second) {
        return first.getLineNumber().equals(second.getLineNumber())
                && java.util.Objects.equals(first.getDirectionName(), second.getDirectionName());
    }

    private static int indexOfStop(List<Stop> stops, String stopId, int startIndex) {
        for (int i = startIndex; i < stops.size(); i++) {
            if (stopId.equals(stops.get(i).getId())) {
                return i;
            }
        }
        return -1;
    }

    private static int countHops(RouteResult route) {
        return route.legs().stream().mapToInt(leg -> leg.stops().size() - 1).sum();
    }

    private static int countStops(List<RouteLeg> legs) {
        int stopCount = 0;
        String previousLegLastStopId = null;
        for (RouteLeg leg : legs) {
            List<Stop> stops = leg.stops();
            int firstStopIndex = 0;
            if (previousLegLastStopId != null && !stops.isEmpty()) {
                Stop firstStop = stops.getFirst();
                if (previousLegLastStopId.equals(firstStop.getId())) {
                    firstStopIndex = 1;
                }
            }
            stopCount += stops.size() - firstStopIndex;
            previousLegLastStopId = stops.isEmpty() ? null : stops.getLast().getId();
        }
        return stopCount;
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

        public int numberOfStops() {
            return countStops(legs);
        }
    }

    public record RouteLeg(String lineNumber, String directionName, List<Stop> stops) {
        public RouteLeg {
            stops = List.copyOf(stops);
        }
    }

    public record ScheduledRoute(int transfers, List<ScheduledLeg> legs) {
        public ScheduledRoute {
            legs = List.copyOf(legs);
            if (legs.isEmpty()) {
                throw new IllegalArgumentException("Zaplanowana trasa musi zawierać co najmniej jeden odcinek.");
            }
        }

        public LocalDateTime departureDateTime() {
            return legs.getFirst().departureDateTime();
        }

        public LocalDateTime arrivalDateTime() {
            return legs.getLast().arrivalDateTime();
        }

        /** Zwraca czas od pierwszego odjazdu do przyjazdu na przystanek końcowy. */
        public Duration totalTravelTime() {
            return Duration.between(departureDateTime(), arrivalDateTime());
        }

        /** Zwraca liczbę odwiedzonych przystanków, nie licząc dwukrotnie wspólnego węzła przesiadki. */
        public int numberOfStops() {
            return countStops(legs.stream().map(ScheduledLeg::route).toList());
        }

        /** Zwraca łączny czas oczekiwania między kolejnymi odcinkami trasy. */
        public Duration totalTransferWaitingTime() {
            Duration waitingTime = Duration.ZERO;
            for (int i = 1; i < legs.size(); i++) {
                waitingTime = waitingTime.plus(Duration.between(
                        legs.get(i - 1).arrivalDateTime(), legs.get(i).departureDateTime()));
            }
            return waitingTime;
        }

        /** Zwraca łączny szacowany czas dojścia pieszego między słupkami przesiadkowymi. */
        public Duration totalWalkingTransferTime() {
            Duration walkingTime = Duration.ZERO;
            for (int i = 1; i < legs.size(); i++) {
                List<Stop> previousStops = legs.get(i - 1).route().stops();
                List<Stop> nextStops = legs.get(i).route().stops();
                Stop alightingStop = previousStops.getLast();
                Stop boardingStop = nextStops.getFirst();
                if (sameStopComplex(alightingStop, boardingStop)) {
                    walkingTime = walkingTime.plus(
                            estimateWalkingTransferTime(alightingStop, boardingStop));
                }
            }
            return walkingTime;
        }
    }

    public record ScheduledLeg(RouteLeg route, LocalDateTime departureDateTime,
                               LocalDateTime arrivalDateTime) {
    }

    private record Connection(Stop from, Stop to, RouteVariant routeVariant) {
    }

    private record VariantPath(RouteVariant variant, List<Stop> stops) {
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
