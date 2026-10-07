package pl.j.reinmar.mapwaw.wtp.ui.view;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.j.reinmar.mapwaw.wtp.ui.component.DepartureTableCellRenderer;
import pl.j.reinmar.mapwaw.wtp.ui.component.DepartureTableModel;

import javax.swing.*;
import javax.swing.table.TableColumnModel;
import java.awt.*;

/**
 * Panel boczny prezentujący tablicę odjazdów z przystanku.
 * Zawiera nagłówek oraz tabelę JTable skonfigurowaną z customowym model i rendererem komórek.
 */
public class DepartureBoardPanel extends JPanel {

    private static final Logger logger = LoggerFactory.getLogger(DepartureBoardPanel.class);

    private JLabel stopHeaderLabel;
    private JTable departureTable;
    private DepartureTableModel tableModel;

    /**
     * Konstruktor tworzący panel tablicy odjazdów.
     */
    public DepartureBoardPanel() {
        logger.info("Inicjalizacja DepartureBoardPanel z wyznaczoną konfiguracją kolumn i rendererem...");
        initUI();
    }

    /**
     * Inicjalizuje układ, komponenty Swing oraz nakłada customowy renderer komórek dla opóźnień.
     */
    private void initUI() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // 1. NAGŁÓWEK TABLICY ODJAZDÓW
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

        // 2. MODEL I TABELA ODJAZDÓW (JTable)[cite: 2, 6]
        tableModel = new DepartureTableModel();
        departureTable = new JTable(tableModel);

        // Ogólne właściwości JTable
        departureTable.setRowHeight(28);
        departureTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        departureTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        departureTable.getTableHeader().setReorderingAllowed(false);
        departureTable.setAutoCreateRowSorter(true);

        // Konfiguracja kolumn JTable oraz przypisanie customowego renderera
        setupTableColumns();

        JScrollPane scrollPane = new JScrollPane(departureTable);
        add(scrollPane, BorderLayout.CENTER);

        logger.info("Panel DepartureBoardPanel został pomyślnie skonfigurowany.");
    }

    /**
     * Konfiguruje preferowane szerokości kolumn oraz nakłada customowy renderer komórek.
     */
    private void setupTableColumns() {
        TableColumnModel columnModel = departureTable.getColumnModel();

        // 1. Przypisanie wymiarów kolumn
        columnModel.getColumn(0).setPreferredWidth(60);  // Linia
        columnModel.getColumn(0).setMaxWidth(80);
        columnModel.getColumn(1).setPreferredWidth(160); // Kierunek
        columnModel.getColumn(2).setPreferredWidth(110); // Czas rozkładowy
        columnModel.getColumn(3).setPreferredWidth(130); // Estymowany czas (GPS)
        columnModel.getColumn(4).setPreferredWidth(90);  // Opóźnienie

        // 2. Zastosowanie customowego renderera kolorującego opóźnienia
        DepartureTableCellRenderer cellRenderer = new DepartureTableCellRenderer();
        for (int i = 0; i < departureTable.getColumnCount(); i++) {
            departureTable.getColumnModel().getColumn(i).setCellRenderer(cellRenderer);
        }
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
     * Zwraca model tabeli odjazdów.
     */
    public DepartureTableModel getTableModel() {
        return tableModel;
    }

    /**
     * Zwraca komponent JTable.
     */
    public JTable getDepartureTable() {
        return departureTable;
    }
}