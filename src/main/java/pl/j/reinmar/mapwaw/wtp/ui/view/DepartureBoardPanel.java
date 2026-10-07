package pl.j.reinmar.mapwaw.wtp.ui.view;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * Panel boczny reprezentujący tablicę odjazdów z przystanku.
 * Zawiera nagłówek z nazwą przystanku oraz tabelę JTable przeznaczoną do wyświetlania odjazdów.
 */
public class DepartureBoardPanel extends JPanel {

    private static final Logger logger = LoggerFactory.getLogger(DepartureBoardPanel.class);

    private JLabel stopHeaderLabel;
    private JTable departureTable;
    private DefaultTableModel tableModel;

    /**
     * Konstruktor tworzący panel tablicy odjazdów.
     */
    public DepartureBoardPanel() {
        logger.info("Inicjalizacja panelu bocznego DepartureBoardPanel...");
        initUI();
    }

    /**
     * Inicjalizuje układ oraz komponenty Swing dla tablicy odjazdów.
     */
    private void initUI() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // 1. NAGŁÓWEK TABLICY ODJAZDÓW[cite: 2, 6]
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0, new Color(0, 102, 204)),
                BorderFactory.createEmptyBorder(4, 4, 6, 4)
        ));

        stopHeaderLabel = new JLabel("Wybierz przystanek z mapy / szukaj", SwingConstants.LEFT);
        stopHeaderLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        stopHeaderLabel.setForeground(new Color(0, 51, 102));
        headerPanel.add(stopHeaderLabel, BorderLayout.CENTER);

        add(headerPanel, BorderLayout.NORTH);

        // 2. TABELA ODJAZDÓW (JTable)[cite: 2, 6]
        String[] columnNames = {"Linia", "Kierunek", "Rozkład", "Estymacja (GPS)", "Opóźnienie"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Tabela tylko do odczytu
            }
        };

        departureTable = new JTable(tableModel);
        departureTable.setRowHeight(26);
        departureTable.getTableHeader().setReorderingAllowed(false);
        departureTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        departureTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));

        JScrollPane scrollPane = new JScrollPane(departureTable);
        add(scrollPane, BorderLayout.CENTER);

        logger.info("Panel DepartureBoardPanel został pomyślnie zainicjalizowany.");
    }

    /**
     * Ustawia nagłówek z nazwą i kodem aktywnego przystanku.
     *
     * @param stopName nazwa zespołu i numer słupka (np. "Centrum 01")
     */
    public void setStopHeader(String stopName) {
        SwingUtilities.invokeLater(() -> {
            if (stopName != null && !stopName.isBlank()) {
                stopHeaderLabel.setText("🏏 Przystanek: " + stopName);
            } else {
                stopHeaderLabel.setText("Wybierz przystanek z mapy / szukaj");
            }
        });
    }

    /**
     * Zwraca model tabeli w celu zasilania danymi o odjazdach.
     */
    public DefaultTableModel getTableModel() {
        return tableModel;
    }

    /**
     * Zwraca instancję komponentu JTable.
     */
    public JTable getDepartureTable() {
        return departureTable;
    }
}