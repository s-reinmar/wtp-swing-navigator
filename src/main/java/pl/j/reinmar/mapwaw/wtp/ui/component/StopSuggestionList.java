package pl.j.reinmar.mapwaw.wtp.ui.component;

import pl.j.reinmar.mapwaw.wtp.model.Stop;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Komponent JList dedykowany do prezentacji listy podpowiedzi pasujących przystanków.
 * Zawiera dedykowany renderer komórek z ikonami oraz obsługę zdarzeń wyboru.
 */
public class StopSuggestionList extends JList<Stop> {

    private final DefaultListModel<Stop> listModel;
    private Consumer<Stop> onStopSelectedCallback;

    /**
     * Konstruktor komponentu listy podpowiedzi przystanków.
     */
    public StopSuggestionList() {
        this.listModel = new DefaultListModel<>();
        setModel(listModel);
        initUI();
        initListeners();
    }

    private void initUI() {
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setFont(new Font("SansSerif", Font.PLAIN, 12));
        setFocusable(true);

        // Customowy renderer prezentujący nazwę oraz kod słupka przystankowego
        setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Stop stop) {
                    label.setText("🏏 " + stop.getName() + " (" + stop.getCode() + ") [" + stop.getId() + "]");
                    label.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
                }
                return label;
            }
        });
    }

    private void initListeners() {
        // Obsługa pojedynczego kliknięcia myszą
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 1) {
                    Stop selected = getSelectedValue();
                    if (selected != null && onStopSelectedCallback != null) {
                        onStopSelectedCallback.accept(selected);
                    }
                }
            }
        });
    }

    /**
     * Wypełnia listę nowym zbiorem pasujących przystanków.
     *
     * @param stops lista przystanków do wyświetlenia
     */
    public void setSuggestions(List<Stop> stops) {
        SwingUtilities.invokeLater(() -> {
            listModel.clear();
            if (stops != null) {
                stops.forEach(listModel::addElement);
                if (!listModel.isEmpty()) {
                    setSelectedIndex(0);
                }
            }
        });
    }

    /**
     * Czyszczenie zawartości listy podpowiedzi.
     */
    public void clearSuggestions() {
        SwingUtilities.invokeLater(listModel::clear);
    }

    /**
     * Rejestruje nasłuchiwanie wyboru przystanku z listy.
     */
    public void setOnStopSelectedListener(Consumer<Stop> callback) {
        this.onStopSelectedCallback = callback;
    }
}