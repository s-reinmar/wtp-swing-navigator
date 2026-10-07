package pl.j.reinmar.mapwaw.wtp.ui.view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DepartureBoardPanelUsabilityTest {

    private DepartureBoardPanel panel;

    @BeforeEach
    void setUp() throws Exception {
        SwingUtilities.invokeAndWait(() -> panel = new DepartureBoardPanel());
        SwingUtilities.invokeAndWait(() -> { });
    }

    @Test
    @DisplayName("Zapewnia filtrowanie i sortowanie przez czytelne, domyślnie aktywne kontrolki")
    void exposesClearlyLabeledFiltersAndSortingControls() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<JCheckBox> filters = findComponents(panel, JCheckBox.class);
            assertEquals(3, filters.size());
            assertEquals(List.of("Autobusy", "Tramwaje", "Metro"),
                    filters.stream().map(AbstractButton::getText).toList());
            assertTrue(filters.stream().allMatch(AbstractButton::isSelected));

            JComboBox<?> sortOptions = findComponents(panel, JComboBox.class).getFirst();
            assertEquals(2, sortOptions.getItemCount());
            assertEquals(DepartureBoardPanel.SortOption.ARRIVAL_TIME, sortOptions.getItemAt(0));
            assertEquals(DepartureBoardPanel.SortOption.LINE, sortOptions.getItemAt(1));

            JLabel sortLabel = findComponents(panel, JLabel.class).stream()
                    .filter(label -> "Sortuj:".equals(label.getText()))
                    .findFirst()
                    .orElseThrow();
            assertSame(sortOptions, sortLabel.getLabelFor());
        });
    }

    @Test
    @DisplayName("Ułatwia odczyt i obsługę klawiaturą tabeli odjazdów")
    void configuresTableForReadableKeyboardFriendlySelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTable table = findComponents(panel, JTable.class).getFirst();

            assertEquals(28, table.getRowHeight());
            assertTrue(table.isFocusable());
            assertTrue(table.getRowSelectionAllowed());
            assertFalse(table.getColumnSelectionAllowed());
            assertEquals(ListSelectionModel.SINGLE_SELECTION, table.getSelectionModel().getSelectionMode());
            assertFalse(table.isCellEditable(0, 0));
            assertEquals("Tablica odjazdów",
                    table.getAccessibleContext().getAccessibleName());
            assertFalse(table.getAccessibleContext().getAccessibleDescription().isBlank());
            assertFalse(table.getTableHeader().getReorderingAllowed());

            assertEquals(List.of("Linia", "Kierunek", "Czas rozkładowy", "Estymacja (GPS)", "Opóźnienie"),
                    java.util.stream.IntStream.range(0, table.getColumnCount())
                            .mapToObj(table::getColumnName)
                            .toList());
        });
    }

    @Test
    @DisplayName("Pozwala wrócić do mapy z klawiatury i przełączać stan pusty oraz tabelę")
    void keepsClearActionKeyboardAccessibleAndSupportsEmptyStateTransitions() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JButton clearButton = findComponents(panel, JButton.class).stream()
                    .filter(button -> "Wyczyść / Powrót".equals(button.getText()))
                    .findFirst()
                    .orElseThrow();
            assertTrue(clearButton.isFocusable());
            JScrollPane tableScrollPane = findComponents(panel, JScrollPane.class).getFirst();
            JLabel panelEmptyMessage = findComponents(panel, JLabel.class).stream()
                    .filter(label -> label.getText().equals("Wybierz przystanek z mapy, aby zobaczyć odjazdy."))
                    .findFirst()
                    .orElseThrow();
            assertTrue(panelEmptyMessage.getParent().isVisible());
            assertFalse(tableScrollPane.isVisible());
        });

        panel.showTable();
        SwingUtilities.invokeAndWait(() -> {
            JScrollPane panelScrollPane = findComponents(panel, JScrollPane.class).getFirst();
            JLabel panelEmptyMessage = findComponents(panel, JLabel.class).stream()
                    .filter(label -> label.getText().equals("Wybierz przystanek z mapy, aby zobaczyć odjazdy."))
                    .findFirst()
                    .orElseThrow();
            assertTrue(panelScrollPane.isVisible());
            assertFalse(panelEmptyMessage.getParent().isVisible());
        });

        panel.showEmptyMessage("Brak odjazdów dla wybranego przystanku.");
        SwingUtilities.invokeAndWait(() -> {
            JScrollPane panelScrollPane = findComponents(panel, JScrollPane.class).getFirst();
            JLabel panelEmptyMessage = findComponents(panel, JLabel.class).stream()
                    .filter(label -> label.getText().equals("Brak odjazdów dla wybranego przystanku."))
                    .findFirst()
                    .orElseThrow();
            assertFalse(panelScrollPane.isVisible());
            assertTrue(panelEmptyMessage.getParent().isVisible());
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
