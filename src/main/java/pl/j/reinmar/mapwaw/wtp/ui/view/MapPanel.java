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
 * Komponent interfejsu Swing/AWT reprezentujący kontener mapy OpenStreetMap.
 * Odpowiada za skonfigurowanie dostawcy kafelków (TileFactory) z obsługą szyfrowanego
 * protokołu HTTPS oraz optymalizacją pobierania w tle.
 */
public class MapPanel extends JPanel {

    private static final Logger logger = LoggerFactory.getLogger(MapPanel.class);

    private final JXMapViewer mapViewer;

    /**
     * Domyślny konstruktor inicjalizujący silnik mapy JXMapViewer2 i fabrykę kafelków OSM.
     */
    public MapPanel() {
        logger.info("Inicjalizacja komponentu MapPanel...");
        setLayout(new BorderLayout());

        // KROK 52: Wymuszenie unikalnego nagłówka User-Agent (wymagane przez serwery OpenStreetMap)
        System.setProperty("http.agent", "WtpSwingNavigator/1.0 (pl.j.reinmar.mapwaw.wtp)");

        this.mapViewer = new JXMapViewer();

        // KROK 52: Skonfigurowanie dostawcy kafelków mapy (TileFactory) wskazanego na serwery OpenStreetMap
        TileFactoryInfo info = new OSMTileFactoryInfo("OpenStreetMap", "https://tile.openstreetmap.org");
        DefaultTileFactory tileFactory = new DefaultTileFactory(info);

        // Zwiększenie wielowątkowej puli pobierania kafelków w tle dla zachowania płynności UI
        tileFactory.setThreadPoolSize(8);

        this.mapViewer.setTileFactory(tileFactory);

        add(mapViewer, BorderLayout.CENTER);

        logger.info("Dostawca kafelków mapy OpenStreetMap (TileFactory po HTTPS) został pomyślnie skonfigurowany.");
    }

    /**
     * Zwraca wewnętrzną instancję JXMapViewer.
     *
     * @return instancja JXMapViewer
     */
    public JXMapViewer getMapViewer() {
        return mapViewer;
    }
}