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
 * Usługa tła wykorzystująca ScheduledExecutorService do cyklicznego odpytywania
 * interfejsu REST API ZTM Warszawa o aktualne pozycje GPS autobusów i tramwajów.
 *
 * Zapewnia automatyczne czyszczenie nieaktywnych pojazdów z pamięci cache (TTL),
 * monitorowanie połączenia oraz bezpieczną aktualizację komponentów Swing (EDT).
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

    private volatile Runnable uiRefreshCallback;
    private volatile Consumer<Boolean> connectionStatusCallback;
    private volatile Boolean lastConnectionState;

    /**
     * Konstruktor usługi harmonogramu odświeżania pozycji GPS na żywo.
     *
     * @param apiClient       klient HTTP do komunikacji z serwerami ZTM
     * @param vehicleCache    współbieżna pamięć podręczna pozycji pojazdów (ConcurrentHashMap)
     * @param delayCalculator serwis kalkulujący opóźnienia względem statycznego rozkładu
     */
    public RealtimeFetchScheduler(WtpRealtimeApiClient apiClient,
                                  RealtimeVehicleCache vehicleCache,
                                  DelayCalculatorService delayCalculator) {
        this.apiClient = apiClient;
        this.jsonParser = new WtpRealtimeJsonParser();
        this.vehicleCache = vehicleCache;
        this.delayCalculator = delayCalculator;
    }

    /**
     * Rejestruje akcję odświeżania widoku UI (np. repaint mapy), która zostanie
     * bezpiecznie wykonana na wątku Swing Event Dispatch Thread (EDT).
     */
    public void setUiRefreshCallback(Runnable uiRefreshCallback) {
        this.uiRefreshCallback = uiRefreshCallback;
    }

    /**
     * Rejestruje nasłuchiwanie zmiany stanu połączenia z siecią API ZTM.
     */
    public synchronized void setConnectionStatusCallback(Consumer<Boolean> connectionStatusCallback) {
        this.connectionStatusCallback = connectionStatusCallback;
        Boolean connectionState = lastConnectionState;
        if (connectionStatusCallback != null && connectionState != null) {
            SwingUtilities.invokeLater(() -> connectionStatusCallback.accept(connectionState));
        }
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
            thread.setDaemon(true); // Wątek typu Daemon nie blokuje zamknięcia aplikacji
            return thread;
        });

        logger.info("Uruchamianie pętli pobierania danych GPS co {} sekund w tle...", intervalSeconds);

        scheduledTask = scheduler.scheduleAtFixedRate(
                this::fetchAndProcessRealtimeData,
                0,
                intervalSeconds,
                TimeUnit.SECONDS
        );
    }

    /**
     * Zleca natychmiastowe, dodatkowe pobranie danych na wątku tła (np. po naciśnięciu F5).
     */
    public synchronized void refreshNow() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.execute(this::fetchAndProcessRealtimeData);
        }
    }

    /**
     * Zatrzymuje pętlę odpytywania API w tle i zwalnia zasoby wątku.
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

    /**
     * Sprawdza, czy zadanie cykliczne aktualnie pracuje.
     */
    public synchronized boolean isRunning() {
        return scheduledTask != null && !scheduledTask.isCancelled();
    }

    /**
     * Główna metoda wykonywana cyklicznie w osobnym wątku tła.
     */
    private void fetchAndProcessRealtimeData() {
        try {
            logger.debug("Rozpoczęcie cyklu pobierania danych GPS z API ZTM...");

            // 1. Pobranie surowych ramek JSON z API ZTM (type=1: Autobusy, type=2: Tramwaje)
            String busJson = apiClient.fetchBusPositions();
            String tramJson = apiClient.fetchTramPositions();

            // Sprawdzenie, czy serwer odpowiedział (pusty ciąg świadczy o braku połączenia lub walidacji)
            if (busJson.isBlank() && tramJson.isBlank()) {
                notifyConnectionState(false);
                logger.warn("Brak odpowiedzi z API ZTM lub brak połączenia internetowego. Pomięcie cyklu.");

                // Mimo braku nowej ramki wywołujemy wygasanie TTL dla starych pojazdów
                vehicleCache.cleanExpiredPositions();
                return;
            }

            // Połączenie powiodło się
            notifyConnectionState(true);

            // 2. Parsowanie odpowiedzi JSON do obiektów domenowych LiveVehiclePosition
            List<LiveVehiclePosition> busPositions = jsonParser.parseVehiclePositions(busJson);
            List<LiveVehiclePosition> tramPositions = jsonParser.parseVehiclePositions(tramJson);

            int updatedCount = 0;

            // 3. Obliczenie opóźnień i aktualizacja wpisów w ConcurrentHashMap (RealtimeVehicleCache)
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

            // 4. KROK 46: Wywołanie mechanizmu wygasania (TTL) nieaktywnych/przestarzałych pojazdów
            int evictedCount = vehicleCache.cleanExpiredPositions();

            logger.info("Zakończono cykl GPS. Zaktualizowano pojazdów: {} (Busem: {}, Tramwajem: {}). Usunięto przestarzałych (TTL): {}",
                    updatedCount, busPositions.size(), tramPositions.size(), evictedCount);

            // 5. Zlecenie odświeżenia interfejsu graficznego w bezpiecznym wątku Swing EDT
            if (uiRefreshCallback != null) {
                SwingUtilities.invokeLater(uiRefreshCallback);
            }

        } catch (Exception e) {
            notifyConnectionState(false);
            logger.error("Awarjna obsługa cyklu odświeżania pozycji GPS w tle: {}", e.getMessage(), e);
        }
    }

    /**
     * Powiadamia warstwę UI o zmianie stanu połączenia z serwerem API.
     */
    private synchronized void notifyConnectionState(boolean isConnected) {
        if (lastConnectionState == null || lastConnectionState != isConnected) {
            this.lastConnectionState = isConnected;
            if (!isConnected) {
                vehicleCache.clear();
                Runnable refresh = uiRefreshCallback;
                if (refresh != null) {
                    SwingUtilities.invokeLater(refresh);
                }
            }
            Consumer<Boolean> callback = connectionStatusCallback;
            if (callback != null) {
                SwingUtilities.invokeLater(() -> callback.accept(isConnected));
            }
        }
    }
}