package pl.j.reinmar.mapwaw.wtp.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.j.reinmar.mapwaw.wtp.model.Line;
import pl.j.reinmar.mapwaw.wtp.model.TransportType;
import pl.j.reinmar.mapwaw.wtp.repository.ScheduleRepository;

/**
 * Parser odpowiedzialny za przetwarzanie danych linii transportowych z pliku routes.txt (standard GTFS).
 * Wyznacza numery linii, operatorów oraz klasyfikuje typy transportu (BUS, TRAM, METRO).
 */
public class LineScheduleSectionParser {

    private static final Logger logger = LoggerFactory.getLogger(LineScheduleSectionParser.class);

    /**
     * Parsuje pojedynczą linię CSV z pliku routes.txt i rejestruje linię w repozytorium.
     *
     * @param line       linia tekstu z pliku routes.txt
     * @param repository docelowy magazyn danych w pamięci RAM
     * @return true jeśli pomyślnie sparsowano i dodano linię, false w przeciwnym razie
     */
    public boolean parseAndAddLine(String line, ScheduleRepository repository) {
        if (line == null || line.isBlank()) {
            return false;
        }

        // Pomijamy nagłówek pliku GTFS routes.txt
        String trimmed = line.trim();
        if (trimmed.startsWith("route_id") || trimmed.startsWith("\"route_id\"")) {
            return false;
        }

        try {
            // Rozdzielenie kolumn CSV z uwzględnieniem ewentualnych cudzysłowów
            String[] parts = trimmed.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
            if (parts.length < 5) {
                return false;
            }

            // Standard GTFS routes.txt: route_id, agency_id, route_short_name, route_long_name, route_type
            String routeId = cleanValue(parts[0]);
            String agencyId = cleanValue(parts[1]);
            String lineNumber = cleanValue(parts[2]);
            String routeTypeStr = cleanValue(parts[4]);

            // Rozpoznanie typu transportu oraz przewoźnika
            TransportType transportType = resolveTransportType(lineNumber, routeTypeStr);
            String operator = resolveOperator(agencyId);

            // Tworzenie encji linii i dodanie do magazynu in-memory
            Line transitLine = new Line(lineNumber, transportType, operator);
            repository.addLine(routeId, transitLine);

            return true;
        } catch (Exception e) {
            logger.debug("Błąd parsowania linii transportowej [{}]: {}", trimmed, e.getMessage());
            return false;
        }
    }

    /**
     * Określa typ transportu na podstawie numeru linii oraz kodu GTFS route_type.
     */
    private TransportType resolveTransportType(String lineNumber, String routeTypeStr) {
        if (lineNumber != null && (lineNumber.toUpperCase().startsWith("M1") || lineNumber.toUpperCase().startsWith("M2"))) {
            return TransportType.METRO;
        }
        try {
            int typeCode = Integer.parseInt(routeTypeStr);
            switch (typeCode) {
                case 0:
                    return TransportType.TRAM;
                case 1:
                    return TransportType.METRO;
                case 3:
                default:
                    return TransportType.BUS;
            }
        } catch (NumberFormatException e) {
            return TransportType.BUS;
        }
    }

    /**
     * Określa operatora na podstawie agency_id w GTFS.
     */
    private String resolveOperator(String agencyId) {
        if ("2".equals(agencyId)) {
            return "ZTM Warszawa (MZA / TW)";
        }
        return "WTP Warszawa";
    }

    /**
     * Pomocnicza metoda usuwająca zbędne cudzysłowy wokół wartości CSV.
     */
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