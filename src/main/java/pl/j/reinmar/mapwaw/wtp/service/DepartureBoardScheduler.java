package pl.j.reinmar.mapwaw.wtp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.j.reinmar.mapwaw.wtp.ui.controller.MapController;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Usługa tła wykorzystująca ScheduledExecutorService do cyklicznego
 * odświeżania tablicy odjazdów w czasie rzeczywistym co 10 sekund.
 */
public class DepartureBoardScheduler {

    private static final Logger logger = LoggerFactory.getLogger(DepartureBoardScheduler.class);
    private static final int REFRESH_INTERVAL_SECONDS = 10;

    private final MapController mapController;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledTask;

    /**
     * Konstruktor tworzący harmonogram odświeżania tablicy odjazdów.
     *
     * @param mapController kontroler zarządzający widokiem i przeładowaniem tabeli odjazdów
     */
    public DepartureBoardScheduler(MapController mapController) {
        this.mapController = mapController;
    }

    /**
     * Uruchamia cykliczny wątek odświeżający tablicę odjazdów co 10 sekund.
     */
    public synchronized void start() {
        if (isRunning()) {
            logger.warn("DepartureBoardScheduler jest już uruchomiony.");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "WTP-DepartureBoardRefresh-Thread");
            thread.setDaemon(true); // Wątek typu Daemon nie blokuje zamknięcia aplikacji
            return thread;
        });

        logger.info("Uruchamianie cyklicznego wątku odświeżania tablicy odjazdów co {} sekund...", REFRESH_INTERVAL_SECONDS);

        scheduledTask = scheduler.scheduleAtFixedRate(
                this::refreshBoardTask,
                REFRESH_INTERVAL_SECONDS, // Pierwsze wykonanie po 10 sekundach
                REFRESH_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    /**
     * Zatrzymuje pętlę cyklicznego odświeżania w tle.
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
        logger.info("DepartureBoardScheduler został zatrzymany.");
    }

    /**
     * Sprawdza, czy zadanie odświeżania jest aktywne.
     */
    public synchronized boolean isRunning() {
        return scheduledTask != null && !scheduledTask.isCancelled();
    }

    /**
     * Główna metoda wykonywana cyklicznie przez wątek tła.
     */
    private void refreshBoardTask() {
        try {
            if (mapController != null && mapController.getCurrentlySelectedStop() != null) {
                logger.debug("Cykliczne odświeżanie tablicy odjazdów dla przystanku: {}",
                        mapController.getCurrentlySelectedStop().getName());
                mapController.refreshDepartureBoard();
            }
        } catch (Exception e) {
            logger.error("Błąd podczas cyklicznego odświeżania tablicy odjazdów: {}", e.getMessage(), e);
        }
    }
}