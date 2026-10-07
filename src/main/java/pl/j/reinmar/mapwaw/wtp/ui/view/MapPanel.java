package pl.j.reinmar.mapwaw.wtp.ui.view;

import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.OSMTileFactoryInfo;
import org.jxmapviewer.input.PanMouseInputListener;
import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.DefaultWaypoint;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.TileFactoryInfo;
import org.jxmapviewer.viewer.Waypoint;
import org.jxmapviewer.viewer.WaypointPainter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pl.j.reinmar.mapwaw.wtp.model.LiveVehiclePosition;
import pl.j.reinmar.mapwaw.wtp.repository.RealtimeVehicleCache;

import javax.swing.*;
import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Point2D;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Komponent interfejsu Swing/AWT reprezentujący kontener mapy OpenStreetMap.
 * Odpowiada za obsługę zdarzeń interaktywnych, w tym dynamiczne wyświetlanie okna
 * podręcznego (Tooltip) ze szczegółami pojazdu po najechaniu myszą.
 */
public class MapPanel extends JPanel {

    private static final Logger logger = LoggerFactory.getLogger(MapPanel.class);

    private static final double WARSAW_CENTER_LAT = 52.229712;
    private static final double WARSAW_CENTER_LON = 21.012234;
    private static final int DEFAULT_ZOOM_LEVEL = 5;
    private static final double CLICK_RADIUS_PIXELS = 15.0;
    private static final double VEHICLE_HOVER_RADIUS_PIXELS = 12.0; // Promień wykrywania najechania myszą na pojazd

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final JXMapViewer mapViewer;
    private final WaypointPainter<Waypoint> waypointPainter;
    private final Set<Waypoint> waypoints = new HashSet<>();

    private RealtimeVehicleCache vehicleCache;
    private Consumer<Waypoint> onStopSelectedListener;

    /**
     * Domyślny konstruktor inicjalizujący silnik mapy JXMapViewer2 oraz interakcję Tooltipa.
     */
    public MapPanel() {
        logger.info("Inicjalizacja komponentu MapPanel wraz z obsługą okna podręcznego (Tooltip)...");
        setLayout(new BorderLayout());

        System.setProperty("http.agent", "WtpSwingNavigator/1.0 (pl.j.reinmar.mapwaw.wtp)");

        this.mapViewer = new JXMapViewer();

        // Skonfigurowanie czułości i opóźnienia pojawiania się Tooltipa w Swing ToolTipManager
        ToolTipManager.sharedInstance().setInitialDelay(200);
        ToolTipManager.sharedInstance().setDismissDelay(8000);

        TileFactoryInfo info = new OSMTileFactoryInfo("OpenStreetMap", "https://tile.openstreetmap.org");
        DefaultTileFactory tileFactory = new DefaultTileFactory(info);
        tileFactory.setThreadPoolSize(8);
        this.mapViewer.setTileFactory(tileFactory);

        GeoPosition warsawCenter = new GeoPosition(WARSAW_CENTER_LAT, WARSAW_CENTER_LON);
        this.mapViewer.setAddressLocation(warsawCenter);
        this.mapViewer.setZoom(DEFAULT_ZOOM_LEVEL);

        setupMouseNavigation();
        setupStopSelectionMouseListener();

        // KROK 62: Podpięcie słuchacza ruchu myszy do dynamicznego Tooltipa dla pojazdów
        setupVehicleTooltipMouseListener();

        this.waypointPainter = new WaypointPainter<>();
        this.waypointPainter.setRenderer(new StopWaypointRenderer());
        this.waypointPainter.setWaypoints(this.waypoints);
        this.mapViewer.setOverlayPainter(this.waypointPainter);

        add(mapViewer, BorderLayout.CENTER);

        logger.info("Mechanizm Tooltip pojazdów został pomyślnie skonfigurowany.");
    }

    /**
     * Rejestruje repozytorium/cache pozycji pojazdów do potrzeb wyliczania Tooltipa.
     *
     * @param vehicleCache instancja RealtimeVehicleCache
     */
    public void setVehicleCache(RealtimeVehicleCache vehicleCache) {
        this.vehicleCache = vehicleCache;
    }

    private void setupMouseNavigation() {
        MouseInputListener panListener = new PanMouseInputListener(mapViewer);
        this.mapViewer.addMouseListener(panListener);
        this.mapViewer.addMouseMotionListener(panListener);

        this.mapViewer.addMouseWheelListener(e -> {
            int currentZoom = mapViewer.getZoom();
            if (e.getWheelRotation() < 0) {
                mapViewer.setZoom(Math.max(1, currentZoom - 1));
            } else {
                mapViewer.setZoom(Math.min(15, currentZoom + 1));
            }
        });
    }

    private void setupStopSelectionMouseListener() {
        this.mapViewer.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) {
                    return;
                }

                Point clickPoint = e.getPoint();
                Waypoint selectedStop = findNearestWaypointAtPoint(clickPoint);

