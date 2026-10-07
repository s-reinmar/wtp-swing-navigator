package pl.j.reinmar.mapwaw.wtp.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.j.reinmar.mapwaw.wtp.repository.ScheduleRepository;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Implementacja parsera statycznych plików rozkładów jazdy WTP (ZTM Warszawa).
 * Wykorzystuje mechanizm strumieniowego czytania pliku linia po linii za pomocą BufferedReader,
 * co zapobiega błędom OutOfMemoryError przy przetwarzaniu dużych plików tekstowych.
 */
public class WtpTxtScheduleParser {

    private static final Logger logger = LoggerFactory.getLogger(WtpTxtScheduleParser.class);

    /**
     * Parsuje plik tekstowy z rozkładem jazdy w sposób strumieniowy.
     *
     * @param file       plik rozkładu jazdy (.txt)
     * @param repository docelowy magazyn danych w pamięci RAM
     * @throws IOException w przypadku błędów wejścia/wyjścia
     */
    public void parse(File file, ScheduleRepository repository) throws IOException {
        if (file == null || !file.exists()) {
            logger.warn("Plik rozkładu nie istnieje lub jest pusty: {}", file);
            return;
        }

        logger.info("Rozpoczęcie strumieniowego parsowania pliku: {}", file.getName());
        long startTime = System.currentTimeMillis();

        int lineCount = 0;
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String currentLine;
            while ((currentLine = reader.readLine()) != null) {
                lineCount++;
                parseLine(currentLine, repository);
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Zakończono parsowanie pliku {}. Przetworiono linii: {}, Czas: {} ms",
                file.getName(), lineCount, duration);
    }

    /**
     * Metoda pomocnicza obsługująca obiekt Path.
     *
     * @param path       ścieżka do pliku rozkładu
     * @param repository docelowy magazyn danych w pamięci RAM
     * @throws IOException w przypadku błędów wejścia/wyjścia
     */
    public void parse(Path path, ScheduleRepository repository) throws IOException {
        if (path != null) {
            parse(path.toFile(), repository);
        }
    }

    /**
     * Przetwarza pojedynczą linię pliku tekstowego (punkty rozszerzeń dla parsera stanowego).
     */
    private void parseLine(String line, ScheduleRepository repository) {
        if (line == null || line.isBlank()) {
            return;
        }

        String trimmed = line.trim();
        // Wykrywanie nagłówków sekcji (np. *ZE, *PR, *TR, *WG) przygotowane pod kolejne kroki
        if (trimmed.startsWith("*")) {
            logger.debug("Wykryto nagłówek sekcji w pliku rozkładu: {}", trimmed);
        }
    }
}