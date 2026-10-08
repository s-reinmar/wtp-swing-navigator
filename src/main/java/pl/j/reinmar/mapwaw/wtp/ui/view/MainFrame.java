package pl.j.reinmar.mapwaw.wtp.ui.view;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * Główne okno aplikacji Swing/AWT reprezentujące widok nawigacyjny WTP Warszawa.
 * Wykorzystuje zarządcę układu BorderLayout do podziału widoku na panel boczny (WEST)
 * oraz centralny panel mapy OpenStreetMap (CENTER).
 */
public class MainFrame extends JFrame {

    private static final Logger logger = LoggerFactory.getLogger(MainFrame.class);

    private static final String APP_TITLE = "WTP Warszawa - Nawigacja i Mapa";
    private static final int DEFAULT_WIDTH = 1280;
    private static final int DEFAULT_HEIGHT = 800;

    private JPanel sidebarPanel;
    private JPanel mapPanel;
    private JPanel statusBarPanel;
    private JLabel statusLabel;
    private JLabel gpsApiStatusLabel;
    private JComponent quickSearchComponent;
    private Runnable onRefreshRequested;

    /**
     * Konstruktor tworzący główne okno aplikacji z ukierunkowanym układem BorderLayout.
     */
    public MainFrame() {
        super(APP_TITLE);
        initFrame();
        setupBorderLayoutContainers();
        setupKeyboardShortcuts();
    }

