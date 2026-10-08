package pl.j.reinmar.mapwaw.wtp.ui.view;

import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.OSMTileFactoryInfo;
import org.jxmapviewer.input.PanMouseInputListener;
import org.jxmapviewer.painter.Painter;
import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.DefaultWaypoint;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.TileFactoryInfo;
import org.jxmapviewer.viewer.Waypoint;
import org.jxmapviewer.viewer.WaypointPainter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.j.reinmar.mapwaw.wtp.model.LiveVehiclePosition;
import pl.j.reinmar.mapwaw.wtp.model.Stop;
import pl.j.reinmar.mapwaw.wtp.repository.RealtimeVehicleCache;
import pl.j.reinmar.mapwaw.wtp.service.RoutingEngine;

import javax.swing.*;
import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Komponent interfejsu Swing/AWT reprezentujący kontener mapy OpenStreetMap.
 * Zapewnia obsługę myszy, tooltipów dla pojazdów, wybór przystanków oraz czyszczenie zaznaczenia.
 */
public class MapPanel extends JPanel {

    private static final Logger logger = LoggerFactory.getLogger(MapPanel.class);

    private static final double WARSAW_CENTER_LAT = 52.229712;
    private static final double WARSAW_CENTER_LON = 21.012234;
    private static final int DEFAULT_ZOOM_LEVEL = 5;
    private static final double CLICK_RADIUS_PIXELS = 15.0;
    private static final double VEHICLE_HOVER_RADIUS_PIXELS = 12.0;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final JXMapViewer mapViewer;
    private final WaypointPainter<Waypoint> waypointPainter;
    private final RouteOverlayPainter routeOverlayPainter = new RouteOverlayPainter();
    private final Set<Waypoint> waypoints = new HashSet<>();
    private Painter<JXMapViewer> baseOverlayPainter;
    private Painter<? super JXMapViewer> additionalOverlayPainter;

    private volatile RealtimeVehicleCache vehicleCache;
    private Consumer<Waypoint> onStopSelectedListener;

    public MapPanel() {
        logger.info("Inicjalizacja komponentu MapPanel...");
        setLayout(new BorderLayout());

        System.setProperty("http.agent", "WtpSwingNavigator/1.0 (pl.j.reinmar.mapwaw.wtp)");

        this.mapViewer = new JXMapViewer();

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
        setupVehicleTooltipMouseListener();
        setupResizeListener();

        this.waypointPainter = new WaypointPainter<>();
        this.waypointPainter.setRenderer(new StopWaypointRenderer());
        this.waypointPainter.setWaypoints(this.waypoints);
        this.baseOverlayPainter = this.waypointPainter;
        this.mapViewer.setOverlayPainter(this::paintOverlays);

        add(mapViewer, BorderLayout.CENTER);
    }

