package pl.j.reinmar.mapwaw.wtp.ui.view;

import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.OSMTileFactoryInfo;
import org.jxmapviewer.input.PanMouseInputListener;
import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.TileFactoryInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.MouseInputListener;
import java.awt.*;

/**
 * Komponent interfejsu Swing/AWT reprezentujący kontener mapy OpenStreetMap.
 * Odpowiada za skonfigurowanie dostawcy kafelków (TileFactory po HTTPS)
 * oraz obsługę zdarzeń myszy (przesuwanie przeciągnięciem oraz zoom rolką myszy).
 */
public class MapPanel extends JPanel {

    private static final Logger logger = LoggerFactory.getLogger(MapPanel.class);

    private static final double WARSAW_CENTER_LAT = 52.229712;
    private static final double WARSAW_CENTER_LON = 21.012234;
    private static final int DEFAULT_ZOOM_LEVEL = 5;

    private final JXMapViewer mapViewer;

    /**
     * Domyślny konstruktor inicjalizujący silnik mapy JXMapViewer2 i podpinający słuchaczy zdarzeń myszy.
     */
    public MapPanel() {
        logger.info("Inicjalizacja komponentu MapPanel wraz z obsługą zdarzeń myszy...");
        setLayout(new BorderLayout());

        // Wymuszenie User-Agent dla serwerów OpenStreetMap
        System.setProperty("http.agent", "WtpSwingNavigator/1.0 (pl.j.reinmar.mapwaw.wtp)");

        this.mapViewer = new JXMapViewer();

        // Skonfigurowanie dostawcy kafelków (TileFactory po HTTPS)
        TileFactoryInfo info = new OSMTileFactoryInfo("OpenStreetMap", "https://tile.openstreetmap.org");
        DefaultTileFactory tileFactory = new DefaultTileFactory(info);
        tileFactory.setThreadPoolSize(8);
        this.mapViewer.setTileFactory(tileFactory);

        // Ustawienie punktu startowego (Centrum Warszawy) oraz domyślnego poziomu zoom
        GeoPosition warsawCenter = new GeoPosition(WARSAW_CENTER_LAT, WARSAW_CENTER_LON);
        this.mapViewer.setAddressLocation(warsawCenter);
        this.mapViewer.setZoom(DEFAULT_ZOOM_LEVEL);

        // KROK 54: Włączenie obsługi zdarzeń myszy (przesuwanie przeciągnięciem + zoom rolką myszy)
        setupMouseNavigation();

        add(mapViewer, BorderLayout.CENTER);

        logger.info("Nawigacja myszą (Pan & Zoom) została pomyślnie podpięta do komponentu mapy.");
    }

    /**
     * Podpina słuchaczy zdarzeń zdarzeń przeciągania myszą oraz obsługi rolki myszy.
     */
    private void setupMouseNavigation() {
        // 1. Słuchacz przesuwania mapy przeciągnięciem (Pan)
        MouseInputListener panListener = new PanMouseInputListener(mapViewer);
        this.mapViewer.addMouseListener(panListener);
        this.mapViewer.addMouseMotionListener(panListener);

        // 2. Słuchacz zoomu rolką myszy w czystym Swing/AWT (bez przestarzałych adapterów)
        this.mapViewer.addMouseWheelListener(e -> {
            int currentZoom = mapViewer.getZoom();
            if (e.getWheelRotation() < 0) {
                // Przybliżanie (mniejsza wartość zoom w JXMapViewer2 = większe zbliżenie)
                mapViewer.setZoom(Math.max(1, currentZoom - 1));
            } else {
                // Oddalanie (maksymalny zasięg zdefiniowany na 15)
                mapViewer.setZoom(Math.min(15, currentZoom + 1));
            }
        });
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