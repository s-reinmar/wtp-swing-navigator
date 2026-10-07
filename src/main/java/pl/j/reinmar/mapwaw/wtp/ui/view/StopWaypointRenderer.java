package pl.j.reinmar.mapwaw.wtp.ui.view;

import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.Waypoint;
import org.jxmapviewer.viewer.WaypointRenderer;

import java.awt.*;
import java.awt.geom.Point2D;

/**
 * Customowy renderer dla JXMapViewer2 odpowiadający za rysowanie ikon przystanków komunikacji miejskiej.
 */
public class StopWaypointRenderer implements WaypointRenderer<Waypoint> {

    private static final Color STOP_COLOR = new Color(0, 102, 204); // Niebieski barw ZTM
    private static final Color BORDER_COLOR = Color.WHITE;
    private static final int DIAMETER = 12;

    @Override
    public void paintWaypoint(Graphics2D g, JXMapViewer map, Waypoint waypoint) {
        // Konwersja pozycji geograficznej GeoPosition na pozycję pikselową na ekranie
        Point2D point = map.getTileFactory().geoToPixel(waypoint.getPosition(), map.getZoom());

        int x = (int) point.getX();
        int y = (int) point.getY();

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 1. Rysowanie zewnętrznego cienia/obramowania
        g2d.setColor(BORDER_COLOR);
        g2d.fillOval(x - (DIAMETER / 2) - 2, y - (DIAMETER / 2) - 2, DIAMETER + 4, DIAMETER + 4);

        // 2. Rysowanie właściwego piktogramu przystanku
        g2d.setColor(STOP_COLOR);
        g2d.fillOval(x - (DIAMETER / 2), y - (DIAMETER / 2), DIAMETER, DIAMETER);

        // 3. Wnętrze ikony (punkt centralny)
        g2d.setColor(Color.WHITE);
        g2d.fillOval(x - 2, y - 2, 4, 4);

        g2d.dispose();
    }
}