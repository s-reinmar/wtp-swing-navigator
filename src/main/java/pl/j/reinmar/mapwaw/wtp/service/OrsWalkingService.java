package pl.j.reinmar.mapwaw.wtp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.j.reinmar.mapwaw.wtp.config.AppConfig;
import pl.j.reinmar.mapwaw.wtp.model.Stop;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pobiera z OpenRouteService (profil foot-walking) czasy dojścia pieszego między przystankami
 * i uzupełnia nimi trasy. Przy braku klucza lub błędzie API zachowane jest oszacowanie liniowe.
 */
public class OrsWalkingService {

    private static final Logger logger = LoggerFactory.getLogger(OrsWalkingService.class);

    private final HttpClient httpClient;
    private final String endpoint;
    private final String apiKey;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Optional<Duration>> cache = new ConcurrentHashMap<>();
    private volatile boolean failing;

    public OrsWalkingService() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                AppConfig.getOrsEndpoint(), AppConfig.getOrsApiKey());
    }

    public OrsWalkingService(HttpClient httpClient, String endpoint, String apiKey) {
        this.httpClient = httpClient;
        this.endpoint = endpoint;
        this.apiKey = apiKey == null ? "" : apiKey;
    }

    public boolean isEnabled() {
        return !apiKey.isBlank();
    }

    /** Zwraca trasę z czasami dojścia z ORS; w razie niepowodzenia zwraca trasę bez zmian. */
    public RoutingEngine.ScheduledRoute enrich(RoutingEngine.ScheduledRoute route) {
        if (!isEnabled() || route.legs().size() < 2) {
            return route;
        }
        List<Duration> walks = new ArrayList<>();
        boolean anyMeasured = false;
        for (int i = 0; i < route.legs().size() - 1; i++) {
            Stop from = route.legs().get(i).route().stops().getLast();
            Stop to = route.legs().get(i + 1).route().stops().getFirst();
            Duration walk = null;
            if (from.getId() != null && !from.getId().equals(to.getId())) {
                walk = walkingTime(from, to).orElse(null);
                anyMeasured |= walk != null;
            }
            walks.add(walk);
        }
        return anyMeasured ? route.withMeasuredTransferWalks(walks) : route;
    }

    public Optional<Duration> walkingTime(Stop from, Stop to) {
        String cacheKey = from.getId() + ">" + to.getId();
        Optional<Duration> cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        if (failing) {
            return Optional.empty();
        }
        Optional<Duration> result = fetch(from, to);
        if (result.isPresent()) {
            cache.put(cacheKey, result);
        }
        return result;
    }

    private Optional<Duration> fetch(Stop from, Stop to) {
        String url = endpoint + "?api_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
                + "&start=" + coords(from) + "&end=" + coords(to);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json, application/geo+json")
                    .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode seconds = mapper.readTree(response.body())
                        .path("features").path(0).path("properties").path("summary").path("duration");
                if (seconds.isNumber()) {
                    return Optional.of(Duration.ofSeconds((long) Math.ceil(seconds.asDouble())));
                }
                logger.warn("ORS: nieoczekiwana odpowiedź bez pola summary.duration");
            } else {
                logger.warn("ORS: kod HTTP {}", response.statusCode());
                // Błędy autoryzacji i limitów nie mają sensu przy kolejnych próbach w tej sesji
                failing = response.statusCode() == 401 || response.statusCode() == 403
                        || response.statusCode() == 429;
            }
        } catch (IOException e) {
            logger.warn("ORS: błąd połączenia: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IllegalArgumentException e) {
            logger.warn("ORS: nieprawidłowy adres zapytania: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private static String coords(Stop stop) {
        return String.format(Locale.ROOT, "%.6f,%.6f", stop.getLongitude(), stop.getLatitude());
    }
}
