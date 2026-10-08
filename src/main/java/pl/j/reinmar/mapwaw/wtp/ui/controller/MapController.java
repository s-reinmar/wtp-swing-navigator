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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Kontroler łączący zdarzenia z interaktywnej mapy z panelem bocznym tablicy odjazdów.
 * Reaguje na kliknięcia, odpytuje repozytoria, filtruje dane i steruje widokiem (np. CardLayout).
 */
public class MapController {

    private static final Logger logger = LoggerFactory.getLogger(MapController.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final DepartureBoardPanel departureBoardPanel;
    private final ScheduleRepository scheduleRepository;
    private final DelayCalculatorService delayCalculatorService;
    private volatile MapPanel mapPanel;

    private volatile Stop currentlySelectedStop;

    public MapController(DepartureBoardPanel departureBoardPanel,
                         ScheduleRepository scheduleRepository,
                         DelayCalculatorService delayCalculatorService) {
        this.departureBoardPanel = departureBoardPanel;
        this.scheduleRepository = scheduleRepository;
        this.delayCalculatorService = delayCalculatorService;
    }

    public void setMapPanel(MapPanel mapPanel) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setMapPanel(mapPanel));
            return;
        }
        this.mapPanel = mapPanel;
    }

    public void onStopSelectedOnMap(Stop selectedStop) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> onStopSelectedOnMap(selectedStop));
            return;
        }
        if (selectedStop == null) {
            logger.warn("Otrzymano puste zdarzenie wyboru przystanku z mapy.");
            return;
        }

        this.currentlySelectedStop = selectedStop;

        // Zabezpieczenie przed NullPointerException przy braku nazwy/kodu
        String stopName = selectedStop.getName() != null ? selectedStop.getName() : "Nieznany przystanek";
        String stopCode = selectedStop.getCode() != null ? selectedStop.getCode() : "";
        String stopId = selectedStop.getId() != null ? selectedStop.getId() : "Brak ID";

        logger.info("Wybrano przystanek na mapie: {} [{}]", stopName, stopId);

        departureBoardPanel.setStopHeader(stopName + " " + stopCode);
        refreshDepartureBoard();
    }

    public void clearSelection() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::clearSelection);
            return;
        }
        this.currentlySelectedStop = null;
        departureBoardPanel.setStopHeader("Wybierz przystanek z mapy...");
        departureBoardPanel.getTableModel().clear();

        // Powrót do ekranu z instrukcją (CardLayout)
        departureBoardPanel.showEmptyMessage("Wybierz przystanek z mapy, aby zobaczyć odjazdy.");

        if (mapPanel != null) {
            SwingUtilities.invokeLater(() -> {
                mapPanel.clearSelectedStop();
                mapPanel.repaint();
            });
        }
    }

    public void refreshDepartureBoard() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::refreshDepartureBoard);
            return;
        }
        if (currentlySelectedStop == null) {
            departureBoardPanel.showEmptyMessage("Wybierz przystanek z mapy, aby zobaczyć odjazdy.");
            return;
        }

        Stop selectedStop = currentlySelectedStop;
        LocalTime now = LocalTime.now();

        List<Departure> departures = scheduleRepository.getDeparturesForStop(selectedStop.getId());

        if (departures == null || departures.isEmpty()) {
            departureBoardPanel.getTableModel().clear();
            departureBoardPanel.showEmptyMessage("Brak zaplanowanych odjazdów w najbliższym czasie.");
            return;
        }

        boolean showBuses = departureBoardPanel.isBusFilterActive();
        boolean showTrams = departureBoardPanel.isTramFilterActive();
        boolean showMetro = departureBoardPanel.isMetroFilterActive();

        List<DepartureRow> rows = departures.stream()
                .filter(dep -> isTransportTypeAllowed(dep, showBuses, showTrams, showMetro))
                .sorted(getDepartureComparator(departureBoardPanel.getSelectedSortOption()))
                .map(dep -> mapToDepartureRow(dep, selectedStop, now))
                .collect(Collectors.toList());

        if (rows.isEmpty()) {
            departureBoardPanel.getTableModel().clear();
            departureBoardPanel.showEmptyMessage("Brak odjazdów pasujących do wybranych filtrów.");
        } else {
            departureBoardPanel.showTable();
            departureBoardPanel.getTableModel().setDepartures(rows);
        }
    }

    private Comparator<Departure> getDepartureComparator(DepartureBoardPanel.SortOption option) {
        // Opóźnienie jest obecnie stałe (0), więc czas przybycia == czas rozkładowy
        Comparator<Departure> byTime = Comparator.comparing(Departure::getDepartureTime,
                Comparator.nullsLast(Comparator.naturalOrder()));
        if (option == DepartureBoardPanel.SortOption.LINE) {
            Comparator<Departure> byLine = Comparator.comparing(
                    dep -> dep.getLine() != null ? dep.getLine().getLineNumber() : null,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            return byLine.thenComparing(byTime);
        }
        return byTime;
    }

    private boolean isTransportTypeAllowed(Departure dep, boolean showBuses, boolean showTrams, boolean showMetro) {
        String line = "";
        if (dep.getLine() != null && dep.getLine().getLineNumber() != null) {
            line = dep.getLine().getLineNumber();
        }

        if (line == null || line.isBlank()) return true;

        line = line.trim().toUpperCase();
        boolean isMetro = line.startsWith("M");
        boolean isTram = false;
        try {
            int lineNum = Integer.parseInt(line);
            isTram = (lineNum > 0 && lineNum < 100);
        } catch (NumberFormatException ignored) {}

        boolean isBus = !isMetro && !isTram;

        if (isMetro && showMetro) return true;
        if (isTram && showTrams) return true;
        if (isBus && showBuses) return true;

        return false;
    }

    private DepartureRow mapToDepartureRow(Departure dep, Stop selectedStop, LocalTime now) {
        String line = (dep.getLine() != null && dep.getLine().getLineNumber() != null)
                ? dep.getLine().getLineNumber() : "-";

        // Jeśli obiekt Departure nie posiada metody getDirection(), używamy nazwy przystanku docelowego
        String direction = selectedStop.getName() != null ? selectedStop.getName() : "Nieznany";

        String scheduledTime = (dep.getDepartureTime() != null) ? dep.getDepartureTime().format(TIME_FORMATTER) : "-";

        // Tymczasowy brak błędu DelayCalculator - zwracamy 0 jako opóźnienie domyślne.
        // W przyszłości możesz tu podpiąć odpowiednią metodę serwisu opóźnień.
        int delaySec = 0;

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