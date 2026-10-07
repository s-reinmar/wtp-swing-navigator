package pl.j.reinmar.mapwaw.wtp.ui.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.j.reinmar.mapwaw.wtp.model.Departure;
import pl.j.reinmar.mapwaw.wtp.model.Stop;
import pl.j.reinmar.mapwaw.wtp.repository.ScheduleRepository;
import pl.j.reinmar.mapwaw.wtp.service.DelayCalculatorService;
import pl.j.reinmar.mapwaw.wtp.ui.component.DepartureTableModel.DepartureRow;
import pl.j.reinmar.mapwaw.wtp.ui.view.DepartureBoardPanel;

import javax.swing.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Kontroler łączący zdarzenia z interaktywnej mapy z panelem bocznym tablicy odjazdów.
 */
public class MapController {

    private static final Logger logger = LoggerFactory.getLogger(MapController.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final DepartureBoardPanel departureBoardPanel;
    private final ScheduleRepository scheduleRepository;
    private final DelayCalculatorService delayCalculatorService;

    private Stop currentlySelectedStop;

    public MapController(DepartureBoardPanel departureBoardPanel,
                         ScheduleRepository scheduleRepository,
                         DelayCalculatorService delayCalculatorService) {
        this.departureBoardPanel = departureBoardPanel;
        this.scheduleRepository = scheduleRepository;
        this.delayCalculatorService = delayCalculatorService;
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

    public void refreshDepartureBoard() {
        if (currentlySelectedStop == null) return;

        SwingUtilities.invokeLater(() -> {
            LocalTime now = LocalTime.now();
            List<Departure> departures = scheduleRepository.getDeparturesForStop(currentlySelectedStop.getId());

            if (departures == null || departures.isEmpty()) {
                departureBoardPanel.getTableModel().setDepartures(Collections.emptyList());
                logger.info("Brak nadchodzących odjazdów dla przystanku: {}", currentlySelectedStop.getName());
                return;
            }

            // Czyste mapowanie obiektów domenowych na wiersze tabeli
            List<DepartureRow> rows = departures.stream()
                    .map(dep -> mapToDepartureRow(dep, now))
                    .collect(Collectors.toList());

            // Przekazanie gotowej listy do modelu
            departureBoardPanel.getTableModel().setDepartures(rows);
        });
    }

    private DepartureRow mapToDepartureRow(Departure dep, LocalTime now) {
        String line = (dep.getLine() != null) ? dep.getLine().getLineNumber() : "-";
        String direction = (currentlySelectedStop.getName() != null) ? currentlySelectedStop.getName() : "Nieznany";
        String scheduledTime = (dep.getDepartureTime() != null) ? dep.getDepartureTime().format(TIME_FORMATTER) : "-";

        int delaySec = 0; // Wartość domyślna skorygowana o LiveVehiclePosition z cache
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