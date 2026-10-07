package pl.j.reinmar.mapwaw.wtp.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import pl.j.reinmar.mapwaw.wtp.config.AppConfig;
import pl.j.reinmar.mapwaw.wtp.model.LiveVehiclePosition;
import pl.j.reinmar.mapwaw.wtp.parser.WtpRealtimeJsonParser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WtpRealtimeApiClientLiveTest {

    private static WtpRealtimeApiClient apiClient;
    private static WtpRealtimeJsonParser jsonParser;

    @BeforeAll
    static void setUp() {
        // Inicjalizacja klienta API oraz parsera JSON
        apiClient = new WtpRealtimeApiClient();
        jsonParser = new WtpRealtimeJsonParser();

        // Weryfikacja obecności klucza API w konfiguracji przed uruchomieniem testów integracyjnych
        String apiKey = AppConfig.getApiKey();
        assertNotNull(apiKey, "Klucz API nie może być wartością null");
        assertFalse(apiKey.isBlank(), "Klucz API w config.properties lub apiKey.txt nie może być pusty!");
    }

    @Test
    @DisplayName("Test integracyjny: Pobieranie pozycji na żywo dla autobusów (type=1)")
    void shouldFetchAndParseLiveBusPositions() {
        // Given & When: Pobranie surowego ciągu JSON dla autobusów z żywego API ZTM
        String busJson = apiClient.fetchBusPositions();

        // Then: Odpowiedź nie powinna być pusta i nie może zawierać komunikatu błędu klucza API
        assertNotNull(busJson, "Odpowiedź z API nie może być null");
        assertFalse(busJson.isBlank(), "Odpowiedź z API nie może być pusta");
        assertFalse(busJson.contains("\"result\":\"false\""), "API ZTM zwróciło błąd autoryzacji/klucza API");

        // Weryfikacja parsowania zwracanych ramek danych
        List<LiveVehiclePosition> busPositions = jsonParser.parseVehiclePositions(busJson);
        assertNotNull(busPositions, "Lista przetworzonych pozycji nie może być null");

        System.out.println("Test integracyjny (Autobusy): Pobrano " + busPositions.size() + " aktywnych pozycji.");

        if (!busPositions.isEmpty()) {
            LiveVehiclePosition sampleBus = busPositions.get(0);
            assertNotNull(sampleBus.getVehicleId(), "Numer taborowy pojazdu nie może być null");
            assertNotNull(sampleBus.getLineNumber(), "Numer linii nie może być null");
            assertTrue(sampleBus.getLatitude() > 50.0, "Szerokość geograficzna musi być poprawnym punktem");
            assertTrue(sampleBus.getLongitude() > 15.0, "Długość geograficzna musi być poprawnym punktem");
        }
    }

    @Test
    @DisplayName("Test integracyjny: Pobieranie pozycji na żywo dla tramwajów (type=2)")
    void shouldFetchAndParseLiveTramPositions() {
        // Given & When: Pobranie surowego ciągu JSON dla tramwajów z żywego API ZTM
        String tramJson = apiClient.fetchTramPositions();

        // Then
        assertNotNull(tramJson, "Odpowiedź z API nie może być null");
        assertFalse(tramJson.isBlank(), "Odpowiedź z API nie może być pusta");
        assertFalse(tramJson.contains("\"result\":\"false\""), "API ZTM zwróciło błąd autoryzacji/klucza API");

        List<LiveVehiclePosition> tramPositions = jsonParser.parseVehiclePositions(tramJson);
        assertNotNull(tramPositions, "Lista przetworzonych pozycji nie może być null");

        System.out.println("Test integracyjny (Tramwaje): Pobrano " + tramPositions.size() + " aktywnych pozycji.");
    }
}