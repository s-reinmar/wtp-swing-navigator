package pl.j.reinmar.mapwaw.wtp.ui.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.j.reinmar.mapwaw.wtp.model.Departure;
import pl.j.reinmar.mapwaw.wtp.model.Stop;
import pl.j.reinmar.mapwaw.wtp.repository.ScheduleRepository;
import pl.j.reinmar.mapwaw.wtp.service.DelayCalculatorService;
import pl.j.reinmar.mapwaw.wtp.ui.component.DepartureTableModel.DepartureRow;
import pl.j.reinmar.mapwaw.wtp.ui.view.DepartureBoardPanel;
import pl.j.reinmar.mapwaw.wtp.ui.view.MapPanel; // Importujemy widok mapy, aby móc ją odświeżać

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
    private MapPanel mapPanel; // Dodano opcjonalną referencję do MapPanel, by reagować na czyszczenie

    private Stop currentlySelectedStop;

    public MapController(DepartureBoardPanel departureBoardPanel,
                         ScheduleRepository scheduleRepository,
                         DelayCalculatorService delayCalculatorService) {
        this.departureBoardPanel = departureBoardPanel;
        this.scheduleRepository = scheduleRepository;
        this.delayCalculatorService = delayCalculatorService;
    }

    // Nowa metoda do opcjonalnego wpięcia odniesienia do mapy (wywoływana w MainFrame)
    public void setMapPanel(MapPanel mapPanel) {
        this.mapPanel = mapPanel;
    }

    public void onStopSelectedOnMap(Stop selectedStop) {
        if (selectedStop == null) {
            logger.warn("Otrzymano puste zdarzenie wyboru przystanku z mapy.");
            return;
        }

        this.currentlySelectedStop = selectedStop;
        logger.info("Wybrano przystanek na mapie: {} [{}]", selectedStop.getName(), selectedStop.getId());

        departureBoardPanel.setStopHeader(selectedStop.getName() + " " + selectedStop.getCode());
        refreshDepartureBoard();
    }

    /**
     * Krok 74: Metoda wywoływana przyciskiem "Wyczyść" – wraca do widoku ogólnego.
     */
    public void clearSelection() {
        logger.info("Wyczyszczono zaznaczenie przystanku. Powrót do widoku ogólnego.");

        // 1. Zresetuj aktualnie wybrany przystanek
        this.currentlySelectedStop = null;

        // 2. Zresetuj nagłówek w UI
        departureBoardPanel.setStopHeader("Wybierz przystanek z mapy...");

        // 3. Wyczyść model tabeli (JTable)
        departureBoardPanel.getTableModel().clear();

        // 4. Jeśli wpięto MapPanel (Krok 61 i 72), wywołaj odświeżenie mapy
        if (mapPanel != null) {
            SwingUtilities.invokeLater(() -> {
                // Ta metoda (clearRouteSelection lub clearSelectedStop) powinna znajdować się w MapPanel,
                // np. na wzór clearRouteSelection() z notatnika OsmZtmTransferRouteMapApp.
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

            List<DepartureRow> rows = departures.stream()
                    .map(dep -> mapToDepartureRow(dep, now))
                    .collect(Collectors.toList());

            departureBoardPanel.getTableModel().setDepartures(rows);
        });
    }

    private DepartureRow mapToDepartureRow(Departure dep, LocalTime now) {
        String line = (dep.getLine() != null) ? dep.getLine().getLineNumber() : "-";
        String direction = (currentlySelectedStop.getName() != null) ? currentlySelectedStop.getName() : "Nieznany";
        String scheduledTime = (dep.getDepartureTime() != null) ? dep.getDepartureTime().format(TIME_FORMATTER) : "-";

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