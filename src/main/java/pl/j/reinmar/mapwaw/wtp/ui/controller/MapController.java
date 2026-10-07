package pl.j.reinmar.mapwaw.wtp.ui.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.j.reinmar.mapwaw.wtp.model.Departure;
import pl.j.reinmar.mapwaw.wtp.model.Stop;
import pl.j.reinmar.mapwaw.wtp.repository.ScheduleRepository;
import pl.j.reinmar.mapwaw.wtp.service.DelayCalculatorService;
import pl.j.reinmar.mapwaw.wtp.ui.component.DepartureTableModel.DepartureRow;
import pl.j.reinmar.mapwaw.wtp.ui.view.DepartureBoardPanel;
import pl.j.reinmar.mapwaw.wtp.ui.view.MapPanel;

import javax.swing.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MapController {

    private static final Logger logger = LoggerFactory.getLogger(MapController.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final DepartureBoardPanel departureBoardPanel;
    private final ScheduleRepository scheduleRepository;
    private final DelayCalculatorService delayCalculatorService;
    private MapPanel mapPanel;

    private Stop currentlySelectedStop;

    public MapController(DepartureBoardPanel departureBoardPanel,
                         ScheduleRepository scheduleRepository,
                         DelayCalculatorService delayCalculatorService) {
        this.departureBoardPanel = departureBoardPanel;
        this.scheduleRepository = scheduleRepository;
        this.delayCalculatorService = delayCalculatorService;
    }

    public void setMapPanel(MapPanel mapPanel) {
        this.mapPanel = mapPanel;
    }

    public void onStopSelectedOnMap(Stop selectedStop) {
        if (selectedStop == null) return;

        this.currentlySelectedStop = selectedStop;
        logger.info("Wybrano przystanek na mapie: {} [{}]", selectedStop.getName(), selectedStop.getId());

        departureBoardPanel.setStopHeader(selectedStop.getName() + " " + selectedStop.getCode());
        refreshDepartureBoard();
    }

    public void clearSelection() {
        this.currentlySelectedStop = null;
        departureBoardPanel.setStopHeader("Wybierz przystanek z mapy / szukaj");
        departureBoardPanel.getTableModel().clear();

        if (mapPanel != null) {
            SwingUtilities.invokeLater(() -> {
                mapPanel.clearSelectedStop();
                mapPanel.repaint();
            });
        }
    }

    public void refreshDepartureBoard() {
        if (currentlySelectedStop == null) return;

        SwingUtilities.invokeLater(() -> {
            LocalTime now = LocalTime.now();
            List<Departure> departures = scheduleRepository.getDeparturesForStop(currentlySelectedStop.getId());

            if (departures == null || departures.isEmpty()) {
                departureBoardPanel.getTableModel().setDepartures(Collections.emptyList());
                return;
            }

            // Pobranie stanu filtrów z widoku
            boolean showBuses = departureBoardPanel.isBusFilterActive();
            boolean showTrams = departureBoardPanel.isTramFilterActive();
            boolean showMetro = departureBoardPanel.isMetroFilterActive();

            // Zastosowanie filtracji i mapowanie do wierszy tabeli
            List<DepartureRow> rows = departures.stream()
                    .filter(dep -> isTransportTypeAllowed(dep, showBuses, showTrams, showMetro))
                    .map(dep -> mapToDepartureRow(dep, now))
                    .collect(Collectors.toList());

            departureBoardPanel.getTableModel().setDepartures(rows);
        });
    }

    /**
     * KROK 75: Weryfikacja typu transportu na podstawie numeru linii pojazdu.
     */
    private boolean isTransportTypeAllowed(Departure dep, boolean showBuses, boolean showTrams, boolean showMetro) {
        String line = (dep.getLine() != null) ? dep.getLine().getLineNumber() : "";
        if (line == null || line.isBlank()) return true;

        line = line.trim().toUpperCase();

        // Metro przyjmuje oznaczenia literowe, np. M1, M2
        boolean isMetro = line.startsWith("M");

        boolean isTram = false;
        try {
            int lineNum = Integer.parseInt(line);
            // Zwykle linie tramwajowe to jedno lub dwucyfrowe numery poniżej 100
            isTram = (lineNum > 0 && lineNum < 100);
        } catch (NumberFormatException ignored) {
            // Linie znakowe (N, L, E) zostaną zakwalifikowane jako autobus, jeśli to nie Metro
        }

        // Jeśli nie jest to ani metro, ani tramwaj, traktujemy jako autobus (np. 507, 175)
        boolean isBus = !isMetro && !isTram;

        if (isMetro && showMetro) return true;
        if (isTram && showTrams) return true;
        if (isBus && showBuses) return true;

        return false;
    }

    private DepartureRow mapToDepartureRow(Departure dep, LocalTime now) {
        String line = (dep.getLine() != null) ? dep.getLine().getLineNumber() : "-";
        String direction = (currentlySelectedStop.getName() != null) ? currentlySelectedStop.getName() : "Nieznany";
        String scheduledTime = (dep.getDepartureTime() != null) ? dep.getDepartureTime().format(TIME_FORMATTER) : "-";

        int delaySec = 0; // Logika opóźnień (Krok 72)
        LocalTime estimatedTime = (dep.getDepartureTime() != null) ? dep.getDepartureTime().plusSeconds(delaySec) : now;

        return new DepartureRow(line, direction, scheduledTime, estimatedTime.format(TIME_FORMATTER), formatDelay(delaySec));
    }

    private String formatDelay(int delaySec) {
        if (delaySec > 30) return "+" + (delaySec / 60) + " min";
        if (delaySec < -30) return (delaySec / 60) + " min";
        return "na czas";
    }

    public Stop getCurrentlySelectedStop() {
        return currentlySelectedStop;
    }
}