package pl.j.reinmar.mapwaw.wtp.ui.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.j.reinmar.mapwaw.wtp.parser.WtpTxtScheduleParser;
import pl.j.reinmar.mapwaw.wtp.repository.ScheduleRepository;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.List;

/**
 * Wątek tła (SwingWorker) z pełnym raportowaniem postępu procentowego paska JProgressBar
 * podczas wczytywania plików rozkładów jazdy.
 */
public class ScheduleLoadingWorker extends SwingWorker<Void, Integer> implements PropertyChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleLoadingWorker.class);

    private final File scheduleFile;
    private final ScheduleRepository repository;
    private final JProgressBar progressBar;
    private final JLabel statusLabel;
    private final Runnable onComplete;

    public ScheduleLoadingWorker(File scheduleFile, ScheduleRepository repository,
                                 JProgressBar progressBar, JLabel statusLabel, Runnable onComplete) {
        this.scheduleFile = scheduleFile;
        this.repository = repository;
        this.progressBar = progressBar;
        this.statusLabel = statusLabel;
        this.onComplete = onComplete;

        // Rejestracja nasłuchiwania zmiany właściwości "progress"
        addPropertyChangeListener(this);
    }

    @Override
    protected Void doInBackground() throws Exception {
        logger.info("Rozpoczęcie parsowania pliku rozkładu w tle z raportowaniem postępu...");

        if (progressBar != null) {
            SwingUtilities.invokeLater(() -> {
                progressBar.setIndeterminate(false);
                progressBar.setValue(0);
                progressBar.setVisible(true);
            });
        }

        WtpTxtScheduleParser parser = new WtpTxtScheduleParser();

        // Przekazanie callbacku raportującego postęp do SwingWorker przez setProgress()
        parser.parse(scheduleFile, repository, progress -> setProgress(progress));

        return null;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("progress".equals(evt.getPropertyName())) {
            int progress = (Integer) evt.getNewValue();
            if (progressBar != null) {
                progressBar.setValue(progress);
            }
            if (statusLabel != null) {
                statusLabel.setText(String.format("Wczytywanie rozkładu: %d%%", progress));
            }
        }
    }

    @Override
    protected void process(List<Integer> chunks) {
        // Opcjonalne przetwarzanie dodatkowych paczek danych
    }

    @Override
    protected void done() {
        try {
            get();
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
                progressBar.setValue(100);
            }
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }
}