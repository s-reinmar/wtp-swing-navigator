package pl.j.reinmar.mapwaw.wtp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.j.reinmar.mapwaw.wtp.config.AppConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Klient HTTP oparty na natywnym java.net.http.HttpClient, odpowiedzialny za
 * komunikację z interfejsem REST API UM Warszawa (ZTM) w celu pobierania danych GPS.
 */
public class WtpRealtimeApiClient {

    private static final Logger logger = LoggerFactory.getLogger(WtpRealtimeApiClient.class);

    private final HttpClient httpClient;
    private final String apiEndpoint;
    private final String resourceId;

    /**
     * Domyślny konstruktor inicjalizujący natywny HttpClient z konfiguracją limitów czasowych.
     */
    public WtpRealtimeApiClient() {
        this.apiEndpoint = AppConfig.getApiEndpoint();
        this.resourceId = AppConfig.getResourceId();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Alternatywny konstruktor pozwalający na przekazanie własnej instancji HttpClient (np. do testów).
     */
    public WtpRealtimeApiClient(HttpClient httpClient, String apiEndpoint, String resourceId) {
        this.httpClient = httpClient;
        this.apiEndpoint = apiEndpoint;
        this.resourceId = resourceId;
    }

    /**
     * Pobiera surową odpowiedź JSON z pozycjami pojazdów wskazanego typu (synchronicznie).
     *
     * @param type 1 dla autobusów, 2 dla tramwajów
     * @return treść odpowiedzi JSON lub pusty ciąg w przypadku błędu
     */
    public String fetchRawVehicleData(int type) {
        return fetchRawVehicleData(type, null);
    }

    /**
     * Pobiera surową odpowiedź JSON z opcjonalnym filtrowaniem po numerze linii.
     *
     * @param type       1 dla autobusów, 2 dla tramwajów
     * @param lineNumber opcjonalny numer linii (np. "507"), może być null
     * @return treść odpowiedzi JSON
     */
    public String fetchRawVehicleData(int type, String lineNumber) {
        String url = buildApiUrl(type, lineNumber);
        logger.debug("Wysyłanie zapytania HTTP GET pod adres: {}", url);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(12))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            } else {
                logger.warn("Serwer API ZTM zwrócił niepoprawny kod statusu HTTP: {}", response.statusCode());
            }
        } catch (IOException e) {
            logger.error("Błąd bazy I/O podczas połączenia z API ZTM: {}", e.getMessage());
        } catch (InterruptedException e) {
            logger.error("Przerwano zapytanie HTTP do API ZTM: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }

        return "";
    }

    /**
     * Pobiera surową odpowiedź JSON w sposób asynchroniczny (non-blocking).
     *
     * @param type 1 dla autobusów, 2 dla tramwajów
     * @return CompletableFuture z treścią odpowiedzi JSON
     */
    public CompletableFuture<String> fetchRawVehicleDataAsync(int type) {
        String url = buildApiUrl(type, null);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return response.body();
                    }
                    logger.warn("Asynchroniczne zapytanie zwróciło status HTTP: {}", response.statusCode());
                    return "";
                })
                .exceptionally(ex -> {
                    logger.error("Błąd podczas asynchronicznego zapytania HTTP: {}", ex.getMessage());
                    return "";
                });
    }

    /**
     * Buduje pełny adres URL zapytania na podstawie parametrów konfiguracyjnych i klucza API.
     */
    private String buildApiUrl(int type, String lineNumber) {
        String apiKey = AppConfig.getApiKey();
        StringBuilder urlBuilder = new StringBuilder(apiEndpoint);
        urlBuilder.append("?apikey=").append(apiKey);
        urlBuilder.append("&resource_id=").append(resourceId);
        urlBuilder.append("&type=").append(type);

        if (lineNumber != null && !lineNumber.isBlank()) {
            urlBuilder.append("&line=").append(lineNumber.trim());
        }

        return urlBuilder.toString();
    }
}