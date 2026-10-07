package pl.j.reinmar.mapwaw.wtp.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.j.reinmar.mapwaw.wtp.model.Stop;
import pl.j.reinmar.mapwaw.wtp.repository.ScheduleRepository;

/**
 * Parser odpowiedzialny za przetwarzanie danych przystanków z pliku stops.txt (standard GTFS).
 * Zawiera walidację współrzędnych geograficznych odrzucającą przystanki spoza Warszawy.
 */
public class StopSectionParser {

    private static final Logger logger = LoggerFactory.getLogger(StopSectionParser.class);

    /**
     * Parsuje pojedynczą linię CSV z pliku stops.txt, waliduje współrzędne i rejestruje przystanek w repozytorium.
     *
     * @param line       linia tekstu z pliku stops.txt
     * @param repository docelowy magazyn danych w pamięci RAM
     * @return true jeśli pomyślnie sparsowano, zweryfikowano i dodano przystanek, false w przeciwnym razie
     */
    public boolean parseAndAddStop(String line, ScheduleRepository repository) {
        if (line == null || line.isBlank()) {
            return false;
        }

        String trimmed = ScheduleFormatValidator.sanitizeLine(line);
        if (trimmed.startsWith("stop_id") || trimmed.startsWith("\"stop_id\"")) {
            return false;
        }

        try {
            String[] parts = trimmed.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
            if (parts.length < 4) {
                return false;
            }

            String id = cleanValue(parts[0]);
            String name = cleanValue(parts[1]);
            double latitude = ScheduleFormatValidator.parseSafeDouble(parts[2], 0.0);
            double longitude = ScheduleFormatValidator.parseSafeDouble(parts[3], 0.0);

            // KROK 28: Walidacja danych po parsowaniu – odrzucanie przystanków spoza obszaru Warszawy
            if (!ScheduleFormatValidator.isValidWarsawCoordinates(latitude, longitude)) {
                logger.debug("Odrzucono przystanek '{}' (ID: {}) ze względu na współrzędne poza Warszawą: ({}, {})",
                        name, id, latitude, longitude);
                return false;
            }

            // Opcjonalne pola w standardzie GTFS (kod słupka, dostępność)
            String code = parts.length > 4 && !parts[4].isBlank() ? cleanValue(parts[4]) : "";
            boolean wheelchairAccessible = parts.length > 5 && "1".equals(cleanValue(parts[5]));

            Stop stop = new Stop(id, name, code, latitude, longitude, wheelchairAccessible);
            repository.addStop(stop);

            return true;
        } catch (Exception e) {
            logger.debug("Błąd parsowania i walidacji linii przystanku [{}]: {}", trimmed, e.getMessage());
            return false;
        }
    }

    private String cleanValue(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.trim();
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() >= 2) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned.trim();
    }
}