    /**
     * Rejestruje skróty globalne okna: Ctrl+F (szybkie wyszukiwanie) i F5 (ręczne odświeżenie).
     */
    private void setupKeyboardShortcuts() {
        JRootPane root = getRootPane();
        InputMap inputMap = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = root.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "quickSearch");
        actionMap.put("quickSearch", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                focusQuickSearch();
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "refreshData");
        actionMap.put("refreshData", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                requestRefresh();
            }
        });
    }

    private void focusQuickSearch() {
        if (quickSearchComponent != null) {
            quickSearchComponent.requestFocusInWindow();
            if (quickSearchComponent instanceof javax.swing.text.JTextComponent textComponent) {
                textComponent.selectAll();
            }
        }
    }

    private void requestRefresh() {
        if (onRefreshRequested != null) {
            setStatusText("Odświeżanie danych (F5)...");
            onRefreshRequested.run();
        }
    }

    /**
     * Ustawia komponent, który otrzymuje fokus po naciśnięciu Ctrl+F.
     */
    public void setQuickSearchComponent(JComponent component) {
        this.quickSearchComponent = component;
    }

    /**
     * Ustawia akcję wykonywaną po naciśnięciu F5.
     */
    public void setOnRefreshRequested(Runnable onRefreshRequested) {
        this.onRefreshRequested = onRefreshRequested;
    }

    /**
     * Inicjalizuje podstawowe właściwości okna JFrame.
     */
    private void initFrame() {
        logger.info("Inicjalizacja głównego okna aplikacji MainFrame (rozmiar: {}x{})...", DEFAULT_WIDTH, DEFAULT_HEIGHT);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        setMinimumSize(new Dimension(1024, 600));
        setLocationRelativeTo(null);

        // Ustawienie głównego zarządcy układu na BorderLayout
        setLayout(new BorderLayout(4, 4));
    }

    /**
     * KROK 50: Skonfigurowanie podziału okna za pomocą układu BorderLayout.
     */
    private void setupBorderLayoutContainers() {
        logger.info("Konfigurowanie stref BorderLayout: WEST (panel sterowania), CENTER (mapa), SOUTH (pasek stanu)...");

        // 1. PANEL BOCZNY (STEROWANIE) - Zmieszczony w sekcji WEST
        sidebarPanel = new JPanel();
        sidebarPanel.setLayout(new BoxLayout(sidebarPanel, BoxLayout.Y_AXIS));
        sidebarPanel.setPreferredSize(new Dimension(340, 0));
        sidebarPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, Color.LIGHT_GRAY),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        // Placeholder dla przyszłego panelu wyszukiwania / tablicy odjazdów (SidebarPanel / DepartureBoardPanel)
        JLabel sidebarPlaceholder = new JLabel("<html><b>Panel Sterowania WTP</b><br/>(Wyszukiwarka i Tablica Odjazdów)</html>");
        sidebarPanel.add(sidebarPlaceholder);

        add(sidebarPanel, BorderLayout.WEST);

        // 2. PANEL CENTRALNY (MAPA) - Zmieszczony w sekcji CENTER
        mapPanel = new JPanel(new BorderLayout());
        mapPanel.setBackground(new Color(235, 235, 235));

        // Placeholder pod docelowy komponent MapPanel (JXMapViewer2)
        JLabel mapPlaceholder = new JLabel("Komponent Mapy OpenStreetMap (JXMapViewer2)", SwingConstants.CENTER);
        mapPlaceholder.setFont(new Font("SansSerif", Font.BOLD, 14));
        mapPanel.add(mapPlaceholder, BorderLayout.CENTER);

        add(mapPanel, BorderLayout.CENTER);

        // 3. PASZEK STATUSU (DOLNY) - Zmieszczony w sekcji SOUTH
        statusBarPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        statusBarPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));

        gpsApiStatusLabel = new JLabel("API GPS: tryb offline",
                new ConnectionStatusIcon(false), SwingConstants.LEFT);
        gpsApiStatusLabel.setIconTextGap(6);
        gpsApiStatusLabel.setForeground(new Color(160, 35, 35));
        gpsApiStatusLabel.setToolTipText("Brak potwierdzonego połączenia z API GPS.");
        gpsApiStatusLabel.getAccessibleContext().setAccessibleName("Status połączenia z API GPS");
        gpsApiStatusLabel.getAccessibleContext().setAccessibleDescription(
                "Brak potwierdzonego połączenia z API GPS.");
        statusBarPanel.add(gpsApiStatusLabel);

        statusLabel = new JLabel("Gotowy. Załadowano system interfejsu WTP Swing Navigator.");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        statusBarPanel.add(statusLabel);

        add(statusBarPanel, BorderLayout.SOUTH);

        logger.info("Struktura kontenerów BorderLayout została skonfigurowana.");
    }

    /**
     * Podmienia centralny panel mapy na właściwą instancję MapPanel.
     *
     * @param newMapPanel dedykowany panel z mapą JXMapViewer2
     */
    public void setMapPanel(JPanel newMapPanel) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setMapPanel(newMapPanel));
            return;
        }
        if (this.mapPanel != null) {
            remove(this.mapPanel);
        }
        this.mapPanel = newMapPanel;
        add(this.mapPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    /**
     * Podmienia boczny panel sterowania na właściwy panel z wyszukiwarką/odjazdami.
     *
     * @param newSidebarPanel dedykowany panel boczny sterowania
     */
    public void setSidebarPanel(JPanel newSidebarPanel) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setSidebarPanel(newSidebarPanel));
            return;
        }
        if (this.sidebarPanel != null) {
            remove(this.sidebarPanel);
        }
        this.sidebarPanel = newSidebarPanel;
        add(this.sidebarPanel, BorderLayout.WEST);
        revalidate();
        repaint();
    }

    /**
     * Aktualizuje tekst wyświetlany na dolnym pasku stanu.
     */
    public void setStatusText(String text) {
        if (statusLabel != null) {
            SwingUtilities.invokeLater(() -> statusLabel.setText(text));
        }
    }

    public void setGpsApiConnected(boolean connected) {
        Runnable update = () -> {
            String status = connected ? "API GPS: połączono" : "API GPS: tryb offline";
            gpsApiStatusLabel.setText(status);
            gpsApiStatusLabel.setIcon(new ConnectionStatusIcon(connected));
            gpsApiStatusLabel.setForeground(connected
                    ? new Color(0, 120, 45) : new Color(160, 35, 35));
            gpsApiStatusLabel.setToolTipText(connected
                    ? "Ostatnie pobranie danych z API GPS zakończyło się powodzeniem."
                    : "Brak połączenia z API GPS. Aplikacja działa na rozkładach statycznych.");
            gpsApiStatusLabel.getAccessibleContext().setAccessibleDescription(status);
            statusLabel.setText(connected
                    ? "Połączono. Wyświetlane są pozycje pojazdów na żywo."
                    : "Tryb offline: dostępne są tylko rozkłady statyczne (bez pozycji pojazdów na żywo).");
        };
        if (SwingUtilities.isEventDispatchThread()) {
            update.run();
        } else {
            SwingUtilities.invokeLater(update);
        }
    }

    private static final class ConnectionStatusIcon implements Icon {
        private static final int SIZE = 12;
        private final Color color;

        private ConnectionStatusIcon(boolean connected) {
            color = connected ? new Color(30, 175, 75) : new Color(210, 45, 45);
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(color);
                g.fillOval(x + 1, y + 1, SIZE - 2, SIZE - 2);
                g.setColor(color.darker());
                g.drawOval(x + 1, y + 1, SIZE - 2, SIZE - 2);
            } finally {
                g.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }

    /**
     * Metoda pomocnicza uruchamiająca widoczność okna na wątku EDT.
     */
    public void display() {
        SwingUtilities.invokeLater(() -> setVisible(true));
    }
}