package pl.j.reinmar.mapwaw.wtp.ui.view;

import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.OSMTileFactoryInfo;
import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.TileFactoryInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;

/**
 * Komponent interfejsu Swing/AWT reprezentujący kontener mapy OpenStreetMap.
 * Odpowiada za skonfigurowanie dostawcy kafelków (TileFactory) z obsługą szyfrowanego
 * protokołu HTTPS oraz ustawienie początkowego widoku geograficznego na centrum Warszawy.
 */
public class MapPanel extends JPanel {

    private static final Logger logger = LoggerFactory.getLogger(MapPanel.class);

    // KROK 53: Stałe geograficzne dla centrum Warszawy (skrzyżowanie Al. Jerozolimskie / Marszałkowska)
    private static final double WARSAW_CENTER_LAT = 52.229712;
    private static final double WARSAW_CENTER_LON = 21.012234;
    private static final int DEFAULT_ZOOM_LEVEL = 5; // Domyślny poziom powiększenia w JXMapViewer2

    private final JXMapViewer mapViewer;

    /**
     * Domyślny konstruktor inicjalizujący silnik mapy JXMapViewer2 i konfigurujący punkt startowy.
     */
    public MapPanel() {
        logger.info("Inicjalizacja komponentu MapPanel...");
        setLayout(new BorderLayout());

        // Wymuszenie User-Agent dla serwerów OpenStreetMap
        System.setProperty("http.agent", "WtpSwingNavigator/1.0 (pl.j.reinmar.mapwaw.wtp)");

        this.mapViewer = new JXMapViewer();

        // Skonfigurowanie dostawcy kafelków (TileFactory po HTTPS)
        TileFactoryInfo info = new OSMTileFactoryInfo("OpenStreetMap", "https://tile.openstreetmap.org");
        DefaultTileFactory tileFactory = new DefaultTileFactory(info);
        tileFactory.setThreadPoolSize(8);
        this.mapViewer.setTileFactory(tileFactory);

        // KROK 53: Ustawienie punktu startowego (centrum Warszawy) oraz domyślnego poziomu zoom
        GeoPosition warsawCenter = new GeoPosition(WARSAW_CENTER_LAT, WARSAW_CENTER_LON);
        this.mapViewer.setAddressLocation(warsawCenter);
        this.mapViewer.setZoom(DEFAULT_ZOOM_LEVEL);

        add(mapViewer, BorderLayout.CENTER);

        logger.info("Ustawiono punkt startowy mapy na centrum Warszawy ({}, {}) z zoomem {}.",
                WARSAW_CENTER_LAT, WARSAW_CENTER_LON, DEFAULT_ZOOM_LEVEL);
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