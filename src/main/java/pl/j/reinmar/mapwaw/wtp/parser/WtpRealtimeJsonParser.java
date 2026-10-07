package pl.j.reinmar.mapwaw.wtp.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.j.reinmar.mapwaw.wtp.model.LiveVehiclePosition;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser odpowiedzialny za dekodowanie i przetwarzanie odpowiedzi JSON
 * pochodzących z produkcyjnej bramki API UM Warszawa (busestrams_get).
 */
public class WtpRealtimeJsonParser {

    private static final Logger logger = LoggerFactory.getLogger(WtpRealtimeJsonParser.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;

    public WtpRealtimeJsonParser() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Parsuje surowy ciąg JSON zwrócony przez API ZTM i konwertuje poprawne ramki
     * do listy obiektów domenowych LiveVehiclePosition.
     *
     * @param rawJson surowy ciąg znaków w formacie JSON
     * @return lista przetworzonych i wstępnie zwalidowanych pozycji pojazdów
     */
    public List<LiveVehiclePosition> parseVehiclePositions(String rawJson) {
        List<LiveVehiclePosition> positions = new ArrayList<>();

        if (rawJson == null || rawJson.isBlank()) {
            return positions;
        }

        try {
            JsonNode rootNode = objectMapper.readTree(rawJson);

            // Sprawdzenie, czy struktura zawiera klucz "result"
            if (!rootNode.has("result")) {
                logger.warn("Odpowiedź JSON nie zawiera oczekiwanego węzła 'result'");
                return positions;
            }

            JsonNode resultNode = rootNode.get("result");

            // Obsługa sytuacji, gdy "result" zwraca komunikat błędu (String) zamiast tablicy obiektów
            if (resultNode.isTextual()) {
                logger.warn("API ZTM zwróciło komunikat błędu zamiast danych: {}", resultNode.asText());
                return positions;
            }

            if (resultNode.isArray()) {
                for (JsonNode itemNode : resultNode) {
                    LiveVehiclePosition position = parseVehicleNode(itemNode);
                    // KROK 40: Filtracja uszkodzonych i nieaktualnych ramek GPS
                    if (position != null && GpsFrameValidator.isValidFrame(position)) {
                        positions.add(position);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Błąd podczas parsowania drzewa JSON z API ZTM: {}", e.getMessage());
        }

        return positions;
    }

    /**
     * Parsuje pojedynczy węzeł JSON reprezentujący pozycję pojazdu.
     */
    private LiveVehiclePosition parseVehicleNode(JsonNode node) {
        try {
            // Wyciąganie poszczególnych pól z odpowiedzi API ZTM
            String lineNumber = getSafeText(node, "Lines");
            String vehicleId = getSafeText(node, "VehicleNumber");
            String brigade = getSafeText(node, "Brigade");
            String timeStr = getSafeText(node, "Time");

            double latitude = getSafeDouble(node, "Lat");
            double longitude = getSafeDouble(node, "Lon");

            if (lineNumber.isBlank() || vehicleId.isBlank() || latitude == 0.0 || longitude == 0.0) {
                return null;
            }

            // Parsowanie daty i czasu pomiaru GPS
            Instant lastUpdate = parseTimestamp(timeStr);

            // Początkowy kąt kierunku (bearing = 0.0f) oraz opóźnienie (0 sec) - wyliczane w kolejnych krokach
            return new LiveVehiclePosition(vehicleId, lineNumber, brigade, latitude, longitude, 0.0f, lastUpdate, 0);

        } catch (Exception e) {
            logger.debug("Pominięto uszkodzony węzeł pojazdu JSON: {}", e.getMessage());
            return null;
        }
    }

    private String getSafeText(JsonNode node, String fieldName) {
        return node.has(fieldName) && !node.get(fieldName).isNull() ? node.get(fieldName).asText().trim() : "";
    }

    private double getSafeDouble(JsonNode node, String fieldName) {
        if (!node.has(fieldName) || node.get(fieldName).isNull()) {
            return 0.0;
        }
        try {
            return node.get(fieldName).asDouble(0.0);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private Instant parseTimestamp(String timeStr) {
        if (timeStr.isBlank()) {
            return Instant.now();
        }
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(timeStr, DATE_TIME_FORMATTER);
            return localDateTime.atZone(ZoneId.of("Europe/Warsaw")).toInstant();
        } catch (DateTimeParseException e) {
            return Instant.now();
        }
    }
}