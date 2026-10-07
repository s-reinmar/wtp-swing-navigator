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

import javax.swing.*;
import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Komponent interfejsu Swing/AWT reprezentujący kontener mapy OpenStreetMap.
 * Odpowiada za obsługę zdarzeń interaktywnych, w tym wybór przystanku po kliknięciu myszą na mapie.
 */
public class MapPanel extends JPanel {

    private static final Logger logger = LoggerFactory.getLogger(MapPanel.class);

    private static final double WARSAW_CENTER_LAT = 52.229712;
    private static final double WARSAW_CENTER_LON = 21.012234;
    private static final int DEFAULT_ZOOM_LEVEL = 5;
    private static final double CLICK_RADIUS_PIXELS = 15.0; // Promień tolerancji kliknięcia w pikselach

    private final JXMapViewer mapViewer;
    private final WaypointPainter<Waypoint> waypointPainter;
    private final Set<Waypoint> waypoints = new HashSet<>();

    private Consumer<Waypoint> onStopSelectedListener;

    /**
     * Domyślny konstruktor inicjalizujący silnik mapy JXMapViewer2 oraz interakcję kliknięcia.
     */
    public MapPanel() {
        logger.info("Inicjalizacja komponentu MapPanel z obsługą wyboru przystanku...");
        setLayout(new BorderLayout());

        System.setProperty("http.agent", "WtpSwingNavigator/1.0 (pl.j.reinmar.mapwaw.wtp)");

        this.mapViewer = new JXMapViewer();

        TileFactoryInfo info = new OSMTileFactoryInfo("OpenStreetMap", "https://tile.openstreetmap.org");
        DefaultTileFactory tileFactory = new DefaultTileFactory(info);
        tileFactory.setThreadPoolSize(8);
        this.mapViewer.setTileFactory(tileFactory);

        GeoPosition warsawCenter = new GeoPosition(WARSAW_CENTER_LAT, WARSAW_CENTER_LON);
        this.mapViewer.setAddressLocation(warsawCenter);
        this.mapViewer.setZoom(DEFAULT_ZOOM_LEVEL);

        setupMouseNavigation();

        // KROK 61: Zaimplementowanie mechanizmu wyboru przystanku po kliknięciu myszą na mapie
        setupStopSelectionMouseListener();

        this.waypointPainter = new WaypointPainter<>();
        this.waypointPainter.setRenderer(new StopWaypointRenderer());
        this.waypointPainter.setWaypoints(this.waypoints);
        this.mapViewer.setOverlayPainter(this.waypointPainter);

        add(mapViewer, BorderLayout.CENTER);

        logger.info("Mechanizm wyboru przystanku po kliknięciu na mapie został pomyślnie skonfigurowany.");
    }

    /**
     * Podpina słuchaczy zdarzeń przeciągania myszą oraz obsługi rolki myszy.
     */
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

    /**
     * KROK 61: Rejestruje słuchacza kliknięcia myszą w celu identyfikacji wybranego przystanku.
     */
    private void setupStopSelectionMouseListener() {
        this.mapViewer.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) {
                    return; // Obsługa wyłącznie lewego przycisku myszy
                }

                Point clickPoint = e.getPoint();
                Waypoint selectedStop = findNearestWaypointAtPoint(clickPoint);

                if (selectedStop != null) {
                    logger.info("Wybrano przystanek na mapie: Lat={}, Lon={}",
                            selectedStop.getPosition().getLatitude(),
                            selectedStop.getPosition().getLongitude());

                    if (onStopSelectedListener != null) {
                        onStopSelectedListener.accept(selectedStop);
                    }
                }
            }
        });
    }

    /**
     * Odnajduje najbliższy punkt Waypoint zlokalizowany w promieniu CLICK_RADIUS_PIXELS od miejsca kliknięcia.
     */
    private Waypoint findNearestWaypointAtPoint(Point clickPoint) {
        Waypoint nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (Waypoint wp : waypoints) {
            Point2D wpPixelPoint = mapViewer.getTileFactory().geoToPixel(wp.getPosition(), mapViewer.getZoom());

            // Przeliczenie uwzględniające przesunięcie widoku (viewport bounds)
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

    /**
     * Ustawia słuchacza/callback wywoływany w momencie zaznaczenia przystanku na mapie.
     *
     * @param listener funkcja przyjmująca zaznaczony Waypoint
     */
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