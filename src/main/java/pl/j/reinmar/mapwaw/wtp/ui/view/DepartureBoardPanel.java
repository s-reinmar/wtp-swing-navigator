package pl.j.reinmar.mapwaw.wtp.ui.view;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.j.reinmar.mapwaw.wtp.ui.component.DepartureTableCellRenderer;
import pl.j.reinmar.mapwaw.wtp.ui.component.DepartureTableModel;
import pl.j.reinmar.mapwaw.wtp.ui.controller.MapController;

import javax.swing.*;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * Panel boczny prezentujący tablicę odjazdów z przystanku[cite: 4].
 * Zawiera nagłówek, przycisk czyszczenia, checkboxy do filtrowania typów transportu
 * oraz tabelę JTable skonfigurowaną z customowym modelem[cite: 4].
 */
public class DepartureBoardPanel extends JPanel {

    private static final Logger logger = LoggerFactory.getLogger(DepartureBoardPanel.class);

    private JLabel stopHeaderLabel;
    private JTable departureTable;
    private DepartureTableModel tableModel;
    private JButton clearButton;
    private MapController mapController;

    // KROK 75: Checkboxy do filtrowania odjazdów
    private JCheckBox busCheckBox;
    private JCheckBox tramCheckBox;
    private JCheckBox metroCheckBox;

    public DepartureBoardPanel() {
        logger.info("Inicjalizacja DepartureBoardPanel z filtrami transportu...");
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // 1. GŁÓWNY PANEL NAGŁÓWKA (Zawiera tytuł, przycisk i filtry)
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0, new Color(0, 102, 204)),
                BorderFactory.createEmptyBorder(4, 4, 6, 4)
        ));

        // 1A. Górna część nagłówka (Etykieta i przycisk czyszczenia)
        JPanel topHeaderPart = new JPanel(new BorderLayout());

        stopHeaderLabel = new JLabel("Wybierz przystanek z mapy / szukaj", SwingConstants.LEFT);
        stopHeaderLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        stopHeaderLabel.setForeground(new Color(0, 51, 102));
        topHeaderPart.add(stopHeaderLabel, BorderLayout.CENTER);

        clearButton = new JButton("Wyczyść / Powrót");
        clearButton.setFocusable(false);
        clearButton.addActionListener(e -> {
            if (mapController != null) {
                mapController.clearSelection();
            }
        });
        topHeaderPart.add(clearButton, BorderLayout.EAST);

        headerPanel.add(topHeaderPart, BorderLayout.NORTH);

        // 1B. KROK 75: Dolna część nagłówka (Filtry transportu)
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));

        busCheckBox = new JCheckBox("Autobusy", true);
        tramCheckBox = new JCheckBox("Tramwaje", true);
        metroCheckBox = new JCheckBox("Metro", true);

        busCheckBox.setFocusPainted(false);
        tramCheckBox.setFocusPainted(false);
        metroCheckBox.setFocusPainted(false);

        // Wspólny listener dla checkboxów automatycznie odświeżający tabelę po kliknięciu
        ActionListener filterListener = e -> {
            if (mapController != null) {
                mapController.refreshDepartureBoard();
            }
        };
        busCheckBox.addActionListener(filterListener);
        tramCheckBox.addActionListener(filterListener);
        metroCheckBox.addActionListener(filterListener);

        filterPanel.add(new JLabel("Pokaż:"));
        filterPanel.add(busCheckBox);
        filterPanel.add(tramCheckBox);
        filterPanel.add(metroCheckBox);

        headerPanel.add(filterPanel, BorderLayout.SOUTH);

        add(headerPanel, BorderLayout.NORTH);

        // 2. MODEL I TABELA ODJAZDÓW (JTable)[cite: 4]
        tableModel = new DepartureTableModel();
        departureTable = new JTable(tableModel);

        departureTable.setRowHeight(28);
        departureTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        departureTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        departureTable.getTableHeader().setReorderingAllowed(false);
        departureTable.setAutoCreateRowSorter(true);

        setupTableColumns();

        JScrollPane scrollPane = new JScrollPane(departureTable);
        add(scrollPane, BorderLayout.CENTER);
    }

    private void setupTableColumns() {
        TableColumnModel columnModel = departureTable.getColumnModel();
        columnModel.getColumn(0).setPreferredWidth(60);
        columnModel.getColumn(0).setMaxWidth(80);
        columnModel.getColumn(1).setPreferredWidth(160);
        columnModel.getColumn(2).setPreferredWidth(110);
        columnModel.getColumn(3).setPreferredWidth(130);
        columnModel.getColumn(4).setPreferredWidth(90);

        DepartureTableCellRenderer cellRenderer = new DepartureTableCellRenderer();
        for (int i = 0; i < departureTable.getColumnCount(); i++) {
            departureTable.getColumnModel().getColumn(i).setCellRenderer(cellRenderer);
        }
    }

    public void setStopHeader(String stopName) {
        SwingUtilities.invokeLater(() -> {
            if (stopName != null && !stopName.isBlank()) {
                stopHeaderLabel.setText("🏏 Przystanek: " + stopName);
            } else {
                stopHeaderLabel.setText("Wybierz przystanek z mapy / szukaj");
            }
        });
    }

    public void setMapController(MapController mapController) {
        this.mapController = mapController;
    }

    public DepartureTableModel getTableModel() { return tableModel; }
    public JTable getDepartureTable() { return departureTable; }

    // --- Metody dostępu do stanu filtrów ---
    public boolean isBusFilterActive() { return busCheckBox.isSelected(); }
    public boolean isTramFilterActive() { return tramCheckBox.isSelected(); }
    public boolean isMetroFilterActive() { return metroCheckBox.isSelected(); }
}