package pl.j.reinmar.mapwaw.wtp.ui.view;

import pl.j.reinmar.mapwaw.wtp.model.Stop;
import pl.j.reinmar.mapwaw.wtp.repository.ScheduleRepository;
import pl.j.reinmar.mapwaw.wtp.ui.component.StopSearchTextField;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.function.Consumer;

/**
 * Panel do wskazania przystanków początkowego i końcowego oraz godziny wyjazdu.
 */
public class RoutePlannerPanel extends JPanel {

    private final StopSearchTextField originField;
    private final StopSearchTextField destinationField;
    private final JSpinner departureTimeSpinner;
    private final JButton searchButton;
    private final JLabel validationLabel;
    private Stop selectedOrigin;
    private Stop selectedDestination;
    private Consumer<RouteRequest> onRouteRequested;

    public RoutePlannerPanel(ScheduleRepository scheduleRepository) {
        if (scheduleRepository == null) {
            throw new IllegalArgumentException("Repozytorium rozkładu nie może być puste.");
        }
        originField = new StopSearchTextField(scheduleRepository);
        destinationField = new StopSearchTextField(scheduleRepository);
        departureTimeSpinner = createDepartureTimeSpinner();
        searchButton = new JButton("Szukaj trasy");
        validationLabel = new JLabel(" ");

        initUI();
        initListeners();
    }

    private void initUI() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Planowanie trasy"),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));

        JPanel fieldsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(4, 0, 4, 8);

        JLabel originLabel = new JLabel("Z:");
        originLabel.setLabelFor(originField);
        fieldsPanel.add(originLabel, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(4, 0, 4, 0);
        fieldsPanel.add(originField, constraints);

        JLabel destinationLabel = new JLabel("Do:");
        destinationLabel.setLabelFor(destinationField);
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.insets = new Insets(4, 0, 4, 8);
        fieldsPanel.add(destinationLabel, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(4, 0, 4, 0);
        fieldsPanel.add(destinationField, constraints);

        JLabel timeLabel = new JLabel("Godzina wyjazdu:");
        timeLabel.setLabelFor(departureTimeSpinner);
        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.insets = new Insets(4, 0, 4, 8);
        fieldsPanel.add(timeLabel, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(4, 0, 4, 0);
        fieldsPanel.add(departureTimeSpinner, constraints);

        add(fieldsPanel, BorderLayout.CENTER);

        JPanel actionPanel = new JPanel(new BorderLayout(4, 4));
        validationLabel.setForeground(new Color(170, 40, 40));
        validationLabel.setFont(validationLabel.getFont().deriveFont(Font.PLAIN, 11f));
        actionPanel.add(validationLabel, BorderLayout.CENTER);
        actionPanel.add(searchButton, BorderLayout.SOUTH);
        add(actionPanel, BorderLayout.SOUTH);
        searchButton.setEnabled(false);
    }

    private void initListeners() {
        originField.setOnStopSelectedListener(stop -> {
            selectedOrigin = stop;
            updateSearchButton();
        });
        destinationField.setOnStopSelectedListener(stop -> {
            selectedDestination = stop;
            updateSearchButton();
        });
        clearSelectionWhenEdited(originField, true);
        clearSelectionWhenEdited(destinationField, false);
        searchButton.addActionListener(event -> requestRoute());
    }

    private void clearSelectionWhenEdited(StopSearchTextField field, boolean origin) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                clearSelection();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                clearSelection();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                clearSelection();
            }

            private void clearSelection() {
                if (origin) {
                    selectedOrigin = null;
                } else {
                    selectedDestination = null;
                }
                updateSearchButton();
            }
        });
    }

    private void updateSearchButton() {
        searchButton.setEnabled(selectedOrigin != null && selectedDestination != null);
        if (searchButton.isEnabled()) {
            validationLabel.setText(" ");
        }
    }

    private void requestRoute() {
        if (selectedOrigin == null || selectedDestination == null) {
            validationLabel.setText("Wybierz przystanki z listy podpowiedzi.");
            return;
        }
        if (selectedOrigin.getId().equals(selectedDestination.getId())) {
            validationLabel.setText("Wybierz różne przystanki początkowy i końcowy.");
            return;
        }
        if (onRouteRequested != null) {
            LocalTime departureTime = ((Date) departureTimeSpinner.getValue()).toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0);
            onRouteRequested.accept(new RouteRequest(selectedOrigin, selectedDestination,
                    LocalDateTime.of(LocalDate.now(), departureTime)));
        }
    }

    private static JSpinner createDepartureTimeSpinner() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        SpinnerDateModel model = new SpinnerDateModel(calendar.getTime(), null, null, Calendar.MINUTE);
        JSpinner spinner = new JSpinner(model);
        spinner.setEditor(new JSpinner.DateEditor(spinner, "HH:mm"));
        spinner.getAccessibleContext().setAccessibleName("Godzina wyjazdu");
        spinner.getAccessibleContext().setAccessibleDescription(
                "Wybierz godzinę wyjazdu w lokalnej strefie czasowej.");
        return spinner;
    }

    public void setOnRouteRequested(Consumer<RouteRequest> onRouteRequested) {
        this.onRouteRequested = onRouteRequested;
    }

    public record RouteRequest(Stop origin, Stop destination, LocalDateTime departureDateTime) {
    }
}
