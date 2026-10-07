package pl.j.reinmar.mapwaw.wtp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.j.reinmar.mapwaw.wtp.config.AppConfig;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Zoptymalizowany klient HTTP odporny na błędy braku połączenia internetowego
 * oraz przeciążenia/przerwy w działaniu serwerów API UM Warszawa.
 */
public class WtpRealtimeApiClient {

    private static final Logger logger = LoggerFactory.getLogger(WtpRealtimeApiClient.class);

    private final HttpClient httpClient;
    private final String apiEndpoint;
    private final String resourceId;

    public WtpRealtimeApiClient() {
        this.apiEndpoint = AppConfig.getApiEndpoint();
        this.resourceId = AppConfig.getResourceId();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6)) // Krótki timeout połączenia
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public WtpRealtimeApiClient(HttpClient httpClient, String apiEndpoint, String resourceId) {
        this.httpClient = httpClient;
        this.apiEndpoint = apiEndpoint;
        this.resourceId = resourceId;
    }

    public String fetchBusPositions() {
        return fetchBusPositions(null);
    }

    public String fetchBusPositions(String lineNumber) {
        return fetchRawVehicleData(1, lineNumber);
    }

    public String fetchTramPositions() {
        return fetchTramPositions(null);
    }

    public String fetchTramPositions(String lineNumber) {
        return fetchRawVehicleData(2, lineNumber);
    }

    /**
     * Główna metoda wykonująca zapytanie HTTP z kompleksową obsługą błędów sieci.
     */
    public String fetchRawVehicleData(int type, String lineNumber) {
        String url = buildApiUrl(type, lineNumber);
        logger.debug("Wysyłanie zapytania HTTP GET pod adres: {}", url);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8)) // Max czas oczekiwania na odpowiedź
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String body = response.body();
                // Sprawdzenie, czy serwer UM nie zwrócił błędu w treści JSON
                if (body != null && body.contains("\"result\":\"false\"")) {
                    logger.warn("Serwer API UM zwrócił komunikat o błędnym zapytaniu lub braku klucza API.");
                    return "";
                }
                return body;
            } else {
                logger.warn("Serwer API UM odpowiedział kodem błędu HTTP: {}", response.statusCode());
            }

        } catch (UnknownHostException | ConnectException e) {
            // KROK 45: Brak połączenia z Internetem lub problem z DNS
            logger.error("Brak połączenia z Internetem lub serwerem UM Warszawa: {}", e.getMessage());
        } catch (HttpTimeoutException e) {
            // KROK 45: Przekroczono limit czasu odpowiedzi serwera ZTM
            logger.warn("Przekroczono czas oczekiwania na odpowiedź serwera API UM (Timeout 8s).");
        } catch (IOException e) {
            logger.error("Błąd wejścia/wyjścia podczas połączenia z API ZTM: {}", e.getMessage());
        } catch (InterruptedException e) {
            logger.warn("Przerwano połączenie HTTP z API ZTM: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }

        return ""; // Zwraca pusty ciąg, informując serwis nadrzędny o niepowodzeniu
    }

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