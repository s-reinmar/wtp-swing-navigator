package pl.j.reinmar.mapwaw.wtp;

import com.formdev.flatlaf.FlatLightLaf;
import pl.j.reinmar.mapwaw.wtp.repository.RealtimeVehicleCache;
import pl.j.reinmar.mapwaw.wtp.repository.ScheduleRepository;
import pl.j.reinmar.mapwaw.wtp.service.DelayCalculatorService;
import pl.j.reinmar.mapwaw.wtp.service.RealtimeFetchScheduler;
import pl.j.reinmar.mapwaw.wtp.service.WtpRealtimeApiClient;
import pl.j.reinmar.mapwaw.wtp.ui.view.MainFrame;

import javax.swing.*;

public class MainApp {

    public static void main(String[] args) {
        // Konfiguracja agenta HTTP dla OpenStreetMap (wymagana przez bibliotekę mapową)
        System.setProperty("http.agent", "WtpSwingNavigator/1.0 (pl.j.reinmar.mapwaw.wtp)");

        // Uruchomienie aplikacji w wątku zdarzeń Swing (EDT)
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(new FlatLightLaf());
                UIManager.put("Component.arc", 12);
                UIManager.put("Button.arc", 10);
                UIManager.put("TextComponent.arc", 8);
                UIManager.put("ScrollBar.width", 12);
                UIManager.put("Table.showHorizontalLines", true);
                UIManager.put("Table.showVerticalLines", false);
            } catch (Exception e) {
                System.err.println("Nie udało się ustawić FlatLaf: " + e.getMessage());
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception fallbackException) {
                    System.err.println("Nie udało się ustawić systemowego Look and Feel: "
                            + fallbackException.getMessage());
                }
            }

            MainFrame frame = new MainFrame();
            frame.setMapPanel(createPlaceholderPanel());

            RealtimeFetchScheduler realtimeScheduler = new RealtimeFetchScheduler(
                    new WtpRealtimeApiClient(),
                    new RealtimeVehicleCache(),
                    new DelayCalculatorService(new ScheduleRepository()));
            realtimeScheduler.setConnectionStatusCallback(frame::setGpsApiConnected);

            frame.setVisible(true);
            realtimeScheduler.start();
        });
    }

    private static JPanel createPlaceholderPanel() {
        JPanel placeholderPanel = new JPanel();
        placeholderPanel.add(new JLabel("Aplikacja WTP Swing Navigator - Start w toku..."));
        return placeholderPanel;
    }
}