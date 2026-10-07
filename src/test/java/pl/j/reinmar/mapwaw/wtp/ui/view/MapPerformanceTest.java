package pl.j.reinmar.mapwaw.wtp.ui.view;

import org.jxmapviewer.viewer.DefaultWaypoint;
import org.jxmapviewer.viewer.GeoPosition;
import org.jxmapviewer.viewer.Waypoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.j.reinmar.mapwaw.wtp.model.LiveVehiclePosition;
import pl.j.reinmar.mapwaw.wtp.repository.RealtimeVehicleCache;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testy integracyjno-wydajnościowe weryfikujące płynność renderowania mapy (FPS)
 * oraz czas wykonania pętli repaint() przy dużym obciążeniu pojazdami na żywo.
 */
class MapPerformanceTest {

    private MapPanel mapPanel;
    private RealtimeVehicleCache vehicleCache;
    private VehicleOverlayPainter vehicleOverlayPainter;

    @BeforeEach
    void setUp() throws Exception {
        // Zapewnienie wykonania tworzenia komponentów na wątku Swing EDT
        SwingUtilities.invokeAndWait(() -> {
            mapPanel = new MapPanel();
            mapPanel.setSize(1280, 800);
            vehicleCache = new RealtimeVehicleCache();
            mapPanel.setVehicleCache(vehicleCache);

            vehicleOverlayPainter = new VehicleOverlayPainter(vehicleCache);
            mapPanel.getMapViewer().setOverlayPainter(vehicleOverlayPainter);
        });
    }

    @Test
    @DisplayName("Test wydajnościowy: Renderowanie 1000 pojazdów na mapie Swing (Target FPS > 30)")
    void testMapRenderingPerformanceWithThousandVehicles() throws Exception {
        // Given: Zapełnienie cache 1000 symulowanymi pojazdami rozsianymi po aglomeracji warszawskiej
        Random random = new Random(42);
        for (int i = 0; i < 1000; i++) {
            String line = (i % 5 == 0) ? String.valueOf(i % 30 + 1) : String.valueOf(i % 400 + 100);
            double lat = 52.15 + (random.nextDouble() * 0.20); // Zasięg Warszawy
            double lon = 20.90 + (random.nextDouble() * 0.30);

            LiveVehiclePosition vehicle = new LiveVehiclePosition(
                    "TAB-" + i,
                    line,
                    "B-" + (i % 10 + 1),
                    lat,
                    lon,
                    (float) (random.nextDouble() * 360.0), // bearing
                    Instant.now(),
                    random.nextInt(300)
            );
            vehicleCache.updatePosition(vehicle);
        }

        // When: Rysowanie klatek animacji na buforze obrazu w pamięci RAM (Off-screen Rendering)
        int frameCount = 100;
        BufferedImage offscreenBuffer = new BufferedImage(1280, 800, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = offscreenBuffer.createGraphics();

        long startTimeNs = System.nanoTime();

        SwingUtilities.invokeAndWait(() -> {
            for (int i = 0; i < frameCount; i++) {
                mapPanel.getMapViewer().paint(g2d);
            }
        });

        long elapsedTimeNs = System.nanoTime() - startTimeNs;
        g2d.dispose();

        // Then: Wyliczenie średniego czasu klatki (Frame Render Time) oraz oszacowanie FPS
        double totalTimeSeconds = elapsedTimeNs / 1_000_000_000.0;
        double avgFrameTimeMs = (elapsedTimeNs / 1_000_000.0) / frameCount;
        double estimatedFps = frameCount / totalTimeSeconds;

        System.out.printf("Wynik testu wydajności mapy Swing:%n");
        System.out.printf(" - Średni czas renderowania 1 klatki: %.2f ms%n", avgFrameTimeMs);
        System.out.printf(" - Szacowana płynność (FPS): %.1f FPS%n", estimatedFps);

        // Kryterium sukcesu: Renderowanie 1 klatki nie powinno przekraczać 33 ms (~30 FPS)
        assertTrue(avgFrameTimeMs < 33.0, "Czas renderowania klatki jest zbyt wysoki (spadek poniżej 30 FPS): " + avgFrameTimeMs + " ms");
    }

    @Test
    @DisplayName("Test optymalizacji: Sprawdzenie poprawności podwójnego buforowania (Double Buffering)")
    void testDoubleBufferingEnabled() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            // Weryfikacja, czy podwójne buforowanie jest domyślnie aktywne na komponentach MapPanel i JXMapViewer
            assertTrue(mapPanel.isDoubleBuffered(), "Komponent MapPanel powinien mieć włączone podwójne buforowanie (Double Buffering)");
            assertTrue(mapPanel.getMapViewer().isDoubleBuffered(), "Komponent JXMapViewer powinien mieć włączone podwójne buforowanie");
        });
    }

    @Test
    @DisplayName("Test skrajności: Przerysowywanie przy masowym dodawaniu i usuwaniu punktów Waypoint")
    void testMassiveWaypointRepaint() throws Exception {
        List<Waypoint> waypoints = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            waypoints.add(new DefaultWaypoint(new GeoPosition(52.22 + (i * 0.0001), 21.01 + (i * 0.0001))));
        }

        long startMs = System.currentTimeMillis();
        SwingUtilities.invokeAndWait(() -> {
            mapPanel.setWaypoints(waypoints);
            mapPanel.clearWaypoints();
        });
        long durationMs = System.currentTimeMillis() - startMs;

        assertTrue(durationMs < 500, "Operacja masowej podmienny punktów przystankowych zajęła zbyt dużo czasu: " + durationMs + " ms");
    }
}