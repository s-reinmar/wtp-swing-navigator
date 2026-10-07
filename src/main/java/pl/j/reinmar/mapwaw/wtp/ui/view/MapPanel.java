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
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Komponent interfejsu Swing/AWT reprezentujący kontener mapy OpenStreetMap
 * z własnym rendererem przystanków StopWaypointRenderer.
 */
public class MapPanel extends JPanel {

    private static final Logger logger = LoggerFactory.getLogger(MapPanel.class);

    private static final double WARSAW_CENTER_LAT = 52.229712;
    private static final double WARSAW_CENTER_LON = 21.012234;
    private static final int DEFAULT_ZOOM_LEVEL = 5;

    private final JXMapViewer mapViewer;
    private final WaypointPainter<Waypoint> waypointPainter;
    private final Set<Waypoint> waypoints = new HashSet<>();

    public MapPanel() {
        logger.info("Inicjalizacja komponentu MapPanel z StopWaypointRenderer...");
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

        // KROK 56: Podpięcie customowego StopWaypointRenderer pod WaypointPainter
        this.waypointPainter = new WaypointPainter<>();
        this.waypointPainter.setRenderer(new StopWaypointRenderer());
        this.waypointPainter.setWaypoints(this.waypoints);
        this.mapViewer.setOverlayPainter(this.waypointPainter);

        add(mapViewer, BorderLayout.CENTER);

        logger.info("Customowy StopWaypointRenderer został pomyślnie podpięty do nakładki mapy.");
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