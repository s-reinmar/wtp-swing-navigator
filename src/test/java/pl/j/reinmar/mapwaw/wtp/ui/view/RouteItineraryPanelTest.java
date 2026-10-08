package pl.j.reinmar.mapwaw.wtp.ui.view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.j.reinmar.mapwaw.wtp.model.Stop;
import pl.j.reinmar.mapwaw.wtp.service.RoutingEngine;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouteItineraryPanelTest {

    private RouteItineraryPanel panel;

    @BeforeEach
    void setUp() throws Exception {
        SwingUtilities.invokeAndWait(() -> panel = new RouteItineraryPanel());
    }

    @Test
    @DisplayName("Pokazuje wejście, wyjście i szczegółowe instrukcje przesiadki")
    void rendersDetailedLegsAndTransferInstructions() throws Exception {
        Stop origin = stop("A", "Centrum", "01", 52.0, 21.0);
        Stop alightingStop = stop("B1", "Dworzec", "02", 52.0, 21.0);
        Stop boardingStop = stop("B2", "Dworzec", "04", 52.0, 21.001);
        Stop intermediate = stop("C", "Muzeum", "01", 52.0, 21.002);
        Stop destination = stop("D", "Stadion", "03", 52.0, 21.003);
        RoutingEngine.ScheduledRoute route = new RoutingEngine.ScheduledRoute(1, List.of(
                new RoutingEngine.ScheduledLeg(
                        new RoutingEngine.RouteLeg("17", "Dworzec Wschodni",
                                List.of(origin, alightingStop)),
                        LocalDateTime.of(2026, 10, 8, 8, 0),
                        LocalDateTime.of(2026, 10, 8, 8, 10)),
                new RoutingEngine.ScheduledLeg(
                        new RoutingEngine.RouteLeg("9", "Stadion", List.of(
                                boardingStop, intermediate, destination)),
                        LocalDateTime.of(2026, 10, 8, 8, 14),
                        LocalDateTime.of(2026, 10, 8, 8, 24))
        ));

        SwingUtilities.invokeAndWait(() -> panel.setRoute(route));

        SwingUtilities.invokeAndWait(() -> {
            List<String> text = findComponents(panel, JLabel.class).stream()
                    .map(JLabel::getText)
                    .toList();
            assertTrue(text.contains("Odcinek 1: linia 17 w kierunku Dworzec Wschodni"));
            assertTrue(text.contains("Wsiądź: Centrum (01)"));
            assertTrue(text.contains("Odjazd: 08.10.2026 08:00"));
            assertTrue(text.contains("Wysiądź: Dworzec (02)"));
            assertTrue(text.contains("Przystanki po drodze: Muzeum (01)"));
            assertTrue(text.contains("Po wysiadaniu na Dworzec (02) przejdź na Dworzec (04)."));
            assertTrue(text.contains("Szacowany czas dojścia: 1 min 15 s."));
            assertTrue(text.contains("Odjazd kolejnego kursu: 08.10.2026 08:14."));
            assertTrue(text.contains("Czas od przyjazdu do odjazdu: 4 min."));
            assertTrue(text.contains("Wysiądź: Stadion (03)"));
        });
    }

    @Test
    @DisplayName("Czyści szczegóły po odznaczeniu trasy")
    void clearsItineraryWhenNoRouteIsSelected() throws Exception {
        SwingUtilities.invokeAndWait(() -> panel.setRoute(null));

        SwingUtilities.invokeAndWait(() -> {
            assertNull(panel.getRoute());
            assertTrue(findComponents(panel, JLabel.class).stream()
                    .anyMatch(label -> label.getText().contains("Wybierz wariant trasy")));
        });
    }

    private static Stop stop(String id, String name, String code, double latitude, double longitude) {
        return new Stop(id, name, code, latitude, longitude, true);
    }

    private static <T extends Component> List<T> findComponents(Container root, Class<T> type) {
        List<T> matches = new ArrayList<>();
        for (Component component : root.getComponents()) {
            if (type.isInstance(component)) {
                matches.add(type.cast(component));
            }
            if (component instanceof Container container) {
                matches.addAll(findComponents(container, type));
            }
        }
        return matches;
    }
}
