package pl.j.reinmar.mapwaw.wtp.ui.view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.j.reinmar.mapwaw.wtp.model.Stop;
import pl.j.reinmar.mapwaw.wtp.repository.ScheduleRepository;
import pl.j.reinmar.mapwaw.wtp.ui.component.StopSearchTextField;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoutePlannerPanelTest {

    private RoutePlannerPanel panel;

    @BeforeEach
    void setUp() throws Exception {
        ScheduleRepository repository = new ScheduleRepository();
        repository.addStop(new Stop("A", "Początek", "01", 52.0, 21.0, true));
        repository.addStop(new Stop("B", "Koniec", "02", 52.1, 21.1, true));
        SwingUtilities.invokeAndWait(() -> panel = new RoutePlannerPanel(repository));
    }

    @Test
    @DisplayName("Udostępnia pola przystanków, godzinę wyjazdu i akcję wyszukiwania")
    void exposesAccessibleRouteSearchControls() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<JLabel> labels = findComponents(panel, JLabel.class);
            JLabel originLabel = labels.stream()
                    .filter(label -> "Z:".equals(label.getText()))
                    .findFirst().orElseThrow();
            JLabel destinationLabel = labels.stream()
                    .filter(label -> "Do:".equals(label.getText()))
                    .findFirst().orElseThrow();
            JLabel timeLabel = labels.stream()
                    .filter(label -> "Godzina wyjazdu:".equals(label.getText()))
                    .findFirst().orElseThrow();
            List<StopSearchTextField> stopFields =
                    findComponents(panel, StopSearchTextField.class);
            JSpinner timeSpinner = findComponents(panel, JSpinner.class).getFirst();
            JButton searchButton = findComponents(panel, JButton.class).stream()
                    .filter(button -> "Szukaj trasy".equals(button.getText()))
                    .findFirst().orElseThrow();

            assertEquals(2, stopFields.size());
            assertSame(stopFields.get(0), originLabel.getLabelFor());
            assertSame(stopFields.get(1), destinationLabel.getLabelFor());
            assertSame(timeSpinner, timeLabel.getLabelFor());
            assertEquals("HH:mm", ((JSpinner.DateEditor) timeSpinner.getEditor()).getFormat().toPattern());
            assertEquals("Godzina wyjazdu", timeSpinner.getAccessibleContext().getAccessibleName());
            assertEquals("Szukaj trasy", searchButton.getText());
            assertFalse(searchButton.isEnabled());
        });
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
