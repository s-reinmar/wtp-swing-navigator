package pl.j.reinmar.mapwaw.wtp.ui.view;

import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.OSMTileFactoryInfo;
import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.TileFactoryInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;

/**
 * Komponent interfejsu Swing/AWT opakowujący silnik mapy JXMapViewer2.
 * Odpowiada za bezpieczną inicjalizację fabryki kafelków OpenStreetMap (HTTPS)
 * oraz dostarczanie kontenera widoku geograficznego.
 */
public class MapPanel extends JPanel {

    private static final Logger logger = LoggerFactory.getLogger(MapPanel.class);

    private final JXMapViewer mapViewer;

    /**
     * Domyślny konstruktor inicjalizujący pakiet komponentu mapy.
     */
    public MapPanel() {
        logger.info("Inicjalizacja komponentu MapPanel i oprawy JXMapViewer2...");
        setLayout(new BorderLayout());

        // 1. Utworzenie instancji JXMapViewer
        this.mapViewer = new JXMapViewer();

        // 2. Skonfigurowanie dostawcy kafelków OpenStreetMap po protokole HTTPS
        TileFactoryInfo info = new OSMTileFactoryInfo("OpenStreetMap", "https://tile.openstreetmap.org");
        DefaultTileFactory tileFactory = new DefaultTileFactory(info);
        tileFactory.setThreadPoolSize(8); // Zwiększenie puli wątków do wielowątkowego pobierania kafelków
        this.mapViewer.setTileFactory(tileFactory);

        // 3. Dodanie komponentu mapy w centralnym obszarze układu
        add(mapViewer, BorderLayout.CENTER);

        logger.info("Komponent MapPanel z instancją JXMapViewer2 został skonfigurowany.");
    }

    /**
     * Zwraca wewnętrzną instancję JXMapViewer w celu konfiguracji widoków, nakładek i zdarzeń.
     *
     * @return instancja JXMapViewer
     */
    public JXMapViewer getMapViewer() {
        return mapViewer;
    }
}