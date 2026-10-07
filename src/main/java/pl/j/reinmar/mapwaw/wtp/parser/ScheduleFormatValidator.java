package pl.j.reinmar.mapwaw.wtp.parser;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zoptymalizowany walidator i fabryka deduplikacji ciągów tekstowych (String Pool)
 * w celu redukcji narzutu alokacji pamięci na stercie (GC pressure).
 */
public class ScheduleFormatValidator {

    private static final double MIN_LAT = 52.0;
    private static final double MAX_LAT = 52.5;
    private static final double MIN_LON = 20.6;
    private static final double MAX_LON = 21.6;

    // Kanoniczny bufor podręczny dla często powtarzających się wartości String (np. kody, numery linii)
    private static final Map<String, String> STRING_POOL = new ConcurrentHashMap<>(1024);

    /**
     * Zwraca kanoniczną instancję ciągu tekstowego z wewnętrznej puli,
     * redukując liczbę duplikatów w pamięci RAM.
     */
    public static String intern(String val) {
        if (val == null) {
            return "";
        }
        String trimmed = val.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return STRING_POOL.computeIfAbsent(trimmed, s -> s);
    }

    public static String sanitizeLine(String rawLine) {
        if (rawLine == null) {
            return "";
        }
        if (rawLine.indexOf('\uFEFF') != -1) {
            return rawLine.replace("\uFEFF", "").trim();
        }
        return rawLine.trim();
    }

    public static boolean isValidWarsawCoordinates(double latitude, double longitude) {
        return latitude >= MIN_LAT && latitude <= MAX_LAT && longitude >= MIN_LON && longitude <= MAX_LON;
    }

    public static double parseFastDouble(CharSequence cs, double defaultValue) {
        if (cs == null || cs.length() == 0) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(cs.toString().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static int parseFastInt(CharSequence cs, int defaultValue) {
        if (cs == null || cs.length() == 0) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(cs.toString().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}