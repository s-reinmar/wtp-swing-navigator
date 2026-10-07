package pl.j.reinmar.mapwaw.wtp;

import javax.swing.*;

public class MainApp {

    public static void main(String[] args) {
        // Konfiguracja agenta HTTP dla OpenStreetMap (wymagana przez bibliotekę mapową)
        System.setProperty("http.agent", "WtpSwingNavigator/1.0 (pl.j.reinmar.mapwaw.wtp)");

        // Uruchomienie aplikacji w wątku zdarzeń Swing (EDT)
        SwingUtilities.invokeLater(() -> {
            try {
                // Ustawienie natywnego wyglądu systemowego okien
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                System.err.println("Nie udało się ustawić systemowego Look and Feel: " + e.getMessage());
            }

            // Inicjalizacja głównego okna aplikacji
            JFrame frame = new JFrame("WTP Warszawa - Nawigacja i Mapa");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1280, 800);
            frame.setLocationRelativeTo(null);

            // Tymczasowy panel główny (w kolejnych fazach zastąpiony przez MapPanel i SidebarPanel)
            JPanel placeholderPanel = new JPanel();
            placeholderPanel.add(new JLabel("Aplikacja WTP Swing Navigator - Start w toku..."));
            frame.add(placeholderPanel);

            frame.setVisible(true);
        });
    }
}
