package pl.j.reinmar.mapwaw.wtp.ui.view;

import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.painter.Painter;
import org.jxmapviewer.viewer.GeoPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pl.j.reinmar.mapwaw.wtp.model.LiveVehiclePosition;
import pl.j.reinmar.mapwaw.wtp.repository.RealtimeVehicleCache;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/**
 * Nakładka na mapę (Painter) odpowiedzialna za renderowanie i rysowanie
 * aktualnych pozycji pojazdów z uwzględnieniem typu transportu (autobus vs tramwaj).
 */
public class VehicleOverlayPainter implements Painter<JXMapViewer> {

    private static final Logger logger = LoggerFactory.getLogger(VehicleOverlayPainter.class);

    private final RealtimeVehicleCache vehicleCache;

    private BufferedImage busIcon;
    private BufferedImage tramIcon;

    /**
     * Konstruktor przyjmujący pamięć podręczną pozycji pojazdów oraz ładujący ikony graficzne.
     *
     * @param vehicleCache współbieżna pamięć podręczna RealtimeVehicleCache
     */
    public VehicleOverlayPainter(RealtimeVehicleCache vehicleCache) {
        this.vehicleCache = vehicleCache;
        loadVehicleIcons();
    }

    /**
     * Wczytuje ikony pojazdów z folderu zasobów (resources/icons).
     */
    private void loadVehicleIcons() {
        this.busIcon = loadIconFromClasspath("icons/bus.png");
        this.tramIcon = loadIconFromClasspath("icons/tram.png");

        if (busIcon == null || tramIcon == null) {
            logger.warn("Nie udało się załadować części ikon pojazdów z zasobów. Zostanie użyty fallback.");
        }
    }

    private BufferedImage loadIconFromClasspath(String resourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                logger.error("Brak pliku zasobu ikony w classpath: {}", resourcePath);
                return null;
            }
            return ImageIO.read(is);
        } catch (IOException e) {
            logger.error("Błąd podczas odczytu pliku ikony: {}", resourcePath, e);
            return null;
        }
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

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        for (LiveVehiclePosition pos : positions) {
            if (pos.getLatitude() == 0.0 && pos.getLongitude() == 0.0) {
                continue;
            }

            // Konwersja pozycji geograficznej na piksele ekranu
            GeoPosition geoPos = new GeoPosition(pos.getLatitude(), pos.getLongitude());
            Point2D point = map.getTileFactory().geoToPixel(geoPos, map.getZoom());

            int x = (int) point.getX();
            int y = (int) point.getY();

            // KROK 58: Wyznaczenie i narysowanie właściwej ikony pojazdu
            drawVehicleWithIcon(g2d, x, y, pos);
        }

        g2d.dispose();
    }

    /**
     * Rysuje pozycję pojazdu na podstawie dedykowanej ikony (autobus/tramwaj).
     */
    private void drawVehicleWithIcon(Graphics2D g2d, int x, int y, LiveVehiclePosition vehicle) {
        BufferedImage icon = resolveVehicleIcon(vehicle);

        if (icon != null) {
            int iconWidth = icon.getWidth();
            int iconHeight = icon.getHeight();

            // Rysowanie ikony wycentrowanej w punkcie geograficznym pojazdu
            int drawX = x - (iconWidth / 2);
            int drawY = y - (iconHeight / 2);

            g2d.drawImage(icon, drawX, drawY, null);
        } else {
            // Fallback w przypadku braku załadowanej ikony .png
            drawFallbackMarker(g2d, x, y, isTram(vehicle));
        }
    }

    /**
     * Rozstrzyga, która ikona powinna zostać użyta na podstawie numeru linii lub typu transportu.
     */
    private BufferedImage resolveVehicleIcon(LiveVehiclePosition vehicle) {
        if (isTram(vehicle)) {
            return tramIcon != null ? tramIcon : busIcon;
        }
        return busIcon != null ? busIcon : tramIcon;
    }

    /**
     * Określa, czy pojazd jest tramwajem (np. linie 1-99).
     */
    private boolean isTram(LiveVehiclePosition vehicle) {
        if (vehicle.getLineNumber() == null || vehicle.getLineNumber().isBlank()) {
            return false;
        }
        try {
            int lineNum = Integer.parseInt(vehicle.getLineNumber().trim());
            return lineNum > 0 && lineNum < 100;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Zastępczy znacznik wektorowy na wypadek braku plików .png.
     */
    private void drawFallbackMarker(Graphics2D g2d, int x, int y, boolean isTram) {
        int radius = 8;
        Color color = isTram ? new Color(230, 81, 0) : new Color(2, 119, 189);

        g2d.setColor(color);
        g2d.fillOval(x - radius, y - radius, radius * 2, radius * 2);
        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawOval(x - radius, y - radius, radius * 2, radius * 2);
    }
}