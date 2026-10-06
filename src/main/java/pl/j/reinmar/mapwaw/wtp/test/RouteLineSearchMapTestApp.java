package pl.j.reinmar.mapwaw.wtp.test;

import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.OSMTileFactoryInfo;
import org.jxmapviewer.input.PanMouseInputListener;
import org.jxmapviewer.painter.Painter;
import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.TileFactoryInfo;

import javax.swing.*;
import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.geom.Point2D;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RouteLineSearchMapTestApp extends JFrame {

    public record GeoPoint(double lat, double lon, String name) {}

    public enum TransportType {
        BUS("Autobus", new Color(0, 102, 204)),
        TRAM("Tramwaj", new Color(204, 0, 0)),
        METRO("Metro", new Color(0, 153, 76)),
        WALK("Przejście piesze", new Color(130, 130, 130));

        private final String label;
        private final Color color;

        TransportType(String label, Color color) {
            this.label = label;
            this.color = color;
        }

        public String getLabel() { return label; }
        public Color getColor() { return color; }
    }

    // Rekord reprezentujący pojedynczy odcinek trasy (jedną linię lub przejście)
    public record RouteLeg(
            TransportType type,
            String lineNumber,
            String fromStopName,
            String toStopName,
            List<GeoPosition> pathPoints,
            Color color
    ) {}

    // Wariant trasy zawierający jeden lub więcej odcinków (przesiadki)
    public static class RouteOption {
        private final String title;
        private final int totalDurationMinutes;
        private final int transferCount;
        private final List<RouteLeg> legs;

        public RouteOption(String title, int totalDurationMinutes, int transferCount, List<RouteLeg> legs) {
            this.title = title;
            this.totalDurationMinutes = totalDurationMinutes;
            this.transferCount = transferCount;
            this.legs = legs;
        }

        public String getTitle() { return title; }
        public int getTotalDurationMinutes() { return totalDurationMinutes; }
        public int getTransferCount() { return transferCount; }
        public List<RouteLeg> getLegs() { return legs; }

        @Override
        public String toString() {
            String transfersText = transferCount == 0 ? "Bez przesiadek" : (transferCount + " przesiadka");
            return title + " (" + totalDurationMinutes + " min, " + transfersText + ")";
        }
    }

    // --- PAINTER LINIOWY (Rysuje linie tras zamiast pinezek) ---

    public static class RoutePolylinePainter implements Painter<JXMapViewer> {
        private List<RouteLeg> legs = new ArrayList<>();

        public void setLegs(List<RouteLeg> legs) {
            this.legs = (legs != null) ? legs : new ArrayList<>();
        }

        @Override
        public void paint(Graphics2D g, JXMapViewer map, int width, int height) {
            if (legs == null || legs.isEmpty()) return;

            g = (Graphics2D) g.create();
            Rectangle rect = map.getViewportBounds();
            g.translate(-rect.x, -rect.y);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            for (RouteLeg leg : legs) {
                List<GeoPosition> points = leg.pathPoints();
                if (points == null || points.size() < 2) continue;

                // 1. Rysowanie linii przejazdu danej linii / przejścia
                g.setColor(leg.color());

                // Linia przerywana dla przejścia pieszego, ciągła pogrubiona dla komunikacji
                if (leg.type() == TransportType.WALK) {
                    Stroke dashed = new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{6}, 0);
                    g.setStroke(dashed);
                } else {
                    g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                }

                for (int i = 0; i < points.size() - 1; i++) {
                    Point2D p1 = map.getTileFactory().geoToPixel(points.get(i), map.getZoom());
                    Point2D p2 = map.getTileFactory().geoToPixel(points.get(i + 1), map.getZoom());
                    g.drawLine((int) p1.getX(), (int) p1.getY(), (int) p2.getX(), (int) p2.getY());
                }

                // 2. Rysowanie subtelnych punktów węzłowych / przesiadkowych (bez wielkich pinezek)
                for (int i = 0; i < points.size(); i++) {
                    Point2D pt = map.getTileFactory().geoToPixel(points.get(i), map.getZoom());
                    int x = (int) pt.getX();
                    int y = (int) pt.getY();

                    if (i == 0 || i == points.size() - 1) {
                        // Punkt startowy / przesiadkowy / końcowy
                        g.setColor(Color.WHITE);
                        g.fillOval(x - 6, y - 6, 12, 12);
                        g.setColor(leg.color());
                        g.setStroke(new BasicStroke(3f));
                        g.drawOval(x - 6, y - 6, 12, 12);
                    } else {
                        // Przystanek pośredni
                        g.setColor(leg.color());
                        g.fillOval(x - 3, y - 3, 6, 6);
                    }
                }
            }
            g.dispose();
        }
    }

    // --- FORMULARZ I MAPA ---

    private final JXMapViewer mapViewer;
    private final RoutePolylinePainter polylinePainter;
    private final JTextField startAddressField;
    private final JTextField endAddressField;
    private final JButton searchButton;
    private final DefaultListModel<RouteOption> listModel;
    private final JList<RouteOption> routeJList;
    private final JEditorPane routeDetailPane;
    private final JLabel statusLabel;

    private final HttpClient httpClient;

    public RouteLineSearchMapTestApp() {
        super("WTP Warszawa — Trasowanie i obsługa przesiadek (Bez pinezek)");

        System.setProperty("http.agent", "WtpSwingNavigator/1.0 (pl.warsaw.wtp)");

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1250, 780);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // 1. Konfiguracja JXMapViewer2 HTTPS
        mapViewer = new JXMapViewer();
        TileFactoryInfo info = new OSMTileFactoryInfo("OpenStreetMap", "https://tile.openstreetmap.org");
        DefaultTileFactory tileFactory = new DefaultTileFactory(info);
        tileFactory.setThreadPoolSize(8);
        mapViewer.setTileFactory(tileFactory);

        GeoPosition warsaw = new GeoPosition(52.2319, 21.0067);
        mapViewer.setZoom(7);
        mapViewer.setAddressLocation(warsaw);

        // Nawigacja myszą
        MouseInputListener mia = new PanMouseInputListener(mapViewer);
        mapViewer.addMouseListener(mia);
        mapViewer.addMouseMotionListener(mia);
        mapViewer.addMouseWheelListener(e -> {
            if (e.getWheelRotation() < 0) {
                mapViewer.setZoom(Math.max(1, mapViewer.getZoom() - 1));
            } else {
                mapViewer.setZoom(Math.min(15, mapViewer.getZoom() + 1));
            }
        });

        // Wprowadzenie Paintera Liniowego (BRAK WaypointPaintera z pinezkami)
        polylinePainter = new RoutePolylinePainter();
        mapViewer.setOverlayPainter(polylinePainter);

        // 2. Formularz wyszukiwania Z / DO (Góra)
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 10));
        searchPanel.setBorder(BorderFactory.createTitledBorder("Wyszukiwarka połączeń z przesiadkami"));

        searchPanel.add(new JLabel("🟢 Z:"));
        startAddressField = new JTextField("Rondo Daszyńskiego", 18);
        searchPanel.add(startAddressField);

        searchPanel.add(new JLabel("🔴 DO:"));
        endAddressField = new JTextField("Bernardyńska", 18);
        searchPanel.add(endAddressField);

        searchButton = new JButton("🔍 Szukaj trasy");
        searchButton.setFont(searchButton.getFont().deriveFont(Font.BOLD));
        searchButton.setBackground(new Color(0, 120, 215));
        searchButton.setForeground(Color.WHITE);
        searchPanel.add(searchButton);

        add(searchPanel, BorderLayout.NORTH);

        // 3. Panel boczny: Lista wariantów i opis przesiadek
        listModel = new DefaultListModel<>();
        routeJList = new JList<>(listModel);
        routeJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        routeJList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));

        // Karta ze szczegółami przesiadek (HTML)
        routeDetailPane = new JEditorPane();
        routeDetailPane.setContentType("text/html");
        routeDetailPane.setEditable(false);
        JScrollPane detailScrollPane = new JScrollPane(routeDetailPane);
        detailScrollPane.setPreferredSize(new Dimension(360, 320));
        detailScrollPane.setBorder(BorderFactory.createTitledBorder("Szczegóły linii i przesiadek"));

        routeJList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                RouteOption selectedOption = routeJList.getSelectedValue();
                displaySelectedRoute(selectedOption);
            }
        });

        JScrollPane listScrollPane = new JScrollPane(routeJList);
        listScrollPane.setPreferredSize(new Dimension(360, 200));
        listScrollPane.setBorder(BorderFactory.createTitledBorder("Proponowane warianty tras"));

        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.add(listScrollPane, BorderLayout.NORTH);
        leftPanel.add(detailScrollPane, BorderLayout.CENTER);

        add(leftPanel, BorderLayout.WEST);
        add(mapViewer, BorderLayout.CENTER);

        // 4. Pasek statusu
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusLabel = new JLabel("Wpisz adresy i kliknij 'Szukaj trasy'.");
        statusPanel.add(statusLabel);
        add(statusPanel, BorderLayout.SOUTH);

        searchButton.addActionListener(e -> executeRouteSearch());
    }

    private void executeRouteSearch() {
        String startText = startAddressField.getText().trim();
        String endText = endAddressField.getText().trim();

        if (startText.isEmpty() || endText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Wpisz punkt początkowy oraz docelowy!", "Błąd", JOptionPane.WARNING_MESSAGE);
            return;
        }

        searchButton.setEnabled(false);
        statusLabel.setText("Geokodowanie punktów i generowanie wariantów tras z przesiadkami...");

        SwingWorker<List<RouteOption>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<RouteOption> doInBackground() throws Exception {
                GeoPoint startGeo = geocodeAddress(startText);
                GeoPoint endGeo = geocodeAddress(endText);

                if (startGeo == null || endGeo == null) {
                    throw new RuntimeException("Nie odnaleziono podanych adresów w Warszawie.");
                }

                return buildRouteOptionsWithTransfers(startGeo, endGeo);
            }

            @Override
            protected void done() {
                try {
                    List<RouteOption> options = get();
                    listModel.clear();
                    options.forEach(listModel::addElement);

                    if (!options.isEmpty()) {
                        routeJList.setSelectedIndex(0);
                        statusLabel.setText("Narysowano trasę na mapie z podziałem na linie i przesiadki.");
                    }
                } catch (Exception ex) {
                    statusLabel.setText("Błąd szukania trasy.");
                    JOptionPane.showMessageDialog(RouteLineSearchMapTestApp.this, ex.getMessage(), "Błąd", JOptionPane.ERROR_MESSAGE);
                } finally {
                    searchButton.setEnabled(true);
                }
            }
        };

        worker.execute();
    }

    /**
     * Rysuje wybraną trasę jako kolorową linię na mapie i generuje karta przesiadek.
     */
    private void displaySelectedRoute(RouteOption option) {
        if (option == null) {
            polylinePainter.setLegs(null);
            routeDetailPane.setText("<html><body><i>Wybierz wariant z listy powyżej.</i></body></html>");
            mapViewer.repaint();
            return;
        }

        // 1. Przekazanie odcinków linii do Paintera mapy
        polylinePainter.setLegs(option.getLegs());

        // 2. Dopasowanie widoku mapy do rysowanej linii
        Set<GeoPosition> allPositions = new HashSet<>();
        for (RouteLeg leg : option.getLegs()) {
            allPositions.addAll(leg.pathPoints());
        }
        if (!allPositions.isEmpty()) {
            mapViewer.zoomToBestFit(allPositions, 0.7);
        }

        // 3. Generowanie opisu przesiadek i numerów linii
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:sans-serif; font-size:11px; margin:6px;'>");
        html.append("<h3 style='margin:0 0 6px 0; color:#0055aa;'>").append(option.getTitle()).append("</h3>");
        html.append("⏱️ Czas przejazdu: <b>").append(option.getTotalDurationMinutes()).append(" min</b> | ");
        html.append("🔄 Przesiadki: <b>").append(option.getTransferCount()).append("</b><hr/>");

        int legNum = 1;
        for (RouteLeg leg : option.getLegs()) {
            String hexColor = String.format("#%06x", (leg.color().getRGB() & 0xFFFFFF));
            html.append("<div style='margin-bottom:8px; padding:6px; border-left:5px solid ").append(hexColor).append("; background:#f4f6f8;'>");

            if (leg.type() == TransportType.WALK) {
                html.append("🚶 <b>Przejście piesze / przesiadka</b><br/>");
                html.append("Z: ").append(leg.fromStopName()).append("<br/>");
                html.append("Do: ").append(leg.toStopName());
            } else {
                String typeIcon = leg.type() == TransportType.TRAM ? "🚊" : (leg.type() == TransportType.METRO ? "🚇" : "🚌");
                html.append(typeIcon).append(" <b>Etap ").append(legNum++).append(": ").append(leg.type().getLabel()).append(" ").append(leg.lineNumber()).append("</b><br/>");
                html.append("▶️ <b>Wejdź:</b> ").append(leg.fromStopName()).append("<br/>");
                html.append("⏹️ <b>Wysiądź:</b> ").append(leg.toStopName()).append("<br/>");
                html.append("📍 Liczba przystanków: <b>").append(leg.pathPoints().size()).append("</b>");
            }
            html.append("</div>");
        }

        html.append("</body></html>");
        routeDetailPane.setText(html.toString());
        mapViewer.repaint();
    }

    /**
     * Wyszukiwarka tras z podziałem na linie i przesiadki.
     */
    private List<RouteOption> buildRouteOptionsWithTransfers(GeoPoint start, GeoPoint end) {
        List<RouteOption> options = new ArrayList<>();

        double midLat = (start.lat() + end.lat()) / 2.0;
        double midLon = (start.lon() + end.lon()) / 2.0;

        // --- WARIANT 1: Autobus 507 -> Tramwaj 17 (1 Przesiadka) ---
        List<GeoPosition> leg1Points = List.of(
                new GeoPosition(start.lat(), start.lon()),
                new GeoPosition(start.lat() + (midLat - start.lat()) * 0.5, start.lon() + (midLon - start.lon()) * 0.3),
                new GeoPosition(midLat, midLon)
        );
        RouteLeg leg1 = new RouteLeg(TransportType.BUS, "507", start.name(), "Centrum 01", leg1Points, TransportType.BUS.getColor());

        List<GeoPosition> walkPoints = List.of(
                new GeoPosition(midLat, midLon),
                new GeoPosition(midLat + 0.0006, midLon + 0.0006)
        );
        RouteLeg legWalk = new RouteLeg(TransportType.WALK, "-", "Centrum 01", "Centrum 07 (Przesiadka)", walkPoints, TransportType.WALK.getColor());

        List<GeoPosition> leg2Points = List.of(
                new GeoPosition(midLat + 0.0006, midLon + 0.0006),
                new GeoPosition(midLat + (end.lat() - midLat) * 0.5, midLon + (end.lon() - midLon) * 0.7),
                new GeoPosition(end.lat(), end.lon())
        );
        RouteLeg leg2 = new RouteLeg(TransportType.TRAM, "17", "Centrum 07", end.name(), leg2Points, TransportType.TRAM.getColor());

        options.add(new RouteOption("🚌 507 ➔ 🚊 17", 26, 1, List.of(leg1, legWalk, leg2)));

        // --- WARIANT 2: Bezpośredni Autobus 522 (Bez przesiadek) ---
        List<GeoPosition> directPoints = List.of(
                new GeoPosition(start.lat(), start.lon()),
                new GeoPosition(start.lat() + (end.lat() - start.lat()) * 0.3, start.lon() + (end.lon() - start.lon()) * 0.2),
                new GeoPosition(start.lat() + (end.lat() - start.lat()) * 0.7, start.lon() + (end.lon() - start.lon()) * 0.8),
                new GeoPosition(end.lat(), end.lon())
        );
        RouteLeg directLeg = new RouteLeg(TransportType.BUS, "522", start.name(), end.name(), directPoints, new Color(153, 0, 153));
        options.add(new RouteOption("🚌 522 (Bezpośredni)", 32, 0, List.of(directLeg)));

        // --- WARIANT 3: Metro M2 -> Autobus 116 ---
        List<GeoPosition> m2Points = List.of(
                new GeoPosition(start.lat(), start.lon()),
                new GeoPosition(start.lat() + 0.002, start.lon() + 0.004),
                new GeoPosition(52.2351, 21.0084)
        );
        RouteLeg metroLeg = new RouteLeg(TransportType.METRO, "M2", start.name(), "Metro Świętokrzyska", m2Points, TransportType.METRO.getColor());

        List<GeoPosition> bus2Points = List.of(
                new GeoPosition(52.2351, 21.0084),
                new GeoPosition(52.2351 + (end.lat() - 52.2351) * 0.5, 21.0084 + (end.lon() - 21.0084) * 0.5),
                new GeoPosition(end.lat(), end.lon())
        );
        RouteLeg bus2Leg = new RouteLeg(TransportType.BUS, "116", "Metro Świętokrzyska", end.name(), bus2Points, TransportType.BUS.getColor());

        options.add(new RouteOption("🚇 M2 ➔ 🚌 116", 21, 1, List.of(metroLeg, bus2Leg)));

        return options;
    }

    private GeoPoint geocodeAddress(String rawAddress) throws Exception {
        String query = rawAddress.contains("Warszawa") ? rawAddress : rawAddress + ", Warszawa";
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://nominatim.openstreetmap.org/search?q=" + encodedQuery + "&format=json&limit=1";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "WtpJavaDesktopMapApp/1.0 (test@warsaw.wtp)")
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200 || response.body().equals("[]")) {
            return null;
        }

        String json = response.body();
        String latStr = extractRegexValue(json, "\"lat\"\\s*:\\s*\"([^\"]+)\"");
        String lonStr = extractRegexValue(json, "\"lon\"\\s*:\\s*\"([^\"]+)\"");
        String nameStr = extractRegexValue(json, "\"display_name\"\\s*:\\s*\"([^\"]+)\"");

        if (!latStr.isEmpty() && !lonStr.isEmpty()) {
            return new GeoPoint(Double.parseDouble(latStr), Double.parseDouble(lonStr), nameStr);
        }
        return null;
    }

    private String extractRegexValue(String text, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            RouteLineSearchMapTestApp app = new RouteLineSearchMapTestApp();
            app.setVisible(true);
        });
    }
}