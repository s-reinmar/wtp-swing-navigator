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
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

/**
 * Nakładka na mapę (Painter) odpowiedzialna za renderowanie i rysowanie
 * aktualnych pozycji pojazdów na mapie. Zaimplementowano optymalizację przerysowywania (Culling)
 * ograniczającą rysowanie pojazdów i etykiet wyłącznie do aktualnego obszaru widoku (Viewport Bounds).
 */
public class VehicleOverlayPainter implements Painter<JXMapViewer> {

    private static final Logger logger = LoggerFactory.getLogger(VehicleOverlayPainter.class);

    // Margines bezpieczeństwa w pikselach dla obiektów usytuowanych przy samej krawędzi kadru
    private static final int VIEWPORT_MARGIN_PIXELS = 40;

    private final RealtimeVehicleCache vehicleCache;

    private BufferedImage busIcon;
    private BufferedImage tramIcon;

    /**
     * Konstruktor przyjmujący pamięć podręczną pozycji pojazdów.
     *
     * @param vehicleCache współbieżna pamięć podręczna RealtimeVehicleCache
     */
    public VehicleOverlayPainter(RealtimeVehicleCache vehicleCache) {
        this.vehicleCache = vehicleCache;
        loadVehicleIcons();
    }

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

        // KROK 63: Pobranie obszaru widocznego ekranu (Viewport) w pikselach globalnych
        Rectangle viewportBounds = map.getViewportBounds();

        // Rozszerzenie prostokąta widoczności o margines bezpieczeństwa dla etykiet/ikon przy krawędziach
        Rectangle expandedViewport = new Rectangle(
                viewportBounds.x - VIEWPORT_MARGIN_PIXELS,
                viewportBounds.y - VIEWPORT_MARGIN_PIXELS,
                viewportBounds.width + (VIEWPORT_MARGIN_PIXELS * 2),
                viewportBounds.height + (VIEWPORT_MARGIN_PIXELS * 2)
        );

        // Przesunięcie kontekstu graficznego AWT uwzględniające lewy górny róg widoku
        g2d.translate(-viewportBounds.x, -viewportBounds.y);

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        int totalVehicles = positions.size();
        int renderedVehicles = 0;

        for (LiveVehiclePosition pos : positions) {
            if (pos.getLatitude() == 0.0 && pos.getLongitude() == 0.0) {
                continue;
            }

            // Konwersja pozycji geograficznej na piksele mapy
            GeoPosition geoPos = new GeoPosition(pos.getLatitude(), pos.getLongitude());
            Point2D point = map.getTileFactory().geoToPixel(geoPos, map.getZoom());

            int x = (int) point.getX();
            int y = (int) point.getY();

            // KROK 63: FRUSTUM CULLING – Odrzucanie obiektów poza widocznym kadrem mapy
            if (!expandedViewport.contains(x, y)) {
                continue; // Pominięcie rysowania pojazdów znajdujących się poza widocznym obszarem okna
            }

            // Rysowanie ikony oraz etykiety wyłącznie dla obiektów wewnątrz widocznego kadrze
            drawRotatedVehicleIcon(g2d, x, y, pos);
            drawLineLabelAboveVehicle(g2d, x, y, pos);
            renderedVehicles++;
        }

        g2d.dispose();

        logger.trace("Optymalizacja przerysowywania: Wyrenderowano {} z {} pojazdów (odrzucono poza kadrem: {}).",
                renderedVehicles, totalVehicles, (totalVehicles - renderedVehicles));
    }

    private void drawRotatedVehicleIcon(Graphics2D g2d, int x, int y, LiveVehiclePosition vehicle) {
        BufferedImage icon = resolveVehicleIcon(vehicle);
        float bearing = vehicle.getBearing();

        if (icon != null) {
            int iconWidth = icon.getWidth();
            int iconHeight = icon.getHeight();

            AffineTransform oldTransform = g2d.getTransform();

            g2d.rotate(Math.toRadians(bearing), x, y);

            int drawX = x - (iconWidth / 2);
            int drawY = y - (iconHeight / 2);
            g2d.drawImage(icon, drawX, drawY, null);

            g2d.setTransform(oldTransform);
        } else {
            drawFallbackMarker(g2d, x, y, isTram(vehicle));
        }
    }

    private void drawLineLabelAboveVehicle(Graphics2D g2d, int x, int y, LiveVehiclePosition vehicle) {
        String lineNumber = vehicle.getLineNumber();
        if (lineNumber == null || lineNumber.isBlank()) {
            return;
        }

        g2d.setFont(new Font("SansSerif", Font.BOLD, 11));
        FontMetrics fm = g2d.getFontMetrics();

        int textWidth = fm.stringWidth(lineNumber);
        int textHeight = fm.getAscent();

        int paddingX = 5;
        int paddingY = 2;

        int boxWidth = textWidth + (paddingX * 2);
        int boxHeight = textHeight + (paddingY * 2);

        int boxX = x - (boxWidth / 2);
        int boxY = y - 22;

        g2d.setColor(new Color(255, 255, 255, 230));
        g2d.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 6, 6);

        Color borderCol = isTram(vehicle) ? new Color(204, 0, 0) : new Color(0, 102, 204);
        g2d.setColor(borderCol);
        g2d.setStroke(new BasicStroke(1.2f));
        g2d.drawRoundRect(boxX, boxY, boxWidth, boxHeight, 6, 6);

        int textX = boxX + paddingX;
        int textY = boxY + textHeight + (paddingY / 2) - 1;
        g2d.setColor(Color.BLACK);
        g2d.drawString(lineNumber, textX, textY);
    }

    private BufferedImage resolveVehicleIcon(LiveVehiclePosition vehicle) {
        if (isTram(vehicle)) {
            return tramIcon != null ? tramIcon : busIcon;
        }
        return busIcon != null ? busIcon : tramIcon;
    }

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