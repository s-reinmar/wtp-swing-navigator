package pl.j.reinmar.mapwaw.wtp.parser;

import pl.j.reinmar.mapwaw.wtp.repository.ScheduleRepository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Interfejs definiujący kontrakt dla parsera statycznych plików rozkładów jazdy WTP (ZTM Warszawa).
 * Odpowiada za strumieniowe przetwarzanie plików tekstowych, walidację struktury oraz
 * wypełnianie magazynu danych w pamięci RAM (ScheduleRepository).
 */
public interface WtpTxtScheduleParser {

    /**
     * Parsuje plik tekstowy z rozkładem jazdy i ładuje sparsowane encje do magazynu.
     *
     * @param file       plik rozkładu jazdy (.txt)
     * @param repository docelowy magazyn danych w pamięci RAM
     * @throws IOException w przypadku błędów wejścia/wyjścia (np. brak pliku, błąd odczytu)
     */
    void parse(File file, ScheduleRepository repository) throws IOException;

    /**
     * Domyślna metoda pomocnicza obsługująca obiekt Path.
     *
     * @param path       ścieżka do pliku rozkładu
     * @param repository docelowy magazyn danych w pamięci RAM
     * @throws IOException w przypadku błędów wejścia/wyjścia
     */
    default void parse(Path path, ScheduleRepository repository) throws IOException {
        if (path != null) {
            parse(path.toFile(), repository);
        }
    }
}