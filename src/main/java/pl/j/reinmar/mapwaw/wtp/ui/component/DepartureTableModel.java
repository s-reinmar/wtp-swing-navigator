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
     * Rekord reprezentujący pojedynczy wiersz odjazdu w tabeli.
     */
    public record DepartureRow(
            String line,
            String direction,
            String scheduledTime,
            String estimatedTimeGps,
            String delay
    ) {}

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
     * Podmienia całą zawartość tabeli i powiadamia JTable o zmianie.
     */
    public void setDepartures(List<DepartureRow> newDepartures) {
        SwingUtilities.invokeLater(() -> {
            this.departures.clear();
            if (newDepartures != null) {
                this.departures.addAll(newDepartures);
            }
            fireTableDataChanged();
            logger.debug("Zaktualizowano tabelę odjazdów. Liczba wierszy: {}", departures.size());
        });
    }

    /**
     * Dodaje pojedynczy wiersz odjazdu do tabeli.
     */
    public void addDeparture(DepartureRow departure) {
        if (departure == null) return;
        SwingUtilities.invokeLater(() -> {
            int newRowIndex = departures.size();
            departures.add(departure);
            fireTableRowsInserted(newRowIndex, newRowIndex);
        });
    }

    /**
     * Czyści wszystkie wiersze z tabeli.
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