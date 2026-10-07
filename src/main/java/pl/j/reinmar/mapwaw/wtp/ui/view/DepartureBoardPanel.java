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
 * Panel boczny prezentujący tablicę odjazdów z przystanku.
 * Wykorzystuje CardLayout do obsługi pustych stanów (brak odjazdów).
 */
public class DepartureBoardPanel extends JPanel {

    private static final Logger logger = LoggerFactory.getLogger(DepartureBoardPanel.class);

    private JLabel stopHeaderLabel;
    private JTable departureTable;
    private DepartureTableModel tableModel;
    private MapController mapController;

    // Checkboxy filtrów (Krok 75)
    private JCheckBox busCheckBox;
    private JCheckBox tramCheckBox;
    private JCheckBox metroCheckBox;

    public enum SortOption {
        ARRIVAL_TIME("Czas przybycia"),
        LINE("Linia");

        private final String label;

        SortOption(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private JComboBox<SortOption> sortComboBox;

    // Zmienne do obsługi CardLayout (Krok 76)
    private JPanel centerCardPanel;
    private CardLayout cardLayout;
    private JLabel emptyMessageLabel;

    public DepartureBoardPanel() {
        logger.info("Inicjalizacja DepartureBoardPanel z obsługą pustych stanów...");
        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // --- 1. NAGŁÓWEK I FILTRY ---
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0, new Color(0, 102, 204)),
                BorderFactory.createEmptyBorder(4, 4, 6, 4)
        ));

        JPanel topHeaderPart = new JPanel(new BorderLayout());
        stopHeaderLabel = new JLabel("Wybierz przystanek z mapy / szukaj", SwingConstants.LEFT);
        stopHeaderLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        stopHeaderLabel.setForeground(new Color(0, 51, 102));
        topHeaderPart.add(stopHeaderLabel, BorderLayout.CENTER);

        JButton clearButton = new JButton("Wyczyść / Powrót");
        clearButton.setFocusable(false);
        clearButton.addActionListener(e -> {
            if (mapController != null) mapController.clearSelection();
        });
        topHeaderPart.add(clearButton, BorderLayout.EAST);
        headerPanel.add(topHeaderPart, BorderLayout.NORTH);

        // Filtry
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        busCheckBox = new JCheckBox("Autobusy", true);
        tramCheckBox = new JCheckBox("Tramwaje", true);
        metroCheckBox = new JCheckBox("Metro", true);

        ActionListener filterListener = e -> {
            if (mapController != null) mapController.refreshDepartureBoard();
        };
        busCheckBox.addActionListener(filterListener);
        tramCheckBox.addActionListener(filterListener);
        metroCheckBox.addActionListener(filterListener);

        filterPanel.add(new JLabel("Pokaż:"));
        filterPanel.add(busCheckBox);
        filterPanel.add(tramCheckBox);
        filterPanel.add(metroCheckBox);

        // Sortowanie (Krok 77)
        sortComboBox = new JComboBox<>(SortOption.values());
        sortComboBox.addActionListener(filterListener);
        filterPanel.add(new JLabel("Sortuj:"));
        filterPanel.add(sortComboBox);
        headerPanel.add(filterPanel, BorderLayout.SOUTH);

        add(headerPanel, BorderLayout.NORTH);

        // --- 2. SEKCJA CENTRALNA (CardLayout: Tabela vs Komunikat) ---
        cardLayout = new CardLayout();
        centerCardPanel = new JPanel(cardLayout);

        // Karta A: Tabela
        tableModel = new DepartureTableModel();
        departureTable = new JTable(tableModel);
        departureTable.setRowHeight(28);
        departureTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        departureTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        departureTable.getTableHeader().setReorderingAllowed(false);
        setupTableColumns();
        JScrollPane scrollPane = new JScrollPane(departureTable);
        centerCardPanel.add(scrollPane, "TABLE");

        // Karta B: Pusty komunikat (Krok 76)
        JPanel emptyPanel = new JPanel(new GridBagLayout());
        emptyPanel.setBackground(new Color(248, 249, 250));
        emptyMessageLabel = new JLabel("Wybierz przystanek z mapy, aby zobaczyć odjazdy.");
        emptyMessageLabel.setFont(new Font("SansSerif", Font.ITALIC, 14));
        emptyMessageLabel.setForeground(Color.GRAY);
        emptyPanel.add(emptyMessageLabel);
        centerCardPanel.add(emptyPanel, "EMPTY_MESSAGE");

        add(centerCardPanel, BorderLayout.CENTER);

        // Domyślny widok
        showEmptyMessage("Wybierz przystanek z mapy, aby zobaczyć odjazdy.");
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

    // --- KROK 76: Metody do sterowania widokiem CardLayout ---

    public void showEmptyMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            emptyMessageLabel.setText(message);
            cardLayout.show(centerCardPanel, "EMPTY_MESSAGE");
        });
    }

    public void showTable() {
        SwingUtilities.invokeLater(() -> {
            cardLayout.show(centerCardPanel, "TABLE");
        });
    }

    // --- Reszta metod ---

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
    public SortOption getSelectedSortOption() { return (SortOption) sortComboBox.getSelectedItem(); }
    public boolean isBusFilterActive() { return busCheckBox.isSelected(); }
    public boolean isTramFilterActive() { return tramCheckBox.isSelected(); }
    public boolean isMetroFilterActive() { return metroCheckBox.isSelected(); }
}