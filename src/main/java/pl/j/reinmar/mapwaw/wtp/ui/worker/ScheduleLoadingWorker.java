package pl.j.reinmar.mapwaw.wtp.ui.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.j.reinmar.mapwaw.wtp.parser.WtpTxtScheduleParser;
import pl.j.reinmar.mapwaw.wtp.repository.ScheduleRepository;

import javax.swing.*;
import java.io.File;
import java.util.List;

/**
 * Wątek tła (SwingWorker) odpowiedzialny za wczytywanie i parsowanie statycznych plików rozkładów jazdy
 * bez blokowania głównego wątku interfejsu graficznego (EDT).
 */
public class ScheduleLoadingWorker extends SwingWorker<Void, Integer> {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleLoadingWorker.class);

    private final File scheduleFile;
    private final ScheduleRepository repository;
    private final JProgressBar progressBar;
    private final JLabel statusLabel;
    private final Runnable onComplete;

    /**
     * Konstruktor inicjalizujący worker ładujący plik rozkładu.
     *
     * @param scheduleFile plik tekstowy z rozkładem
     * @param repository   docelowy magazyn danych w pamięci RAM
     * @param progressBar  komponent paska postępu (opcjonalnie)
     * @param statusLabel  etykieta paska statusu (opcjonalnie)
     * @param onComplete   akcja wywoływana po zakończeniu ładowania (na wątku EDT)
     */
    public ScheduleLoadingWorker(File scheduleFile, ScheduleRepository repository,
                                 JProgressBar progressBar, JLabel statusLabel, Runnable onComplete) {
        this.scheduleFile = scheduleFile;
        this.repository = repository;
        this.progressBar = progressBar;
        this.statusLabel = statusLabel;
        this.onComplete = onComplete;
    }

    @Override
    protected Void doInBackground() throws Exception {
        logger.info("Rozpoczęcie parsowania pliku rozkładu w wątku tła SwingWorker...");

        if (statusLabel != null) {
            SwingUtilities.invokeLater(() -> statusLabel.setText("Wczytywanie i parsowanie rozkładu jazdy w tle..."));
        }
        if (progressBar != null) {
            SwingUtilities.invokeLater(() -> {
                progressBar.setIndeterminate(true);
                progressBar.setVisible(true);
            });
        }

        // Wywołanie głównego parsera statycznych plików WTP
        WtpTxtScheduleParser parser = new WtpTxtScheduleParser();
        parser.parse(scheduleFile, repository);

        return null;
    }

    @Override
    protected void process(List<Integer> chunks) {
        // Obsługa postępu przekazywanego w tle (jeśli zgłaszana przez publish)
        if (progressBar != null && !chunks.isEmpty()) {
            int latestProgress = chunks.get(chunks.size() - 1);
            progressBar.setValue(latestProgress);
        }
    }

    @Override
    protected void done() {
        try {
            get(); // Sprawdzenie ewentualnych wyjątków w tle
            logger.info("Zakończono parsowanie. Załadowano przystanków: {}, linii: {}",
                    repository.getStopsCount(), repository.getLinesCount());

            if (statusLabel != null) {
                statusLabel.setText(String.format("Gotowe. Załadowano przystanków: %d, linii: %d",
                        repository.getStopsCount(), repository.getLinesCount()));
            }
        } catch (Exception e) {
            logger.error("Błąd podczas parsowania pliku rozkładu w tle: {}", e.getMessage(), e);
            if (statusLabel != null) {
                statusLabel.setText("Błąd parsowania rozkładu: " + e.getMessage());
            }
            JOptionPane.showMessageDialog(null,
                    "Nie udało się przetworzyć pliku rozkładu:\n" + e.getMessage(),
                    "Błąd Parsowania", JOptionPane.ERROR_MESSAGE);
        } finally {
            if (progressBar != null) {
                progressBar.setIndeterminate(false);
                progressBar.setValue(100);
            }
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }
}