package pl.j.reinmar.mapwaw.wtp.ui.view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.j.reinmar.mapwaw.wtp.model.Stop;
import pl.j.reinmar.mapwaw.wtp.service.RoutingEngine;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class RouteResultsPanelTest {

    private RouteResultsPanel panel;

    @BeforeEach
    void setUp() throws Exception {
        SwingUtilities.invokeAndWait(() -> panel = new RouteResultsPanel());
    }

    @Test
    @DisplayName("Wyświetla warianty tras z czasem, przystankami i przesiadkami")
    void displaysRouteOptionsAndTheirSummaries() throws Exception {
        RoutingEngine.ScheduledRoute directRoute = scheduledRoute(
                0, 8, 0, 8, 25, "17", "Centrum", "A", "B");
        RoutingEngine.ScheduledRoute transferRoute = scheduledRoute(
                1, 8, 10, 8, 45, "17", "Dworzec", "A", "B",
                "9", "Centrum", "B", "C");

        SwingUtilities.invokeAndWait(() -> panel.setRoutes(List.of(directRoute, transferRoute)));

        SwingUtilities.invokeAndWait(() -> {
            JList<?> routeList = findComponents(panel, JList.class).getFirst();
            JScrollPane routeScrollPane = findComponents(panel, JScrollPane.class).getFirst();
            assertEquals(2, routeList.getModel().getSize());
            assertSame(directRoute, routeList.getModel().getElementAt(0));
            assertTrue(routeScrollPane.isVisible());

            Component rendered = renderItem(routeList, 1);
            List<JLabel> labels = findComponents((Container) rendered, JLabel.class);
            String rowText = labels.stream().map(JLabel::getText)
                    .reduce("", (first, second) -> first + " " + second);

            assertTrue(rowText.contains("17 → Dworzec  |  9 → Centrum"));
            assertTrue(rowText.contains("08:10 – 08:45"));
            assertTrue(rowText.contains("35 min"));
            assertTrue(rowText.contains("3 przystanki"));
            assertTrue(rowText.contains("1 przesiadka"));
        });
    }

    @Test
    @DisplayName("Pokazuje komunikat pustego wyniku i czytelną nazwę listy")
    void showsEmptyStateWhenNoRoutesAreAvailable() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JList<?> routeList = findComponents(panel, JList.class).getFirst();
            JScrollPane routeScrollPane = findComponents(panel, JScrollPane.class).getFirst();
            JLabel emptyMessage = findComponents(panel, JLabel.class).stream()
                    .filter(label -> label.getText().contains("Wyszukaj trasę"))
                    .findFirst().orElseThrow();

            assertEquals("Proponowane warianty tras",
                    routeList.getAccessibleContext().getAccessibleName());
            assertFalse(routeScrollPane.isVisible());
            assertTrue(emptyMessage.isVisible());
        });

        SwingUtilities.invokeAndWait(() -> panel.setRoutes(List.of()));

        SwingUtilities.invokeAndWait(() -> {
            JList<?> routeList = findComponents(panel, JList.class).getFirst();
            JScrollPane routeScrollPane = findComponents(panel, JScrollPane.class).getFirst();
            JLabel emptyMessage = findComponents(panel, JLabel.class).stream()
                    .filter(label -> label.getText().contains("Nie znaleziono"))
                    .findFirst().orElseThrow();
            assertEquals(0, routeList.getModel().getSize());
            assertFalse(routeScrollPane.isVisible());
            assertTrue(emptyMessage.isVisible());
        });
    }

    @Test
    @DisplayName("Wybór wariantu przekazuje trasę do mapy, a odznaczenie ją czyści")
    void connectsSelectedRouteToMapPanel() throws Exception {
        RoutingEngine.ScheduledRoute route = scheduledRoute(
                0, 8, 0, 8, 25, "17", "Centrum", "A", "B");
        MapPanel[] mapPanelRef = new MapPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            mapPanelRef[0] = new MapPanel();
            mapPanelRef[0].setSize(800, 600);
            mapPanelRef[0].doLayout();
            mapPanelRef[0].getMapViewer().setSize(800, 600);
        });
        MapPanel mapPanel = mapPanelRef[0];
        AtomicBoolean additionalOverlayPainted = new AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> {
            mapPanel.setAdditionalOverlayPainter((graphics, map, width, height) ->
                    additionalOverlayPainted.set(true));
            panel.connectMapPanel(mapPanel);
            panel.setRoutes(List.of(route));
        });
        SwingUtilities.invokeAndWait(() -> {
            JList<?> routeList = findComponents(panel, JList.class).getFirst();
            routeList.setSelectedIndex(0);
            assertSame(route, mapPanel.getHighlightedRoute());
            BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                mapPanel.getMapViewer().getOverlayPainter().paint(
                        graphics, mapPanel.getMapViewer(), image.getWidth(), image.getHeight());
            } finally {
                graphics.dispose();
            }
            assertTrue(containsRouteColor(image));
            assertTrue(additionalOverlayPainted.get());

            routeList.clearSelection();
            assertNull(mapPanel.getHighlightedRoute());
        });
    }

    private static RoutingEngine.ScheduledRoute scheduledRoute(int transfers,
                                                                int departureHour,
                                                                int departureMinute,
                                                                int arrivalHour,
                                                                int arrivalMinute,
                                                                String... legData) {
        List<RoutingEngine.ScheduledLeg> legs = new ArrayList<>();
        LocalDateTime firstDeparture = LocalDateTime.of(
                2026, 10, 5, departureHour, departureMinute);
        LocalDateTime lastArrival = LocalDateTime.of(
                2026, 10, 5, arrivalHour, arrivalMinute);
        for (int i = 0; i < legData.length; i += 4) {
            Stop from = stop(legData[i + 2]);
            Stop to = stop(legData[i + 3]);
            LocalDateTime departure = i == 0
                    ? firstDeparture : LocalDateTime.of(2026, 10, 5, 8, 20);
            LocalDateTime arrival = i + 4 == legData.length
                    ? lastArrival : LocalDateTime.of(2026, 10, 5, 8, 15);
            legs.add(new RoutingEngine.ScheduledLeg(new RoutingEngine.RouteLeg(
                    legData[i], legData[i + 1], List.of(from, to)), departure, arrival));
        }
        return new RoutingEngine.ScheduledRoute(transfers, legs);
    }

    private static Stop stop(String id) {
        int offset = Math.floorMod(id.hashCode(), 100);
        return new Stop(id, "Stop " + id, "01",
                52.0 + offset * 0.001, 21.0 + offset * 0.001, true);
    }

    private static boolean containsRouteColor(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color color = new Color(image.getRGB(x, y), true);
                if (color.getRed() > 180 && color.getGreen() < 100 && color.getBlue() < 100) {
                    return true;
                }
            }
        }
        return false;
    }

    private static <T> Component renderItem(JList<T> list, int index) {
        return list.getCellRenderer().getListCellRendererComponent(
                list, list.getModel().getElementAt(index), index, false, false);
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
