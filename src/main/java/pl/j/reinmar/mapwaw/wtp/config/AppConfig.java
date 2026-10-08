package pl.j.reinmar.mapwaw.wtp.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Klasa konfiguracyjna odpowiedzialna za wczytywanie parametrów
 * z pliku config.properties oraz obsługę klucza API ZTM.
 */
public class AppConfig {

    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);
    private static final Properties properties = new Properties();
    private static final String CONFIG_FILE_NAME = "config.properties";

    static {
        loadConfig();
    }

    /**
     * Wczytuje plik konfiguracyjny z zasobów (classpath).
     */
    private static void loadConfig() {
        try (InputStream input = AppConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE_NAME)) {
            if (input != null) {
                properties.load(input);
                logger.info("Pomyślnie wczytano plik konfiguracyjny: {}", CONFIG_FILE_NAME);
            } else {
                logger.warn("Nie znaleziono pliku {} w katalogu resources.", CONFIG_FILE_NAME);
            }
        } catch (IOException e) {
            logger.error("Błąd podczas wczytywania pliku konfiguracyjnego {}: {}", CONFIG_FILE_NAME, e.getMessage());
        }
    }

    /**
     * Zwraca wartość parametru dla podanego klucza.
     *
     * @klucz właściwości w pliku properties
     * @return wartość lub null, jeśli klucz nie istnieje
     */
    public static String getProperty(String key) {
        return properties.getProperty(key);
    }

    /**
     * Zwraca wartość parametru dla podanego klucza lub wartość domyślną.
     */
    public static String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    /**
     * Pobiera klucz API ZTM.
     * Sprawdza najpierw plik konfiguracyjny, a w razie jego braku próbnie odczytuje plik apiKey.txt.
     */
    public static String getApiKey() {
        String apiKey = getProperty("wtp.api.key");
        if (apiKey == null || apiKey.isBlank()) {
            try {
                Path keyPath = Path.of("apiKey.txt");
                if (Files.exists(keyPath)) {
                    apiKey = Files.readString(keyPath).trim();
                }
            } catch (IOException e) {
                logger.debug("Nie odczytano klucza z pliku apiKey.txt: {}", e.getMessage());
            }
        }
        return apiKey != null ? apiKey : "";
    }

    /**
     * Zwraca klucz OpenRouteService (config.properties lub zmienna ORS_API_KEY); pusty, gdy nie ustawiono.
     */
    public static String getOrsApiKey() {
        String key = getProperty("ors.api.key");
        if (key == null || key.isBlank()) {
            key = System.getenv("ORS_API_KEY");
        }
        return key == null ? "" : key.trim();
    }

    public static String getOrsEndpoint() {
        return getProperty("ors.api.endpoint",
                "https://api.openrouteservice.org/v2/directions/foot-walking");
    }

    /**
     * Zwraca endpoint API ZTM.
     */
    public static String getApiEndpoint() {
        return getProperty("wtp.api.endpoint", "https://api.um.warszawa.pl/api/action/busestrams_get/");
    }

    /**
     * Zwraca identyfikator zasobu (resource_id) dla pojazdów.
     */
    public static String getResourceId() {
        return getProperty("wtp.resource.id", "f237384b-0105-44ee-9a2e-cad0334866b5");
    }
}