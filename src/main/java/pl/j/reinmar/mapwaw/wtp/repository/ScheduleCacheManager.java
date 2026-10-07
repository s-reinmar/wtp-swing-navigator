package pl.j.reinmar.mapwaw.wtp.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Menedżer pamięci podręcznej (Cache) odpowiedzialny za serializację i deserializację
 * sparsowanego magazynu rozkładów jazdy (ScheduleRepository) do pliku binarnego na dysku,
 * co umożliwia opcjonalny, błyskawiczny start aplikacji.
 */
public class ScheduleCacheManager {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleCacheManager.class);
    private static final String DEFAULT_CACHE_PATH = "wtp_schedule_cache.bin";

    /**
     * Zapisuje stan repozytorium do pliku cache.
     *
     * @           magazyn danych do zapisu
     * @return       true jeśli zapis się powiódł, false w przeciwnym razie
     */
    public static boolean saveCache(ScheduleRepository repository) {
        return saveCache(repository, Path.of(DEFAULT_CACHE_PATH));
    }

    public static boolean saveCache(ScheduleRepository repository, Path cachePath) {
        if (repository == null) {
            return false;
        }

        logger.info("Zapisywanie stanu rozkładów do pliku cache: {}", cachePath);
        try (ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(cachePath)))) {
            oos.writeObject(repository);
            logger.info("Pomyślnie utworzono plik cache rozkładów.");
            return true;
        } catch (IOException e) {
            logger.warn("Nie udało się zapisać pliku cache rozkładów: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Wczytuje stan repozytorium z pliku cache, jeśli ten istnieje.
     *
     * @param cachePath ścieżka do pliku cache
     * @return załadowany magazyn ScheduleRepository lub null w przypadku braku/błędu cache
     */
    public static ScheduleRepository loadCache(Path cachePath) {
        if (cachePath == null || !Files.exists(cachePath)) {
            logger.info("Brak pliku cache rozkładów pod ścieżką: {}", cachePath);
            return null;
        }

        logger.info("Wczytywanie rozkładów bezpośrednio z pliku cache: {}", cachePath);
        try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(cachePath)))) {
            Object obj = ois.readObject();
            if (obj instanceof ScheduleRepository repository) {
                logger.info("Pomyślnie wczytano dane z cache. Przystanków: {}, Linii: {}",
                        repository.getStopsCount(), repository.getLinesCount());
                return repository;
            }
        } catch (IOException | ClassNotFoundException e) {
            logger.warn("Plik cache jest uszkodzony lub nieaktualny, wymagane ponowne parsowanie: {}", e.getMessage());
        }
        return null;
    }

    public static ScheduleRepository loadCache() {
        return loadCache(Path.of(DEFAULT_CACHE_PATH));
    }
}