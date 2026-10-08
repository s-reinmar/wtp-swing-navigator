package pl.j.reinmar.mapwaw.wtp.ui.view;

import pl.j.reinmar.mapwaw.wtp.model.Stop;
import pl.j.reinmar.mapwaw.wtp.service.RoutingEngine;

import javax.swing.*;
import java.awt.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Pokazuje szczegółowy przebieg wybranej trasy i instrukcje przesiadek.
 */
public class RouteItineraryPanel extends JPanel {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private final JLabel emptyMessage = new JLabel(
            "Wybierz wariant trasy, aby zobaczyć szczegóły.", SwingConstants.CENTER);
    private final JPanel stepsPanel = new JPanel();
    private RoutingEngine.ScheduledRoute route;

    public RouteItineraryPanel() {
        setLayout(new BorderLayout(6, 6));
        setBorder(BorderFactory.createTitledBorder("Szczegółowy opis trasy"));
        emptyMessage.setForeground(Color.GRAY);
        emptyMessage.setFont(emptyMessage.getFont().deriveFont(Font.ITALIC));
        stepsPanel.setLayout(new BoxLayout(stepsPanel, BoxLayout.Y_AXIS));
        add(emptyMessage, BorderLayout.CENTER);
        getAccessibleContext().setAccessibleName("Szczegółowy opis trasy");
    }

    public void setRoute(RoutingEngine.ScheduledRoute route) {
        this.route = route;
        removeAll();
        if (route == null) {
            add(emptyMessage, BorderLayout.CENTER);
        } else {
            stepsPanel.removeAll();
            for (int i = 0; i < route.legs().size(); i++) {
                stepsPanel.add(createLegPanel(route.legs().get(i), i));
                if (i < route.legs().size() - 1) {
                    stepsPanel.add(createTransferPanel(route, i));
                }
            }
            JScrollPane scrollPane = new JScrollPane(stepsPanel);
            scrollPane.setBorder(BorderFactory.createEmptyBorder());
            scrollPane.getVerticalScrollBar().setUnitIncrement(16);
            add(scrollPane, BorderLayout.CENTER);
        }
        revalidate();
        repaint();
    }

    public RoutingEngine.ScheduledRoute getRoute() {
        return route;
    }

    private JPanel createLegPanel(RoutingEngine.ScheduledLeg leg, int index) {
        List<Stop> stops = leg.route().stops();
        Stop boardingStop = stops.getFirst();
        Stop alightingStop = stops.getLast();
        String line = leg.route().lineNumber() == null ? "?" : leg.route().lineNumber();
        String direction = leg.route().directionName();
        String title = "Odcinek " + (index + 1) + ": linia " + line;
        if (direction != null && !direction.isBlank()) {
            title += " w kierunku " + direction;
        }

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(210, 210, 210)),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.getAccessibleContext().setAccessibleName(title);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        addDetail(panel, titleLabel);
        addDetail(panel, "Wsiądź: " + formatStop(boardingStop));
        addDetail(panel, "Odjazd: " + formatDateTime(leg.departureDateTime()));
        addDetail(panel, "Wysiądź: " + formatStop(alightingStop));
        addDetail(panel, "Przyjazd: " + formatDateTime(leg.arrivalDateTime()));
        if (stops.size() > 2) {
            addDetail(panel, "Przystanki po drodze: " + stops.subList(1, stops.size() - 1)
                    .stream().map(RouteItineraryPanel::formatStop)
                    .reduce((first, second) -> first + ", " + second).orElse(""));
        }
        return panel;
    }

    private JPanel createTransferPanel(RoutingEngine.ScheduledRoute route, int transferIndex) {
        RoutingEngine.ScheduledLeg arrivingLeg = route.legs().get(transferIndex);
        RoutingEngine.ScheduledLeg departingLeg = route.legs().get(transferIndex + 1);
        Stop alightingStop = arrivingLeg.route().stops().getLast();
        Stop boardingStop = departingLeg.route().stops().getFirst();
        Duration walkingTime = route.transferWalkingTime(transferIndex);
        Duration connectionWait = Duration.between(
                arrivingLeg.arrivalDateTime(), departingLeg.departureDateTime());

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, new Color(220, 135, 20)),
                BorderFactory.createEmptyBorder(5, 9, 5, 4)));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.getAccessibleContext().setAccessibleName("Przesiadka " + (transferIndex + 1));
        addDetail(panel, "Przesiadka " + (transferIndex + 1));
        if (sameStop(alightingStop, boardingStop)) {
            addDetail(panel, "Przejdź do stanowiska " + formatStop(boardingStop) + ".");
        } else {
            addDetail(panel, "Po wysiadaniu na " + formatStop(alightingStop)
                    + " przejdź na " + formatStop(boardingStop) + ".");
            addDetail(panel, "Szacowany czas dojścia: " + formatDuration(walkingTime) + ".");
        }
        addDetail(panel, "Odjazd kolejnego kursu: "
                + formatDateTime(departingLeg.departureDateTime()) + ".");
        if (!connectionWait.isNegative()) {
            addDetail(panel, "Czas od przyjazdu do odjazdu: "
                    + formatDuration(connectionWait) + ".");
        }
        return panel;
    }

    private static void addDetail(JPanel panel, String text) {
        addDetail(panel, new JLabel(text));
    }

    private static void addDetail(JPanel panel, JLabel label) {
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(label);
    }

    private static String formatStop(Stop stop) {
        String name = stop.getName() == null || stop.getName().isBlank()
                ? "Przystanek" : stop.getName().trim();
        String code = stop.getCode();
        if (code != null && !code.isBlank()) {
            return name + " (" + code.trim() + ")";
        }
        if (stop.getId() != null && !stop.getId().isBlank()) {
            return name + " [" + stop.getId() + "]";
        }
        return name;
    }

    private static String formatDateTime(LocalDateTime dateTime) {
        return DATE_TIME_FORMATTER.format(dateTime);
    }

    private static String formatDuration(Duration duration) {
        long minutes = duration.toMinutes();
        long seconds = duration.minusMinutes(minutes).toSeconds();
        if (minutes == 0) {
            return seconds + " s";
        }
        if (seconds == 0) {
            return minutes + " min";
        }
        return minutes + " min " + seconds + " s";
    }

    private static boolean sameStop(Stop first, Stop second) {
        return first.getId() != null && first.getId().equals(second.getId());
    }
}
