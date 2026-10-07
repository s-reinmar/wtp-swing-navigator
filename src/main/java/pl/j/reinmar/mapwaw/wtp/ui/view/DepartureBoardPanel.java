package pl.j.reinmar.mapwaw.wtp.ui.view;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.j.reinmar.mapwaw.wtp.ui.component.DepartureTableModel;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;

/**
 * Panel boczny prezentujący tablicę odjazdów z przystanku.
 * Zawiera skonfigurowaną tabelę JTable z kolumnami:
 * Linia, Kierunek, Czas rozkładowy, Estymowany czas (GPS) oraz Opóźnienie.
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
        logger.info("Inicjalizacja DepartureBoardPanel z konfiguracją kolumn odjazdów...");
        initUI();
    }

    /**
     * Inicjalizuje układ, komponenty Swing oraz ustala szerokości i wyrównania kolumn JTable.
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

        // 2. MODEL I TABELA ODJAZDÓW (JTable)[cite: 2, 6]
        tableModel = new DepartureTableModel();
        departureTable = new JTable(tableModel);

        // Ogólne właściwości JTable
        departureTable.setRowHeight(28);
        departureTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        departureTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        departureTable.getTableHeader().setReorderingAllowed(false);
        departureTable.setAutoCreateRowSorter(true);

        // Konfiguracja kolumn JTable
        setupTableColumns();

        JScrollPane scrollPane = new JScrollPane(departureTable);
        add(scrollPane, BorderLayout.CENTER);

        logger.info("Skonfigurowano kolumny JTable: Linia, Kierunek, Czas rozkładowy, Estymowany czas (GPS), Opóźnienie.");
    }

    /**
     * Konfiguruje preferowane szerokości oraz wyrównania tekstu w kolumnach tabeli odjazdów.
     */
    private void setupTableColumns() {
        TableColumnModel columnModel = departureTable.getColumnModel();

        // Renderer wyśrodkowujący tekst
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);

        // Renderer do lewej (dla kierunku)
        DefaultTableCellRenderer leftRenderer = new DefaultTableCellRenderer();
        leftRenderer.setHorizontalAlignment(SwingConstants.LEFT);

        // Kolumna 0: Linia (np. "507", "17")
        columnModel.getColumn(0).setPreferredWidth(60);
        columnModel.getColumn(0).setMaxWidth(80);
        columnModel.getColumn(0).setCellRenderer(centerRenderer);

        // Kolumna 1: Kierunek (np. "Gocław")
        columnModel.getColumn(1).setPreferredWidth(160);
        columnModel.getColumn(1).setCellRenderer(leftRenderer);

        // Kolumna 2: Czas rozkładowy (np. "14:25")
        columnModel.getColumn(2).setPreferredWidth(110);
        columnModel.getColumn(2).setCellRenderer(centerRenderer);

        // Kolumna 3: Estymowany czas (GPS) (np. "14:28")
        columnModel.getColumn(3).setPreferredWidth(130);
        columnModel.getColumn(3).setCellRenderer(centerRenderer);

        // Kolumna 4: Opóźnienie (np. "+3 min")
        columnModel.getColumn(4).setPreferredWidth(90);
        columnModel.getColumn(4).setCellRenderer(centerRenderer);
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