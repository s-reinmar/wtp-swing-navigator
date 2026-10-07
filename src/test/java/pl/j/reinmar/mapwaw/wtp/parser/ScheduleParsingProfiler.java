package pl.j.reinmar.mapwaw.wtp.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.j.reinmar.mapwaw.wtp.repository.ScheduleRepository;

import java.io.File;
import java.io.IOException;

/**
 * Aplikacja harness do profilowania wydajności procesu parsowania rozkładów
 * za pomocą zewnętrznych narzędzi profilujących (VisualVM / JProfiler).
 */
public class ScheduleParsingProfiler {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleParsingProfiler.class);

    public static void main(String[] args) throws IOException, InterruptedException {
        logger.info("==================================================");
        logger.info("   WTP NAVIGATOR - PROFILOWANIE PROCESU PARSOWANIA");
        logger.info("==================================================");

        // 1. Oczekiwanie na podpięcie profilera (VisualVM / JProfiler)
        logger.info("Podepnij VisualVM lub JProfiler pod proces PID: {}", ProcessHandle.current().pid());
        logger.info("Naciśnij ENTER w konsoli, aby rozpocząć profilowanie...");
        System.in.read();

        File dataDir = new File("src/main/resources/data");
        if (!dataDir.exists()) {
            logger.error("Katalog zasobów z danymi rozkładu nie istnieje: {}", dataDir.getAbsolutePath());
            return;
        }

        // 2. Rozpoczęcie serii testów parsowania pod kątem alokacji obiektów i zużycia CPU
        int iterations = 5;
        logger.info("Rozpoczynanie pętli testowej (liczba powtórzeń: {})...", iterations);

        for (int i = 1; i <= iterations; i++) {
            logger.info("--- Rozpoczęcie iteracji #{} ---", i);
            ScheduleRepository repository = new ScheduleRepository();
            WtpTxtScheduleParser parser = new WtpTxtScheduleParser();

            long startTime = System.currentTimeMillis();

            File[] files = dataDir.listFiles((dir, name) -> name.endsWith(".txt"));
            if (files != null) {
                for (File file : files) {
                    parser.parse(file, repository);
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Iteracja #{} zakończona w {} ms. Załadowano przystanków: {}, linii: {}",
                    i, duration, repository.getStopsCount(), repository.getLinesCount());

            // Krótka pauza na uspokojenie pamięci RAM / wymuszenie GC w profilerze
            Thread.sleep(1000);
        }

        logger.info("==================================================");
        logger.info("Profilowanie zakończone. Wygeneruj raport w VisualVM/JProfiler.");
        logger.info("==================================================");
    }
}
