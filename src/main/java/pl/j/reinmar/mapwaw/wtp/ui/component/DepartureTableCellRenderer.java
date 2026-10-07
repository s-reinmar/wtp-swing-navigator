package pl.j.reinmar.mapwaw.wtp.ui.component;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * Customowy renderer komórek JTable dla tablicy odjazdów, wyróżniający kolorystycznie
 * stan opóźnienia kursu (kursy na czas, opóźnione, przyspieszone).
 */
public class DepartureTableCellRenderer extends DefaultTableCellRenderer {

    // Kolory statusów opóźnienia
    private static final Color COLOR_ON_TIME_BG = new Color(230, 245, 230); // Jasnozielony
    private static final Color COLOR_DELAYED_BG = new Color(255, 230, 230); // Jasnoczerwony / Różowy
    private static final Color COLOR_EARLY_BG = new Color(230, 240, 255);   // Jasnoniebieski

    private static final Color COLOR_DELAYED_TEXT = new Color(180, 0, 0);   // Ciemnoczerwony
    private static final Color COLOR_ON_TIME_TEXT = new Color(0, 100, 0);   // Ciemnozielony

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {

        Component cell = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        if (isSelected) {
            return cell; // Pozostawiamy domyślny kolor zaznaczenia Swing
        }

        // Pobieramy wartość opóźnienia z 4. kolumny ("Opóźnienie")
        Object delayValue = table.getValueAt(row, 4);
        String delayText = delayValue != null ? delayValue.toString().trim().toLowerCase() : "";

        // Domyślne tło naprzemienne dla czytelności JTable
        if (row % 2 == 0) {
            cell.setBackground(Color.WHITE);
        } else {
            cell.setBackground(new Color(248, 249, 250));
        }
        cell.setForeground(Color.BLACK);
        cell.setFont(cell.getFont().deriveFont(Font.PLAIN));

        // Wyróżnienie kolorystyczne zależne od opóźnienia
        if (delayText.contains("+") || isPositiveDelay(delayText)) {
            // Kurs opóźniony -> Czerwony/Różowy
            cell.setBackground(COLOR_DELAYED_BG);
            if (column == 4) {
                cell.setForeground(COLOR_DELAYED_TEXT);
                cell.setFont(cell.getFont().deriveFont(Font.BOLD));
            }
        } else if (delayText.contains("-")) {
            // Kurs przyspieszony -> Niebieski
            cell.setBackground(COLOR_EARLY_BG);
        } else if (delayText.contains("0") || delayText.contains("brak") || delayText.contains("na czas")) {
            // Kurs na czas -> Zielony
            if (column == 4) {
                cell.setBackground(COLOR_ON_TIME_BG);
                cell.setForeground(COLOR_ON_TIME_TEXT);
            }
        }

        // Wyrównanie w zależności od kolumny
        if (column == 1) {
            setHorizontalAlignment(SwingConstants.LEFT); // Kierunek do lewej
        } else {
            setHorizontalAlignment(SwingConstants.CENTER); // Pozostałe wyśrodkowane
        }

        return cell;
    }

    private boolean isPositiveDelay(String text) {
        try {
            String numOnly = text.replaceAll("[^0-9]", "");
            return !numOnly.isEmpty() && Integer.parseInt(numOnly) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}