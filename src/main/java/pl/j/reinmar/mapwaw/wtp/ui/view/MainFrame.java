package pl.j.reinmar.mapwaw.wtp.ui.view;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;

/**
 * Główne okno aplikacji Swing/AWT reprezentujące widok nawigacyjny WTP Warszawa.
 * Rozszerza klasę JFrame i definiuje bazowe parametry okna (tytuł, wymiary, centrowanie).
 */
public class MainFrame extends JFrame {

    private static final Logger logger = LoggerFactory.getLogger(MainFrame.class);

    private static final String APP_TITLE = "WTP Warszawa - Nawigacja i Mapa";
    private static final int DEFAULT_WIDTH = 1280;
    private static final int DEFAULT_HEIGHT = 800;

    /**
     * Konstruktor tworzący główne okno aplikacji z domyślnym rozmiarem i konfiguracją.
     */
    public MainFrame() {
        super(APP_TITLE);
        initFrame();
    }

    /**
     * Inicjalizuje podstawowe właściwości komponentu JFrame.
     */
    private void initFrame() {
        logger.info("Inicjalizacja głównego okna aplikacji MainFrame (rozmiar: {}x{})...", DEFAULT_WIDTH, DEFAULT_HEIGHT);

        // 1. Ustawienie domyślnej operacji zamykania aplikacji
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // 2. Ustawienie preferowanego rozmiaru okna
        setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        setMinimumSize(new Dimension(1024, 600));

        // 3. Wycentrowanie okna na ekranie
        setLocationRelativeTo(null);

        // 4. Ustawienie bazowego zarządcy układu
        setLayout(new BorderLayout());

        logger.info("Główne okno MainFrame zostało pomyślnie skonsolidowane.");
    }

    /**
     * Metoda pomocnicza uruchamiająca widoczność okna na wątku EDT.
     */
    public void display() {
        SwingUtilities.invokeLater(() -> setVisible(true));
    }
}