    private void setupResizeListener() {
        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (mapViewer == null) return;
                GeoPosition currentCenter = mapViewer.getAddressLocation();
                mapViewer.revalidate();
                if (currentCenter != null) {
                    mapViewer.setAddressLocation(currentCenter);
                }
                mapViewer.repaint();
            }
        });
    }

    public void setVehicleCache(RealtimeVehicleCache vehicleCache) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setVehicleCache(vehicleCache));
            return;
        }
        this.vehicleCache = vehicleCache;
    }

    public void setAdditionalOverlayPainter(Painter<? super JXMapViewer> painter) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setAdditionalOverlayPainter(painter));
            return;
        }
        additionalOverlayPainter = painter;
        mapViewer.repaint();
    }

    /**
     * Sets the route to highlight without replacing waypoint or vehicle overlays.
     */
    public void highlightRoute(RoutingEngine.ScheduledRoute route) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> highlightRoute(route));
            return;
        }
        routeOverlayPainter.setRoute(route);
        if (route != null) {
            showRoute(route);
        }
        mapViewer.repaint();
    }

    public void clearHighlightedRoute() {
        highlightRoute(null);
    }

    public RoutingEngine.ScheduledRoute getHighlightedRoute() {
        return routeOverlayPainter.route;
    }

    private void paintOverlays(Graphics2D graphics, JXMapViewer map, int width, int height) {
        if (baseOverlayPainter != null) {
            baseOverlayPainter.paint(graphics, map, width, height);
        }
        if (additionalOverlayPainter != null) {
            additionalOverlayPainter.paint(graphics, map, width, height);
        }
        routeOverlayPainter.paint(graphics, map, width, height);
    }

    private void showRoute(RoutingEngine.ScheduledRoute route) {
        List<Stop> stops = route.legs().stream()
                .flatMap(leg -> leg.route().stops().stream())
                .filter(this::hasValidCoordinates)
                .toList();
        if (stops.isEmpty()) {
            return;
        }

        double minLatitude = stops.stream().mapToDouble(Stop::getLatitude).min().orElseThrow();
        double maxLatitude = stops.stream().mapToDouble(Stop::getLatitude).max().orElseThrow();
        double minLongitude = stops.stream().mapToDouble(Stop::getLongitude).min().orElseThrow();
        double maxLongitude = stops.stream().mapToDouble(Stop::getLongitude).max().orElseThrow();
        mapViewer.setAddressLocation(new GeoPosition(
                (minLatitude + maxLatitude) / 2, (minLongitude + maxLongitude) / 2));

        int zoom = mapViewer.getZoom();
        while (zoom < 15 && !routeFitsAtZoom(stops, zoom)) {
            zoom++;
        }
        mapViewer.setZoom(zoom);
    }

    private boolean routeFitsAtZoom(List<Stop> stops, int zoom) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (Stop stop : stops) {
            Point2D point = mapViewer.getTileFactory().geoToPixel(
                    new GeoPosition(stop.getLatitude(), stop.getLongitude()), zoom);
            minX = Math.min(minX, point.getX());
            maxX = Math.max(maxX, point.getX());
            minY = Math.min(minY, point.getY());
            maxY = Math.max(maxY, point.getY());
        }
        return maxX - minX <= mapViewer.getWidth() * 0.75
                && maxY - minY <= mapViewer.getHeight() * 0.75;
    }

    private boolean hasValidCoordinates(Stop stop) {
        return stop != null && Double.isFinite(stop.getLatitude())
                && stop.getLatitude() >= -90 && stop.getLatitude() <= 90
                && Double.isFinite(stop.getLongitude())
                && stop.getLongitude() >= -180 && stop.getLongitude() <= 180;
    }

    private final class RouteOverlayPainter implements Painter<JXMapViewer> {
        private static final Color ROUTE_COLOR = new Color(220, 45, 45);
        private static final Color ENDPOINT_COLOR = new Color(24, 130, 65);
        private static final Color TRANSFER_COLOR = new Color(235, 145, 20);
        private static final int ENDPOINT_RADIUS = 8;
        private volatile RoutingEngine.ScheduledRoute route;

        private void setRoute(RoutingEngine.ScheduledRoute route) {
            this.route = route;
        }

        @Override
        public void paint(Graphics2D graphics, JXMapViewer map,
                          int width, int height) {
            if (route == null) {
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));
                Path2D path = new Path2D.Double();
                boolean hasPoint = false;
                Stop start = null;
                Stop end = null;
                for (RoutingEngine.ScheduledLeg leg : route.legs()) {
                    List<Stop> stops = leg.route().stops();
                    for (int i = 0; i < stops.size(); i++) {
                        Stop stop = stops.get(i);
                        if (!MapPanel.this.hasValidCoordinates(stop)) {
                            continue;
                        }
                        Point2D point = toViewportPoint(stop, map);
                        if (!hasPoint) {
                            path.moveTo(point.getX(), point.getY());
                            start = stop;
                            hasPoint = true;
                        } else if (i > 0 || !sameStop(end, stop)) {
                            path.lineTo(point.getX(), point.getY());
                        }
                        end = stop;
                    }
                }
                if (hasPoint) {
                    g.setColor(ROUTE_COLOR);
                    g.draw(path);
                    paintStopMarker(g, start, map, ENDPOINT_COLOR);
                    if (!sameStop(start, end)) {
                        paintStopMarker(g, end, map, ENDPOINT_COLOR);
                    }
                    for (int i = 0; i < route.legs().size() - 1; i++) {
                        List<Stop> stops = route.legs().get(i).route().stops();
                        if (!stops.isEmpty()) {
                            paintStopMarker(g, stops.getLast(), map, TRANSFER_COLOR);
                        }
                    }
                }
            } finally {
                g.dispose();
            }
        }

        private Point2D toViewportPoint(Stop stop, JXMapViewer map) {
            Point2D point = map.getTileFactory().geoToPixel(
                    new GeoPosition(stop.getLatitude(), stop.getLongitude()), map.getZoom());
            Rectangle viewport = map.getViewportBounds();
            return new Point2D.Double(point.getX() - viewport.x, point.getY() - viewport.y);
        }

        private void paintStopMarker(Graphics2D g, Stop stop, JXMapViewer map, Color color) {
            if (!MapPanel.this.hasValidCoordinates(stop)) {
                return;
            }
            Point2D point = toViewportPoint(stop, map);
            int diameter = ENDPOINT_RADIUS * 2;
            g.setColor(Color.WHITE);
            g.fillOval((int) point.getX() - ENDPOINT_RADIUS - 2,
                    (int) point.getY() - ENDPOINT_RADIUS - 2, diameter + 4, diameter + 4);
            g.setColor(color);
            g.fillOval((int) point.getX() - ENDPOINT_RADIUS,
                    (int) point.getY() - ENDPOINT_RADIUS, diameter, diameter);
        }

        private boolean sameStop(Stop first, Stop second) {
            return first != null && second != null
                    && first.getId() != null && first.getId().equals(second.getId());
        }
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
                if (e.getButton() != MouseEvent.BUTTON1) return;

                Point clickPoint = e.getPoint();
                Waypoint selectedStop = findNearestWaypointAtPoint(clickPoint);

                if (selectedStop != null && onStopSelectedListener != null) {
                    onStopSelectedListener.accept(selectedStop);
                }
            }
        });
    }

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
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setOnStopSelectedListener(listener));
            return;
        }
        this.onStopSelectedListener = listener;
    }

    public void setWaypoints(Collection<? extends Waypoint> newWaypoints) {
        Set<Waypoint> snapshot = new HashSet<>();
        if (newWaypoints != null) {
            snapshot.addAll(newWaypoints);
        }
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> replaceWaypoints(snapshot));
            return;
        }
        replaceWaypoints(snapshot);
    }

    private void replaceWaypoints(Set<Waypoint> newWaypoints) {
        this.waypoints.clear();
        this.waypoints.addAll(newWaypoints);
        this.waypointPainter.setWaypoints(this.waypoints);
        this.mapViewer.repaint();
    }

    public void addWaypoint(double latitude, double longitude) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> addWaypoint(latitude, longitude));
            return;
        }
        this.waypoints.add(new DefaultWaypoint(new GeoPosition(latitude, longitude)));
        this.waypointPainter.setWaypoints(this.waypoints);
        this.mapViewer.repaint();
    }

    public void clearWaypoints() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::clearWaypoints);
            return;
        }
        this.waypoints.clear();
        this.waypointPainter.setWaypoints(this.waypoints);
        this.mapViewer.repaint();
    }

    /**
     * KROK 74: Metoda czyszcząca specyficzne zaznaczenie przystanku na mapie.
     * Wywoływana z poziomu MapController przy akcji powrotu do widoku ogólnego.
     */
    public void clearSelectedStop() {
        // Jeśli dodawałeś specjalną warstwę/kolor dla zaznaczonego przystanku
        // w StopWaypointRenderer, możesz go w tym miejscu wyzerować.
        // Obecnie wymuszamy jedynie przerysowanie mapy, co jest zachowaniem neutralnym i pożądanym.
        SwingUtilities.invokeLater(() -> {
            logger.debug("MapPanel: Czyszczenie widoku zaznaczonego przystanku.");
            mapViewer.repaint();
        });
    }

    public JXMapViewer getMapViewer() {
        return mapViewer;
    }

    public WaypointPainter<Waypoint> getWaypointPainter() {
        return waypointPainter;
    }
}