package pl.j.reinmar.mapwaw.wtp.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.j.reinmar.mapwaw.wtp.model.LiveVehiclePosition;

import java.time.Duration;
import java.time.Instant;

/**
 * Klasa odpowiedzialna za walidację jakości i świeżości ramek danych GPS z API ZTM.
 * Odrzuca uszkodzone punkty (0,0), koordynaty poza Warszawą oraz przestarzałe znaczniki czasu.
 */
public class GpsFrameValidator {

    private static final Logger logger = LoggerFactory.getLogger(GpsFrameValidator.class);

    // Granice geograficzne aglomeracji warszawskiej
    private static final double MIN_LAT = 52.0;
    private static final double MAX_LAT = 52.5;
    private static final double MIN_LON = 20.6;
    private static final double MAX_LON = 21.6;

    // Maksymalny dopuszczalny wiek ramki GPS (np. 180 sekund / 3 minuty)
    private static final Duration MAX_FRAME_AGE = Duration.ofSeconds(180);

    /**
     * Weryfikuje, czy pobrana ramka GPS pojazdu jest poprawna i aktualna.
     *
     * @param position ramka GPS pojazdu do weryfikacji
     * @return true jeśli ramka spełnia wszystkie kryteria jakościowe, false w przeciwnym razie
     */
    public static boolean isValidFrame(LiveVehiclePosition position) {
        return isValidFrame(position, Instant.now());
    }

    /**
     * Weryfikuje ramkę GPS względem podanego czasu odniesienia (przydatne w testach jednostkowych).
     */
    public static boolean isValidFrame(LiveVehiclePosition position, Instant referenceTime) {
        if (position == null) {
            return false;
        }

        // 1. Sprawdzenie podstawowych pól identyfikacyjnych
        if (position.getVehicleId() == null || position.getVehicleId().isBlank() ||
                position.getLineNumber() == null || position.getLineNumber().isBlank()) {
            logger.debug("Odrzucono ramkę GPS: brak VehicleNumber lub LineNumber");
            return false;
        }

        // 2. Filtr punktów zerowych (0.0, 0.0) oraz obszaru Warszawy
        double lat = position.getLatitude();
        double lon = position.getLongitude();

        if (lat == 0.0 && lon == 0.0) {
            logger.debug("Odrzucono ramkę GPS pojazdu {}: punkt (0,0)", position.getVehicleId());
            return false;
        }

        if (lat < MIN_LAT || lat > MAX_LAT || lon < MIN_LON || lon > MAX_LON) {
            logger.debug("Odrzucono ramkę GPS pojazdu {}: współrzędne ({}, {}) poza obszarem Warszawy",
                    position.getVehicleId(), lat, lon);
            return false;
        }

        // 3. Filtr świeżości danych (przestarzałe znaczniki czasu)
        Instant frameTime = position.getLastUpdate();
        if (frameTime == null) {
            logger.debug("Odrzucono ramkę GPS pojazdu {}: brak znacznika czasu", position.getVehicleId());
            return false;
        }

        Duration age = Duration.between(frameTime, referenceTime);

        // Odrzucenie ramek z przyszłości (tolerancja do 60 sekund z powodu różnic zegarów)
        if (age.isNegative() && age.abs().compareTo(Duration.ofSeconds(60)) > 0) {
            logger.debug("Odrzucono ramkę GPS pojazdu {}: znacznik czasu z przyszłości ({})",
                    position.getVehicleId(), frameTime);
            return false;
        }

        // Odrzucenie ramek starszych niż MAX_FRAME_AGE (180 sekund)
        if (!age.isNegative() && age.compareTo(MAX_FRAME_AGE) > 0) {
            logger.debug("Odrzucono ramkę GPS pojazdu {}: przestarzały znacznik czasu (wiek: {} s)",
                    position.getVehicleId(), age.getSeconds());
            return false;
        }

        return true;
    }
}