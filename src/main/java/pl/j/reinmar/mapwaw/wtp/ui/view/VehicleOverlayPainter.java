package pl.j.reinmar.mapwaw.wtp.ui.view;

import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.painter.Painter;
import org.jxmapviewer.viewer.GeoPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pl.j.reinmar.mapwaw.wtp.model.LiveVehiclePosition;
import pl.j.reinmar.mapwaw.wtp.repository.RealtimeVehicleCache;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.Collection;

/**
 * Nakładka na mapę (Painter) odpowiedzialna za renderowanie i rysowanie
 * aktualnych pozycji pojazdów pobranych z RealtimeVehicleCache.
 */
public class VehicleOverlayPainter implements Painter<JXMapViewer> {

    private static final Logger logger = LoggerFactory.getLogger(VehicleOverlayPainter.class);

    private final RealtimeVehicleCache vehicleCache;

    /**
     * Konstruktor przyjmujący repozytorium/pamięć podręczną pozycji pojazdów.
     *
     * @param vehicleCache współbieżna pamięć podręczna RealtimeVehicleCache
     */
    public VehicleOverlayPainter(RealtimeVehicleCache vehicleCache) {
        this.vehicleCache = vehicleCache;
    }

    @Override
    public void paint(Graphics2D g, JXMapViewer map, int width, int height) {
        if (vehicleCache == null) {
            return;
        }

        Collection<LiveVehiclePosition> positions = vehicleCache.getAllPositions();
        if (positions.isEmpty()) {
            return;
        }

        Graphics2D g2d = (Graphics2D) g.create();

        // Uwzględnienie przesunięcia widoku (viewport bounds) JXMapViewer2
        Rectangle viewportBounds = map.getViewportBounds();
        g2d.translate(-viewportBounds.x, -viewportBounds.y);

        // Włączenie antyaliasingu dla zapewnienia wysokiej jakości renderowania
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (LiveVehiclePosition pos : positions) {
            if (pos.getLatitude() == 0.0 && pos.getLongitude() == 0.0) {
                continue;
            }

            // Konwersja współrzędnych geograficznych na punkty pikselowe na mapie
            GeoPosition geoPos = new GeoPosition(pos.getLatitude(), pos.getLongitude());
            Point2D point = map.getTileFactory().geoToPixel(geoPos, map.getZoom());

            int x = (int) point.getX();
            int y = (int) point.getY();

            // Bazowe rysowanie punktu pozycji pojazdu
            drawVehicleMarker(g2d, x, y, pos);
        }

        g2d.dispose();
    }

    /**
     * Rysuje podstawowy znacznik pozycji pojazdu na wyliczonych współrzędnych ekranowych.
     */
    private void drawVehicleMarker(Graphics2D g2d, int x, int y, LiveVehiclePosition vehicle) {
        int radius = 8;

        // Domyślna kolorystyka ZTM (np. niebieska dla autobusów/pojazdów)
        g2d.setColor(new Color(0, 102, 204));
        g2d.fillOval(x - radius, y - radius, radius * 2, radius * 2);

        // Obramowanie
        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawOval(x - radius, y - radius, radius * 2, radius * 2);
    }
}