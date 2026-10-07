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
import java.util.function.Consumer;

/**
 * Parser statycznych plików rozkładów jazdy WTP (ZTM Warszawa) ze wsparciem
 * dla strumieniowego odczytu oraz raportowania postępu procentowego.
 */
public class WtpTxtScheduleParser {

    private static final Logger logger = LoggerFactory.getLogger(WtpTxtScheduleParser.class);

    /**
     * Parsuje plik tekstowy z rozkładem jazdy i raportuje postęp przez callback.
     *
     * @param file             plik rozkładu jazdy (.txt)
     * @param repository       docelowy magazyn danych w pamięci RAM
     * @param progressCallback funkcja zwrotna przyjmująca wartość postępu (0-100)
     * @throws IOException w przypadku błędów wejścia/wyjścia
     */
    public void parse(File file, ScheduleRepository repository, Consumer<Integer> progressCallback) throws IOException {
        if (file == null || !file.exists()) {
            logger.warn("Plik rozkładu nie istnieje lub jest pusty: {}", file);
            return;
        }

        logger.info("Rozpoczęcie strumieniowego parsowania pliku z raportowaniem postępu: {}", file.getName());
        long startTime = System.currentTimeMillis();
        long totalBytes = file.length();
        long bytesRead = 0;

        int lineCount = 0;
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String currentLine;
            while ((currentLine = reader.readLine()) != null) {
                lineCount++;
                parseLine(currentLine, repository);

                // Szacowanie postępu na podstawie liczby przeczytanych znaków/bajtów linii
                if (totalBytes > 0) {
                    bytesRead += currentLine.getBytes(StandardCharsets.UTF_8).length + 1; // +1 za znak nowej linii
                    int progress = (int) Math.min(100, (bytesRead * 100) / totalBytes);
                    if (progressCallback != null) {
                        progressCallback.accept(progress);
                    }
                }
            }
        }

        if (progressCallback != null) {
            progressCallback.accept(100);
        }

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Zakończono parsowanie pliku {}. Przetworiono linii: {}, Czas: {} ms",
                file.getName(), lineCount, duration);
    }

    public void parse(File file, ScheduleRepository repository) throws IOException {
        parse(file, repository, null);
    }

    public void parse(Path path, ScheduleRepository repository, Consumer<Integer> progressCallback) throws IOException {
        if (path != null) {
            parse(path.toFile(), repository, progressCallback);
        }
    }

    private void parseLine(String line, ScheduleRepository repository) {
        if (line == null || line.isBlank()) {
            return;
        }

        String trimmed = ScheduleFormatValidator.sanitizeLine(line);
        if (trimmed.startsWith("*")) {
            logger.debug("Wykryto nagłówek sekcji w pliku rozkładu: {}", trimmed);
        }
    }
}