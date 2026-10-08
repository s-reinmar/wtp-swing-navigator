package pl.j.reinmar.mapwaw.wtp.ui.view;

import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.TileFactoryInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import pl.j.reinmar.mapwaw.wtp.model.LiveVehiclePosition;
import pl.j.reinmar.mapwaw.wtp.repository.RealtimeVehicleCache;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Długotrwały test obciążeniowy ciągłego renderowania mapy z ruchem pojazdów.
 * Weryfikuje responsywność EDT oraz brak wycieków pamięci.
 *
 * Wyłączony domyślnie. Uruchomienie godzinnego testu:
 * mvn test -Dtest=MapRenderingLoadTest -Dwtp.loadtest=true -Dwtp.loadtest.minutes=60
 * (parametr minut jest opcjonalny, domyślnie 60; dla szybkiej weryfikacji np. 2).
 */
@EnabledIfSystemProperty(named = "wtp.loadtest", matches = "true")
class MapRenderingLoadTest {

    private static final int VEHICLE_COUNT = 1000;
    private static final int TARGET_FRAME_MS = 50;
    private static final long PROBE_INTERVAL_MS = 250;
    private static final long MAX_EDT_LATENCY_MS = 2_000;
    private static final long MAX_P99_EDT_LATENCY_MS = 250;
    private static final long MAX_HEAP_GROWTH_BYTES = 64L * 1024 * 1024;
    private static final int MEMORY_SAMPLES = 20;

