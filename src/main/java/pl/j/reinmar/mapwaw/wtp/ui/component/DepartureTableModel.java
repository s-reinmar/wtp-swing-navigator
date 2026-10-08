package pl.j.reinmar.mapwaw.wtp.ui.component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Customowy model tabeli (AbstractTableModel) przeznaczony dla komponentu JTable
 * wyświetlającego tablicę odjazdów z przystanku w czasie rzeczywistym.
 */
public class DepartureTableModel extends AbstractTableModel {

    private static final Logger logger = LoggerFactory.getLogger(DepartureTableModel.class);

    private static final String[] COLUMN_NAMES = {
            "Linia", "Kierunek", "Czas rozkładowy", "Estymacja (GPS)", "Opóźnienie"
    };

    private static final Class<?>[] COLUMN_CLASSES = {
            String.class, String.class, String.class, String.class, String.class
    };

    /**
     * Rekord reprezentujący pojedynczy, gotowy do wyświetlenia wiersz odjazdu w tabeli.
     */
    public record DepartureRow(
            String line,
            String direction,
            String scheduledTime,
            String estimatedTimeGps,
            String delay
    ) {}

    // Wewnętrzna lista przechowująca aktualne wiersze tabeli
    private final List<DepartureRow> departures;

    /**
     * Konstruktor tworzący pusty model tabeli odjazdów.
     */
    public DepartureTableModel() {
        this.departures = new ArrayList<>();
    }

    /**
     * Konstruktor inicjalizujący model gotową listą odjazdów.
     */
    public DepartureTableModel(List<DepartureRow> initialDepartures) {
        this.departures = new ArrayList<>(initialDepartures != null ? initialDepartures : List.of());
    }

    @Override
    public int getRowCount() {
        return departures.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return COLUMN_CLASSES[columnIndex];
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false; // Tabela jest całkowicie tylko do odczytu
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= departures.size()) {
            return null;
        }

        DepartureRow row = departures.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> row.line();
            case 1 -> row.direction();
            case 2 -> row.scheduledTime();
            case 3 -> row.estimatedTimeGps();
            case 4 -> row.delay();
            default -> null;
        };
    }

    /**
     * Pobiera pełny obiekt wiersza dla podanej pozycji w tabeli.
     */
    public DepartureRow getRow(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < departures.size()) {
            return departures.get(rowIndex);
        }
        return null;
    }

    /**
     * Główna metoda zasilająca tabelę nowymi danymi.
     * Zastępuje całą dotychczasową zawartość i bezpiecznie powiadamia UI (Swing EDT) o zmianach.
     * Ta metoda naprawia błędy z brakiem "setRowCount" i "addRow".
     */
    public void setDepartures(List<DepartureRow> newDepartures) {
        List<DepartureRow> snapshot = newDepartures == null
                ? List.of() : new ArrayList<>(newDepartures);
        SwingUtilities.invokeLater(() -> {
            this.departures.clear();
            this.departures.addAll(snapshot);
            fireTableDataChanged();
            logger.debug("Zaktualizowano tabelę odjazdów. Liczba wierszy: {}", departures.size());
        });
    }

    /**
     * Czyści wszystkie wiersze z tabeli, powiadamiając widok o usunięciu elementów.
     */
    public void clear() {
        SwingUtilities.invokeLater(() -> {
            int size = departures.size();
            if (size > 0) {
                departures.clear();
                fireTableRowsDeleted(0, size - 1);
            }
        });
    }
}