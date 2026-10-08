package pl.j.reinmar.mapwaw.wtp.ui.view;

import pl.j.reinmar.mapwaw.wtp.service.RoutingEngine;

import javax.swing.*;
import java.awt.*;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Lista wariantów tras znalezionych przez planer.
 */
public class RouteResultsPanel extends JPanel {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM HH:mm");
    private final DefaultListModel<RoutingEngine.ScheduledRoute> listModel =
            new DefaultListModel<>();
    private final JList<RoutingEngine.ScheduledRoute> routeList = new JList<>(listModel);
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentPanel = new JPanel(cardLayout);
    private final RouteItineraryPanel itineraryPanel = new RouteItineraryPanel();
    private final JLabel emptyMessage = new JLabel(
            "Wyszukaj trasę, aby zobaczyć proponowane warianty.", SwingConstants.CENTER);
    private Consumer<RoutingEngine.ScheduledRoute> onRouteSelected;

    public RouteResultsPanel() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Proponowane trasy"),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));

        routeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        routeList.setCellRenderer(new RouteCellRenderer());
        routeList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                RoutingEngine.ScheduledRoute route = routeList.getSelectedValue();
                itineraryPanel.setRoute(route);
                if (onRouteSelected != null) {
                    onRouteSelected.accept(route);
                }
            }
        });
        routeList.getAccessibleContext().setAccessibleName("Proponowane warianty tras");
        routeList.getAccessibleContext().setAccessibleDescription(
                "Lista tras z godziną wyjazdu i przyjazdu, czasem podróży, liczbą przystanków oraz przesiadek.");
        routeList.setVisibleRowCount(4);

        emptyMessage.setForeground(Color.GRAY);
        emptyMessage.setFont(emptyMessage.getFont().deriveFont(Font.ITALIC));
        contentPanel.add(emptyMessage, "EMPTY");
        JScrollPane routeScrollPane = new JScrollPane(routeList);
        routeScrollPane.setPreferredSize(new Dimension(300, 180));
        JSplitPane resultsAndItinerary = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                routeScrollPane, itineraryPanel);
        resultsAndItinerary.setResizeWeight(0.4);
        resultsAndItinerary.setContinuousLayout(true);
        contentPanel.add(resultsAndItinerary, "RESULTS");
        add(contentPanel, BorderLayout.CENTER);
        showEmptyState();
    }

    public void setRoutes(List<RoutingEngine.ScheduledRoute> routes) {
        Objects.requireNonNull(routes, "routes");
        routeList.clearSelection();
        listModel.clear();
        routes.forEach(route -> listModel.addElement(
                Objects.requireNonNull(route, "route")));
        if (listModel.isEmpty()) {
            notifyRouteCleared();
            showEmptyState();
            emptyMessage.setText("Nie znaleziono pasujących tras.");
        } else {
            cardLayout.show(contentPanel, "RESULTS");
        }
    }

    public List<RoutingEngine.ScheduledRoute> getRoutes() {
        return java.util.Collections.list(listModel.elements());
    }

    public RouteItineraryPanel getItineraryPanel() {
        return itineraryPanel;
    }

    public void setOnRouteSelected(Consumer<RoutingEngine.ScheduledRoute> onRouteSelected) {
        this.onRouteSelected = onRouteSelected;
    }

    public void connectMapPanel(MapPanel mapPanel) {
        Objects.requireNonNull(mapPanel, "mapPanel");
        setOnRouteSelected(route -> {
            if (route == null) {
                mapPanel.clearHighlightedRoute();
            } else {
                mapPanel.highlightRoute(route);
            }
        });
    }

    public void clearSelection() {
        routeList.clearSelection();
        notifyRouteCleared();
    }

    private void notifyRouteCleared() {
        if (onRouteSelected != null) {
            onRouteSelected.accept(null);
        }
    }

    private void showEmptyState() {
        emptyMessage.setText("Wyszukaj trasę, aby zobaczyć proponowane warianty.");
        cardLayout.show(contentPanel, "EMPTY");
    }

    private static String formatTime(RoutingEngine.ScheduledRoute route, boolean arrival) {
        var dateTime = arrival ? route.arrivalDateTime() : route.departureDateTime();
        return route.departureDateTime().toLocalDate().equals(dateTime.toLocalDate())
                ? TIME_FORMATTER.format(dateTime)
                : DATE_TIME_FORMATTER.format(dateTime);
    }

    private static String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;
        StringBuilder text = new StringBuilder();
        if (hours > 0) {
            text.append(hours).append(" godz. ");
        }
        if (minutes > 0 || hours > 0) {
            text.append(minutes).append(" min");
        }
        if (remainingSeconds > 0 && hours == 0) {
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append(remainingSeconds).append(" s");
        }
        return text.isEmpty() ? "0 min" : text.toString();
    }

    private static String formatTransfers(int count) {
        return switch (count) {
            case 0 -> "bez przesiadek";
            case 1 -> "1 przesiadka";
            case 2, 3, 4 -> count + " przesiadki";
            default -> count + " przesiadek";
        };
    }

    private static String formatStops(int count) {
        return switch (count) {
            case 1 -> "1 przystanek";
            case 2, 3, 4 -> count + " przystanki";
            default -> count + " przystanków";
        };
    }

    private static String formatLegs(RoutingEngine.ScheduledRoute route) {
        return route.legs().stream()
                .map(leg -> {
                    String line = leg.route().lineNumber() == null
                            ? "?" : leg.route().lineNumber();
                    String direction = leg.route().directionName();
                    return direction == null || direction.isBlank()
                            ? line : line + " → " + direction;
                })
                .collect(Collectors.joining("  |  "));
    }

    private static final class RouteCellRenderer extends JPanel
            implements ListCellRenderer<RoutingEngine.ScheduledRoute> {

        private final JLabel legsLabel = new JLabel();
        private final JLabel summaryLabel = new JLabel();
        private final JLabel walkingLabel = new JLabel();

        private RouteCellRenderer() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            legsLabel.setFont(legsLabel.getFont().deriveFont(Font.BOLD));
            summaryLabel.setFont(summaryLabel.getFont().deriveFont(Font.PLAIN, 11f));
            walkingLabel.setFont(walkingLabel.getFont().deriveFont(Font.PLAIN, 10f));
            add(legsLabel);
            add(summaryLabel);
            add(walkingLabel);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends RoutingEngine.ScheduledRoute> list,
                                                       RoutingEngine.ScheduledRoute route, int index,
                                                       boolean isSelected, boolean cellHasFocus) {
            legsLabel.setText(formatLegs(route));
            summaryLabel.setText(formatTime(route, false) + " – " + formatTime(route, true)
                    + "  ·  " + formatDuration(route.totalTravelTime())
                    + "  ·  " + formatStops(route.numberOfStops())
                    + "  ·  " + formatTransfers(route.transfers()));

            Duration walkingTime = route.totalWalkingTransferTime();
            walkingLabel.setText(walkingTime.isZero() ? " "
                    : "Dojście między słupkami: " + formatDuration(walkingTime));
            walkingLabel.setVisible(!walkingTime.isZero());

            Color background = isSelected ? list.getSelectionBackground() : list.getBackground();
            Color foreground = isSelected ? list.getSelectionForeground() : list.getForeground();
            setBackground(background);
            legsLabel.setForeground(foreground);
            summaryLabel.setForeground(foreground);
            walkingLabel.setForeground(isSelected ? foreground : Color.GRAY);
            setOpaque(true);
            getAccessibleContext().setAccessibleName(
                    "Linie " + formatLegs(route) + ", " + summaryLabel.getText());
            return this;
        }
    }
}
