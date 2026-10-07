package pl.j.reinmar.mapwaw.wtp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.j.reinmar.mapwaw.wtp.model.LiveVehiclePosition;
import pl.j.reinmar.mapwaw.wtp.parser.WtpRealtimeJsonParser;
import pl.j.reinmar.mapwaw.wtp.repository.RealtimeVehicleCache;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Usługa tła wykorzystująca ScheduledExecutorService do cyklicznego odpytywania
 * interfejsu REST API ZTM Warszawa o aktualne pozycje GPS autobusów i tramwajów.
 */
public class RealtimeFetchScheduler {

    private static final Logger logger = LoggerFactory.getLogger(RealtimeFetchScheduler.class);
    private static final int DEFAULT_INTERVAL_SECONDS = 10;

    private final WtpRealtimeApiClient apiClient;
    private final WtpRealtimeJsonParser jsonParser;
    private final RealtimeVehicleCache vehicleCache;
    private final DelayCalculatorService delayCalculator;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledTask;
    private Runnable uiRefreshCallback;

    public RealtimeFetchScheduler(WtpRealtimeApiClient apiClient,
                                  RealtimeVehicleCache vehicleCache,
                                  DelayCalculatorService delayCalculator) {
        this.apiClient = apiClient;
        this.jsonParser = new WtpRealtimeJsonParser();
        this.vehicleCache = vehicleCache;
        this.delayCalculator = delayCalculator;
    }

    /**
     * Rejestruje funkcję zwrotną (callback) wywoływaną na wątku Swing EDT po każdej aktualizacji danych.
     */
    public void setUiRefreshCallback(Runnable uiRefreshCallback) {
        this.uiRefreshCallback = uiRefreshCallback;
    }

    /**
     * Uruchamia cykliczne odpytywanie API w tle z domyślnym interwałem 10 sekund.
     */
    public synchronized void start() {
        start(DEFAULT_INTERVAL_SECONDS);
    }

    /**
     * Uruchamia cykliczne odpytywanie API w tle ze wskazanym interwałem w sekundach (10–15 s).
     *
     * @param intervalSeconds interwał odświeżania w sekundach
     */
    public synchronized void start(int intervalSeconds) {
        if (isRunning()) {
            logger.warn("Usługa RealtimeFetchScheduler jest już uruchomiona.");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "WTP-RealtimeFetch-Thread");
            thread.setDaemon(true);
            return thread;
        });

        logger.info("Uruchamianie pętli pobierania GPS co {} sekund w tle...", intervalSeconds);

        scheduledTask = scheduler.scheduleAtFixedRate(
                this::fetchAndProcessRealtimeData,
                0,
                intervalSeconds,
                TimeUnit.SECONDS
        );
    }

    /**
     * Zatrzymuje pętlę odpytywania API w tle.
     */
    public synchronized void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel(true);
        }
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        logger.info("Usługa RealtimeFetchScheduler została zatrzymana.");
    }

    public synchronized boolean isRunning() {
        return scheduledTask != null && !scheduledTask.isCancelled();
    }

    /**
     * Główna metoda wykonywana cyklicznie w wątku tła.
     */
    private void fetchAndProcessRealtimeData() {
        try {
            logger.debug("Rozpoczęcie cyklu pobierania danych GPS z API ZTM...");

            // 1. Pobranie surowego JSON dla autobusów (type = 1) i tramwajów (type = 2)
            String busJson = apiClient.fetchBusPositions();
            String tramJson = apiClient.fetchTramPositions();

            // 2. Parsowanie i walidacja ramek GPS
            List<LiveVehiclePosition> busPositions = jsonParser.parseVehiclePositions(busJson);
            List<LiveVehiclePosition> tramPositions = jsonParser.parseVehiclePositions(tramJson);

            int updatedCount = 0;

            // 3. Obliczenie opóźnień i aktualizacja ConcurrentHashMap w cache
            for (LiveVehiclePosition pos : busPositions) {
                if (delayCalculator != null) {
                    delayCalculator.calculateAndUpdateDelay(pos);
                }
                vehicleCache.updatePosition(pos);
                updatedCount++;
            }

            for (LiveVehiclePosition pos : tramPositions) {
                if (delayCalculator != null) {
                    delayCalculator.calculateAndUpdateDelay(pos);
                }
                vehicleCache.updatePosition(pos);
                updatedCount++;
            }

            logger.info("Zakończono cykl GPS. Zaktualizowano pojazdów w cache: {} (Autobusy: {}, Tramwaje: {})",
                    updatedCount, busPositions.size(), tramPositions.size());

            // 4. Bezpieczne zlecenie przerysowania interfejsu na wątku Swing EDT
            if (uiRefreshCallback != null) {
                SwingUtilities.invokeLater(uiRefreshCallback);
            }

        } catch (Exception e) {
            logger.error("Błąd podczas cyklicznego pobierania danych GPS w tle: {}", e.getMessage(), e);
        }
    }
}