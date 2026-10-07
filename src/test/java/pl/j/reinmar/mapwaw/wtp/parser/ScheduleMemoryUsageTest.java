package pl.j.reinmar.mapwaw.wtp.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.j.reinmar.mapwaw.wtp.repository.ScheduleRepository;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleMemoryUsageTest {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleMemoryUsageTest.class);

    @Test
    @DisplayName("Weryfikacja zużycia pamięci RAM podczas parsowania rozkładu")
    void verifyMemoryUsageAfterParsing() throws IOException {
        Runtime runtime = Runtime.getRuntime();

        // 1. Czyszczenie pamięci przed pomiarem
        runGarbageCollector();
        long memoryBeforeBytes = runtime.totalMemory() - runtime.freeMemory();

        ScheduleRepository repository = new ScheduleRepository();
        WtpTxtScheduleParser parser = new WtpTxtScheduleParser();

        File sampleFile = new File("src/main/resources/data/stops.txt");
        if (!sampleFile.exists()) {
            logger.warn("Brak pliku do testu pamięciowego: {}. Pomijam pomiar.", sampleFile.getAbsolutePath());
            return;
        }

        // 2. Parsowanie pliku
        parser.parse(sampleFile, repository);

        // 3. Czyszczenie pamięci po alokacji tymczasowych obiektów
        runGarbageCollector();
        long memoryAfterBytes = runtime.totalMemory() - runtime.freeMemory();

        long usedMemoryBytes = memoryAfterBytes - memoryBeforeBytes;
        double usedMemoryMB = usedMemoryBytes / (1024.0 * 1024.0);

        logger.info("=== RAPORT ZUŻYCIA PAMIĘCI RAM ===");
        logger.info("Załadowanych przystanków: {}", repository.getStopsCount());
        logger.info("Szacowane zużycie pamięci RAM: {} MB", String.format("%.2f", usedMemoryMB));

        // Prosta asercja – parsowanie małych/średnich zbiorów w pamięci nie powinno przekraczać 256 MB
        assertTrue(usedMemoryMB < 256.0, "Zużycie pamięci RAM przekroczyło dopuszczalny limit 256 MB!");
    }

    private void runGarbageCollector() {
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}