package pl.j.reinmar.mapwaw.wtp.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.j.reinmar.mapwaw.wtp.model.Stop;
import pl.j.reinmar.mapwaw.wtp.repository.ScheduleRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Zoptymalizowany pod kątem pamięciowym parser sekcji przystanków (stops.txt).
 * Wykorzystuje szybki rozbiór pętlowy bez alokacji wyrażeń regularnych Pattern/String.split.
 */
public class StopSectionParser {

    private static final Logger logger = LoggerFactory.getLogger(StopSectionParser.class);

    public boolean parseAndAddStop(String line, ScheduleRepository repository) {
        if (line == null || line.isBlank()) {
            return false;
        }

        String sanitized = ScheduleFormatValidator.sanitizeLine(line);
        if (sanitized.startsWith("stop_id") || sanitized.startsWith("\"stop_id\"")) {
            return false;
        }

        List<String> tokens = fastCsvSplit(sanitized);
        if (tokens.size() < 4) {
            return false;
        }

        try {
            String id = ScheduleFormatValidator.intern(cleanQuotes(tokens.get(0)));
            String name = ScheduleFormatValidator.intern(cleanQuotes(tokens.get(1)));
            double latitude = ScheduleFormatValidator.parseFastDouble(cleanQuotes(tokens.get(2)), 0.0);
            double longitude = ScheduleFormatValidator.parseFastDouble(cleanQuotes(tokens.get(3)), 0.0);

            if (!ScheduleFormatValidator.isValidWarsawCoordinates(latitude, longitude)) {
                return false;
            }

            String code = tokens.size() > 4 ? ScheduleFormatValidator.intern(cleanQuotes(tokens.get(4))) : "";
            boolean wheelchair = tokens.size() > 5 && "1".equals(cleanQuotes(tokens.get(5)));

            Stop stop = new Stop(id, name, code, latitude, longitude, wheelchair);
            repository.addStop(stop);
            return true;
        } catch (Exception e) {
            logger.debug("Błąd podczas zoptymalizowanego parsowania przystanku: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Szybka, bezalokacyjna metoda podziału wiersza CSV na tokeny.
     */
    private List<String> fastCsvSplit(String line) {
        List<String> result = new ArrayList<>(8);
        int len = line.length();
        int start = 0;
        boolean inQuotes = false;

        for (int i = 0; i < len; i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(line.substring(start, i));
                start = i + 1;
            }
        }
        result.add(line.substring(start));
        return result;
    }

    private String cleanQuotes(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.length() >= 2 && trimmed.charAt(0) == '"' && trimmed.charAt(trimmed.length() - 1) == '"') {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }
}