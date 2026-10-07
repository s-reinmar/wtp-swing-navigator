package pl.j.reinmar.mapwaw.wtp.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Klasa odpowiedzialna za walidację wierszy oraz czyszczenie danych tekstowych
 * z plików rozkładowych (obsługa znaków specjalnych, BOM, pustych linii i błędnych współrzędnych).
 */
public class ScheduleFormatValidator {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleFormatValidator.class);

    // Przybliżone granice geograficzne aglomeracji warszawskiej dla walidacji współrzędnych
    private static final double MIN_LAT = 52.0;
    private static final double MAX_LAT = 52.5;
    private static final double MIN_LON = 20.6;
    private static final double MAX_LON = 21.6;

    /**
     * Oczyszcza surową linię tekstową z niewidocznych znaków specjalnych i BOM.
     */
    public static String sanitizeLine(String rawLine) {
        if (rawLine == null) {
            return "";
        }
        // Usunięcie znacznika BOM (Byte Order Mark) oraz zbędnych białych znaków
        String cleaned = rawLine.replace("\uFEFF", "").trim();
        return cleaned;
    }

    /**
     * Sprawdza, czy linia jest pusta lub stanowi nagłówek.
     */
    public static boolean isEmptyOrComment(String line) {
        String sanitized = sanitizeLine(line);
        return sanitized.isEmpty() || sanitized.startsWith("#");
    }

    /**
     * Waliduje, czy współrzędne geograficzne mieszczą się w rozsądnym zakresie dla Warszawy.
     */
    public static boolean isValidWarsawCoordinates(double latitude, double longitude) {
        if (latitude == 0.0 || longitude == 0.0) {
            return false;
        }
        return latitude >= MIN_LAT && latitude <= MAX_LAT && longitude >= MIN_LON && longitude <= MAX_LON;
    }

    /**
     * Bezpiecznie konwertuje tekst na liczbę typu double, zwracając wartość domyślną w razie błędu.
     */
    public static double parseSafeDouble(String value, double defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            logger.debug("Nie udało się sparsować liczby zmiennoprzecinkowej ze składni: [{}]", value);
            return defaultValue;
        }
    }

    /**
     * Bezpiecznie konwertuje tekst na liczbę całkowitą.
     */
    public static int parseSafeInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            logger.debug("Nie udało się sparsować liczby całkowitej ze składni: [{}]", value);
            return defaultValue;
        }
    }
}