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
import java.util.function.Consumer;

/**
 * Usługa cykliczna odpytująca API UM w tle z reakcją na błędy braku połączenia internetowego.
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
    private Consumer<Boolean> connectionStatusCallback; // Status sieci (true = OK, false = Błąd)
    private boolean lastConnectionState = true;

    public RealtimeFetchScheduler(WtpRealtimeApiClient apiClient,
                                  RealtimeVehicleCache vehicleCache,
                                  DelayCalculatorService delayCalculator) {
        this.apiClient = apiClient;
        this.jsonParser = new WtpRealtimeJsonParser();
        this.vehicleCache = vehicleCache;
        this.delayCalculator = delayCalculator;
    }

    public void setUiRefreshCallback(Runnable uiRefreshCallback) {
        this.uiRefreshCallback = uiRefreshCallback;
    }

    /**
     * Rejestruje nasłuchiwanie zmiany stanu połączenia z siecią (do paska statusu UI).
     */
    public void setConnectionStatusCallback(Consumer<Boolean> connectionStatusCallback) {
        this.connectionStatusCallback = connectionStatusCallback;
    }

    public synchronized void start() {
        start(DEFAULT_INTERVAL_SECONDS);
    }

    public synchronized void start(int intervalSeconds) {
        if (isRunning()) {
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "WTP-RealtimeFetch-Thread");
            thread.setDaemon(true);
            return thread;
        });

        logger.info("Uruchamianie pętli pobierania GPS co {} s z obsługą braków połączenia...", intervalSeconds);

        scheduledTask = scheduler.scheduleAtFixedRate(
                this::fetchAndProcessRealtimeData,
                0,
                intervalSeconds,
                TimeUnit.SECONDS
        );
    }

    public synchronized void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel(true);
        }
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        logger.info("Usługa RealtimeFetchScheduler została zatrzymana.");
    }

    public synchronized boolean isRunning() {
        return scheduledTask != null && !scheduledTask.isCancelled();
    }

    private void fetchAndProcessRealtimeData() {
        try {
            String busJson = apiClient.fetchBusPositions();
            String tramJson = apiClient.fetchTramPositions();

            // KROK 45: Sprawdzenie, czy zapytanie się powiodło (pusty wynik oznacz błąd sieci/timeout)
            if (busJson.isBlank() && tramJson.isBlank()) {
                notifyConnectionState(false);
                logger.warn("Brak połączenia lub serwer UM nie odpowiada. Pomięcie cyklu aktualizacji.");
                return;
            }

            // Połączenie udane
            notifyConnectionState(true);

            List<LiveVehiclePosition> busPositions = jsonParser.parseVehiclePositions(busJson);
            List<LiveVehiclePosition> tramPositions = jsonParser.parseVehiclePositions(tramJson);

            for (LiveVehiclePosition pos : busPositions) {
                if (delayCalculator != null) {
                    delayCalculator.calculateAndUpdateDelay(pos);
                }
                vehicleCache.updatePosition(pos);
            }

            for (LiveVehiclePosition pos : tramPositions) {
                if (delayCalculator != null) {
                    delayCalculator.calculateAndUpdateDelay(pos);
                }
                vehicleCache.updatePosition(pos);
            }

            if (uiRefreshCallback != null) {
                SwingUtilities.invokeLater(uiRefreshCallback);
            }

        } catch (Exception e) {
            notifyConnectionState(false);
            logger.error("Awarjna obsługa cyklu GPS: {}", e.getMessage());
        }
    }

    private void notifyConnectionState(boolean isConnected) {
        if (this.lastConnectionState != isConnected) {
            this.lastConnectionState = isConnected;
            if (connectionStatusCallback != null) {
                SwingUtilities.invokeLater(() -> connectionStatusCallback.accept(isConnected));
            }
        }
    }
}