    @Test
    @DisplayName("Obciążenie: ciągłe renderowanie mapy, responsywność EDT i brak wycieków pamięci")
    void continuousRenderingKeepsEdtResponsiveAndHeapStable() throws Exception {
        long minutes = Long.getLong("wtp.loadtest.minutes", 60L);
        Duration testDuration = Duration.ofMinutes(minutes);

        RealtimeVehicleCache cache = new RealtimeVehicleCache();
        MapPanel[] panelRef = new MapPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            MapPanel panel = new MapPanel();
            panel.setSize(1280, 800);
            panel.doLayout();
            panel.getMapViewer().setSize(1280, 800);
            panel.getMapViewer().setTileFactory(offlineTileFactory());
            panel.setVehicleCache(cache);
            panel.getMapViewer().setOverlayPainter(new VehicleOverlayPainter(cache));
            panelRef[0] = panel;
        });
        MapPanel mapPanel = panelRef[0];

        Random random = new Random(42);
        for (int i = 0; i < VEHICLE_COUNT; i++) {
            cache.updatePosition(randomVehicle("TAB-" + i, random));
        }

        List<Long> edtLatenciesMs = new CopyOnWriteArrayList<>();
        AtomicBoolean running = new AtomicBoolean(true);
        Thread probe = startEdtProbe(running, edtLatenciesMs);

        BufferedImage buffer = new BufferedImage(1280, 800, BufferedImage.TYPE_INT_ARGB);
        List<Long> heapSamples = new ArrayList<>();
        long startNs = System.nanoTime();
        long durationNs = testDuration.toNanos();
        long sampleIntervalNs = durationNs / MEMORY_SAMPLES;
        long nextSampleNs = sampleIntervalNs;
        long frames = 0;
        long totalFrameNs = 0;
        long worstFrameNs = 0;

        try {
            while (System.nanoTime() - startNs < durationNs) {
                long frameStartNs = System.nanoTime();
                simulateVehicleMovement(cache, random);

                SwingUtilities.invokeAndWait(() -> {
                    Graphics2D g = buffer.createGraphics();
                    try {
                        mapPanel.getMapViewer().paint(g);
                    } finally {
                        g.dispose();
                    }
                });

                long frameNs = System.nanoTime() - frameStartNs;
                frames++;
                totalFrameNs += frameNs;
                worstFrameNs = Math.max(worstFrameNs, frameNs);

                if (System.nanoTime() - startNs >= nextSampleNs) {
                    heapSamples.add(usedHeapAfterGc());
                    nextSampleNs += sampleIntervalNs;
                }

                long sleepMs = TARGET_FRAME_MS - frameNs / 1_000_000;
                if (sleepMs > 0) {
                    Thread.sleep(sleepMs);
                }
            }
        } finally {
            running.set(false);
            probe.join(2_000);
        }

        report(minutes, frames, totalFrameNs, worstFrameNs, edtLatenciesMs, heapSamples);

        List<Long> sortedLatencies = new ArrayList<>(edtLatenciesMs);
        Collections.sort(sortedLatencies);
        assertTrue(!sortedLatencies.isEmpty(), "Brak próbek responsywności EDT.");
        long maxLatency = sortedLatencies.getLast();
        long p99Latency = sortedLatencies.get((int) Math.min(
                sortedLatencies.size() - 1, Math.ceil(sortedLatencies.size() * 0.99) - 1));
        assertTrue(maxLatency < MAX_EDT_LATENCY_MS,
                "EDT zawiesił się na " + maxLatency + " ms.");
        assertTrue(p99Latency < MAX_P99_EDT_LATENCY_MS,
                "Opóźnienie EDT p99 zbyt wysokie: " + p99Latency + " ms.");

        if (heapSamples.size() >= 8) {
            int quarter = heapSamples.size() / 4;
            double earlyAvg = average(heapSamples.subList(quarter, quarter * 2));
            double lateAvg = average(heapSamples.subList(heapSamples.size() - quarter, heapSamples.size()));
            assertTrue(lateAvg - earlyAvg < MAX_HEAP_GROWTH_BYTES,
                    String.format("Podejrzenie wycieku pamięci: przyrost sterty %.1f MB.",
                            (lateAvg - earlyAvg) / (1024 * 1024)));
        }
    }

    private static DefaultTileFactory offlineTileFactory() {
        TileFactoryInfo info = new TileFactoryInfo(1, 15, 17, 256, true, true,
                "http://127.0.0.1:9", "x", "y", "z") {
            @Override
            public String getTileUrl(int x, int y, int zoom) {
                return baseURL + "/" + (getTotalMapZoom() - zoom) + "/" + x + "/" + y + ".png";
            }
        };
        return new DefaultTileFactory(info);
    }

    private static Thread startEdtProbe(AtomicBoolean running, List<Long> latenciesMs) {
        Thread probe = new Thread(() -> {
            while (running.get()) {
                long postedNs = System.nanoTime();
                try {
                    SwingUtilities.invokeAndWait(() -> { });
                    latenciesMs.add((System.nanoTime() - postedNs) / 1_000_000);
                    Thread.sleep(PROBE_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    return;
                }
            }
        }, "EDT-Responsiveness-Probe");
        probe.setDaemon(true);
        probe.start();
        return probe;
    }

    private static void simulateVehicleMovement(RealtimeVehicleCache cache, Random random) {
        for (int i = 0; i < VEHICLE_COUNT / 10; i++) {
            int id = random.nextInt(VEHICLE_COUNT);
            cache.updatePosition(randomVehicle("TAB-" + id, random));
        }
        if (random.nextInt(100) == 0) {
            int id = random.nextInt(VEHICLE_COUNT);
            cache.removePosition("TAB-" + id);
            cache.updatePosition(randomVehicle("TAB-" + id, random));
        }
    }

    private static LiveVehiclePosition randomVehicle(String id, Random random) {
        String line = random.nextInt(5) == 0
                ? String.valueOf(random.nextInt(30) + 1)
                : String.valueOf(random.nextInt(400) + 100);
        return new LiveVehiclePosition(id, line, "B-" + (random.nextInt(10) + 1),
                52.15 + random.nextDouble() * 0.20, 20.90 + random.nextDouble() * 0.30,
                (float) (random.nextDouble() * 360.0), Instant.now(), random.nextInt(300));
    }

    private static long usedHeapAfterGc() throws InterruptedException {
        for (int i = 0; i < 3; i++) {
            System.gc();
            Thread.sleep(100);
        }
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static double average(List<Long> values) {
        return values.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    private static void report(long minutes, long frames, long totalFrameNs, long worstFrameNs,
                               List<Long> latenciesMs, List<Long> heapSamples) {
        System.out.printf("=== Test obciążeniowy mapy (%d min) ===%n", minutes);
        System.out.printf("Klatki: %d, średnio %.2f ms, najgorsza %.2f ms%n",
                frames, totalFrameNs / 1e6 / Math.max(1, frames), worstFrameNs / 1e6);
        System.out.printf("Opóźnienie EDT: próbek %d, max %d ms%n", latenciesMs.size(),
                latenciesMs.stream().mapToLong(Long::longValue).max().orElse(0));
        System.out.print("Sterta po GC [MB]:");
        heapSamples.forEach(sample -> System.out.printf(" %.1f", sample / (1024.0 * 1024.0)));
        System.out.println();
    }
}