                if (selectedStop != null && onStopSelectedListener != null) {
                    onStopSelectedListener.accept(selectedStop);
                }
            }
        });
    }

    /**
     * KROK 62: Rejestruje nasłuchiwanie ruchu myszy i wyznacza pojazd znajdujący się pod kursorem.
     */
    private void setupVehicleTooltipMouseListener() {
        this.mapViewer.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (vehicleCache == null) {
                    mapViewer.setToolTipText(null);
                    return;
                }

                Point mousePoint = e.getPoint();
                LiveVehiclePosition hoveredVehicle = findVehicleAtPoint(mousePoint);

                if (hoveredVehicle != null) {
                    mapViewer.setToolTipText(buildVehicleTooltipHtml(hoveredVehicle));
                } else {
                    mapViewer.setToolTipText(null);
                }
            }
        });
    }

    /**
     * Odnajduje pojazd w pamięci cache, którego ikona leży w sąsiedztwie kursora myszy.
     */
    private LiveVehiclePosition findVehicleAtPoint(Point mousePoint) {
        if (vehicleCache == null) return null;

        Collection<LiveVehiclePosition> positions = vehicleCache.getAllPositions();
        Rectangle viewportBounds = mapViewer.getViewportBounds();

        LiveVehiclePosition nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (LiveVehiclePosition pos : positions) {
            if (pos.getLatitude() == 0.0 && pos.getLongitude() == 0.0) continue;

            GeoPosition geoPos = new GeoPosition(pos.getLatitude(), pos.getLongitude());
            Point2D pixelPt = mapViewer.getTileFactory().geoToPixel(geoPos, mapViewer.getZoom());

            double screenX = pixelPt.getX() - viewportBounds.x;
            double screenY = pixelPt.getY() - viewportBounds.y;

            double distance = mousePoint.distance(screenX, screenY);

            if (distance <= VEHICLE_HOVER_RADIUS_PIXELS && distance < minDistance) {
                minDistance = distance;
                nearest = pos;
            }
        }

        return nearest;
    }

    /**
     * KROK 62: Buduje strukturę dokumentu HTML prezentującą kartę szczegółów pojazdu.
     */
    private String buildVehicleTooltipHtml(LiveVehiclePosition v) {
        boolean isTram = isTramLine(v.getLineNumber());
        String vehicleType = isTram ? "Tramwaj" : "Autobus";
        String headerColor = isTram ? "#CC0000" : "#0066CC";

        String delayText = (v.getDelaySeconds() > 0)
                ? String.format("<span style='color:red;'>+%d s (opóźnienie)</span>", v.getDelaySeconds())
                : "<span style='color:green;'>O czasie / Brak opóźnienia</span>";

        String lastTimeStr = (v.getLastUpdate() != null)
                ? TIME_FORMATTER.format(v.getLastUpdate())
                : "Nieznany";

        return String.format("""
                <html>
                <body style='padding:5px; font-family:sans-serif;'>
                    <b style='font-size:12pt; color:%s;'>%s — Linia %s</b>
                    <hr style='border:0; border-top:1px solid #ccc;'/>
                    <table border='0' cellpadding='2' style='font-size:10pt;'>
                        <tr><td><b>Nr taborowy:</b></td><td>#%s</td></tr>
                        <tr><td><b>Brygada:</b></td><td>%s</td></tr>
                        <tr><td><b>Kierunek / Azymut:</b></td><td>%.1f°</td></tr>
                        <tr><td><b>Pozycja GPS:</b></td><td>%.5f, %.5f</td></tr>
                        <tr><td><b>Status kursu:</b></td><td>%s</td></tr>
                        <tr><td><b>Ostatni sygnał GPS:</b></td><td>%s</td></tr>
                    </table>
                </body>
                </html>
                """,
                headerColor,
                vehicleType,
                v.getLineNumber() != null ? v.getLineNumber() : "N/A",
                v.getVehicleId() != null ? v.getVehicleId() : "N/A",
                v.getBrigade() != null ? v.getBrigade() : "N/A",
                v.getBearing(),
                v.getLatitude(),
                v.getLongitude(),
                delayText,
                lastTimeStr
        );
    }

    private boolean isTramLine(String line) {
        if (line == null || line.isBlank()) return false;
        try {
            int lineNum = Integer.parseInt(line.trim());
            return lineNum > 0 && lineNum < 100;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private Waypoint findNearestWaypointAtPoint(Point clickPoint) {
        Waypoint nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (Waypoint wp : waypoints) {
            Point2D wpPixelPoint = mapViewer.getTileFactory().geoToPixel(wp.getPosition(), mapViewer.getZoom());
            Rectangle viewportBounds = mapViewer.getViewportBounds();

            double screenX = wpPixelPoint.getX() - viewportBounds.x;
            double screenY = wpPixelPoint.getY() - viewportBounds.y;

            double distance = clickPoint.distance(screenX, screenY);

            if (distance <= CLICK_RADIUS_PIXELS && distance < minDistance) {
                minDistance = distance;
                nearest = wp;
            }
        }

        return nearest;
    }

    public void setOnStopSelectedListener(Consumer<Waypoint> listener) {
        this.onStopSelectedListener = listener;
    }

    public void setWaypoints(Collection<? extends Waypoint> newWaypoints) {
        this.waypoints.clear();
        if (newWaypoints != null) {
            this.waypoints.addAll(newWaypoints);
        }
        this.waypointPainter.setWaypoints(this.waypoints);
        this.mapViewer.repaint();
    }

    public void addWaypoint(double latitude, double longitude) {
        this.waypoints.add(new DefaultWaypoint(new GeoPosition(latitude, longitude)));
        this.waypointPainter.setWaypoints(this.waypoints);
        this.mapViewer.repaint();
    }

    public void clearWaypoints() {
        this.waypoints.clear();
        this.waypointPainter.setWaypoints(this.waypoints);
        this.mapViewer.repaint();
    }

    public JXMapViewer getMapViewer() {
        return mapViewer;
    }

    public WaypointPainter<Waypoint> getWaypointPainter() {
        return waypointPainter;
    }
}