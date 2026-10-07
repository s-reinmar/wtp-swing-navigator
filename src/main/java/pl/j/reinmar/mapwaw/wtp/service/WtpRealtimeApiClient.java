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
 * Klient HTTP odpowiedzialny za komunikację z REST API UM Warszawa
 * z wbudowanym pomiarem i rejestrowaniem metryk czasu odpowiedzi w logach (SLF4J).
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
                .connectTimeout(Duration.ofSeconds(6))
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
     * Główna metoda wykonująca zapytanie HTTP z pomiarem czasu odpowiedzi i rejestrowaniem metryk w dzienniku zdarzeń.
     *
     * @param type       1 = Autobusy, 2 = Tramwaje
     * @param lineNumber opcjonalny numer linii
     * @return odpowiedź JSON z API UM
     */
    public String fetchRawVehicleData(int type, String lineNumber) {
        String url = buildApiUrl(type, lineNumber);
        String vehicleTypeLabel = (type == 1) ? "Autobusy" : "Tramwaje";

        logger.debug("Wysyłanie zapytania HTTP GET [{}] pod adres: {}", vehicleTypeLabel, url);

        // Pomiar czasu rozpoczęcia zapytania HTTP
        long startTimeMs = System.currentTimeMillis();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // KROK 47: Wyliczenie czasów odpowiedzi w milisekundach
            long responseTimeMs = System.currentTimeMillis() - startTimeMs;
            String responseBody = response.body();
            int bodyLengthBytes = (responseBody != null) ? responseBody.getBytes().length : 0;

            if (response.statusCode() == 200) {
                if (responseBody != null && responseBody.contains("\"result\":\"false\"")) {
                    logger.warn("METRYKA API: Serwer ZTM zwrócił błąd autoryzacji/klucza | Typ: {} | Czas odpowiedzi: {} ms",
                            vehicleTypeLabel, responseTimeMs);
                    return "";
                }

                // Logowanie metryki czasowej dla udanego zapytania
                logger.info("METRYKA API: Pomyślnie pobrano dane [{}] | Czas odpowiedzi: {} ms | Kod HTTP: {} | Rozmiar: {} B",
                        vehicleTypeLabel, responseTimeMs, response.statusCode(), bodyLengthBytes);

                return responseBody;
            } else {
                logger.warn("METRYKA API: Błąd odpowiedzi ZTM [{}] | Kod HTTP: {} | Czas odpowiedzi: {} ms",
                        vehicleTypeLabel, response.statusCode(), responseTimeMs);
            }

        } catch (UnknownHostException | ConnectException e) {
            long responseTimeMs = System.currentTimeMillis() - startTimeMs;
            logger.error("METRYKA API: Brak połączenia internetowego/DNS [{}] | Czas do błędu: {} ms | Komunikat: {}",
                    vehicleTypeLabel, responseTimeMs, e.getMessage());
        } catch (HttpTimeoutException e) {
            long responseTimeMs = System.currentTimeMillis() - startTimeMs;
            logger.warn("METRYKA API: Przekroczono limit czasu odpowiedzi (Timeout) [{}] | Czas: {} ms",
                    vehicleTypeLabel, responseTimeMs);
        } catch (IOException e) {
            long responseTimeMs = System.currentTimeMillis() - startTimeMs;
            logger.error("METRYKA API: Błąd I/O podczas połączenia [{}] | Czas: {} ms | Komunikat: {}",
                    vehicleTypeLabel, responseTimeMs, e.getMessage());
        } catch (InterruptedException e) {
            long responseTimeMs = System.currentTimeMillis() - startTimeMs;
            logger.warn("METRYKA API: Przerwano zapytanie HTTP [{}] | Czas do przerwania: {} ms",
                    vehicleTypeLabel, responseTimeMs);
            Thread.currentThread().interrupt();
        }

        return "";
    }

    /**
     * Asynchroniczne pobieranie danych GPS z metrykami czasowymi.
     */
    public CompletableFuture<String> fetchRawVehicleDataAsync(int type) {
        String url = buildApiUrl(type, null);
        String vehicleTypeLabel = (type == 1) ? "Autobusy" : "Tramwaje";
        long startTimeMs = System.currentTimeMillis();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    long responseTimeMs = System.currentTimeMillis() - startTimeMs;
                    if (response.statusCode() == 200) {
                        logger.info("METRYKA API (Async): Pobrano dane [{}] | Czas odpowiedzi: {} ms",
                                vehicleTypeLabel, responseTimeMs);
                        return response.body();
                    }
                    logger.warn("METRYKA API (Async): Błąd statusu HTTP [{}] | Kod: {} | Czas: {} ms",
                            vehicleTypeLabel, response.statusCode(), responseTimeMs);
                    return "";
                })
                .exceptionally(ex -> {
                    long responseTimeMs = System.currentTimeMillis() - startTimeMs;
                    logger.error("METRYKA API (Async): Wyjątek połączenia [{}] | Czas: {} ms | Komunikat: {}",
                            vehicleTypeLabel, responseTimeMs, ex.getMessage());
                    return "";
                });
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