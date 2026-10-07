package pl.j.reinmar.mapwaw.wtp.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.j.reinmar.mapwaw.wtp.model.DayType;
import pl.j.reinmar.mapwaw.wtp.model.Departure;
import pl.j.reinmar.mapwaw.wtp.model.Line;
import pl.j.reinmar.mapwaw.wtp.model.Stop;
import pl.j.reinmar.mapwaw.wtp.repository.ScheduleRepository;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Parser odpowiedzialny za przetwarzanie tabeli odjazdów, konwersję godzin
 * oraz przypisywanie odjazdów do odpowiednich typów dni (WEEKDAY, SATURDAY, SUNDAY).
 */
public class DepartureScheduleSectionParser {

    private static final Logger logger = LoggerFactory.getLogger(DepartureScheduleSectionParser.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm:ss");

    /**
     * Parsuje wpis odjazdu z uwzględnieniem kalendarza i typu dnia.
     *
     * @param tripId        identyfikator kursu
     * @param rawTime       surowy czas odjazdu (np. "06:03:00" lub "25:15:00" w kursach nocnych)
     * @param stopId        identyfikator przystanku
     * @param serviceId     identyfikator usługi/kalendarza z calendar.txt
     * @param repository    magazyn danych w pamięci RAM
     * @param calendarMap   mapa powiązująca service_id z DayType
     * @param lineMap       mapa powiązująca tripId z obiektem Line
     */
    public void parseAndAddDeparture(String tripId, String rawTime, String stopId, String serviceId,
                                     ScheduleRepository repository,
                                     Map<String, DayType> calendarMap,
                                     Map<String, Line> lineMap) {
        if (rawTime == null || rawTime.isBlank()) {
            return;
        }

        try {
            LocalTime departureTime = parseFlexibleTime(rawTime);
            if (departureTime == null) {
                return;
            }

            Stop stop = repository.findStopById(stopId);
            Line line = lineMap.get(tripId);
            DayType dayType = calendarMap.getOrDefault(serviceId, DayType.WEEKDAY);

            if (stop != null && line != null) {
                // Domyślny numer brygady (może być uzupełniony z dodatkowych metadanych)
                String brigade = "01";

                Departure departure = new Departure(line, stop, departureTime, dayType, brigade);
                repository.addDeparture(departure);
            }
        } catch (Exception e) {
            logger.debug("Błąd parsowania odjazdu [czas: {}, stop: {}]: {}", rawTime, stopId, e.getMessage());
        }
    }

    /**
     * Parsuje czas uwzględniając formaty przekraczające 24:00 (częste w rozkładach nocnych ZTM).
     */
    private LocalTime parseFlexibleTime(String rawTime) {
        try {
            String trimmed = rawTime.trim();
            String[] parts = trimmed.split(":");
            if (parts.length == 3) {
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                int seconds = Integer.parseInt(parts[2]);

                // Obsługa godzin >= 24 (np. 25:15:00 -> 01:15:00)
                if (hours >= 24) {
                    hours = hours % 24;
                }
                return LocalTime.of(hours, minutes, seconds);
            }
            return LocalTime.parse(trimmed, TIME_FORMATTER);
        } catch (NumberFormatException | DateTimeParseException e) {
            return null;
        }
    }
}