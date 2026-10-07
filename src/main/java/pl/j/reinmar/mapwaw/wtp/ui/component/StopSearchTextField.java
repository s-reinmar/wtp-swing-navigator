package pl.j.reinmar.mapwaw.wtp.ui.component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.j.reinmar.mapwaw.wtp.model.Stop;
import pl.j.reinmar.mapwaw.wtp.repository.ScheduleRepository;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Komponent JTextField z autouzupełnianiem oraz menu rozwijanym (JPopupMenu)
 * przeznaczony do wyszukiwania przystanków po nazwie w czasie rzeczywistym.
 */
public class StopSearchTextField extends JTextField {

    private static final Logger logger = LoggerFactory.getLogger(StopSearchTextField.class);

    private final ScheduleRepository scheduleRepository;
    private final JPopupMenu suggestionPopup;
    private final DefaultListModel<Stop> listModel;
    private final JList<Stop> suggestionList;
    private Consumer<Stop> onStopSelectedCallback;

    private static final String PLACEHOLDER = "Szukaj przystanku po nazwie...";

    /**
     * Konstruktor komponentu wyszukiwarki przystanków.
     *
     * @param scheduleRepository repozytorium z indeksem przystanków
     */
    public StopSearchTextField(ScheduleRepository scheduleRepository) {
        super(15);
        this.scheduleRepository = scheduleRepository;
        this.suggestionPopup = new JPopupMenu();
        this.listModel = new DefaultListModel<>();
        this.suggestionList = new JList<>(listModel);

        initUI();
        initListeners();
    }

    private void initUI() {
        setFont(new Font("SansSerif", Font.PLAIN, 13));
        setForeground(Color.GRAY);
        setText(PLACEHOLDER);

        // Konfiguracja podpowiedzi w podłączonej liście
        suggestionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        suggestionList.setFont(new Font("SansSerif", Font.PLAIN, 12));
        suggestionList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Stop stop) {
                    label.setText("🏏 " + stop.getName() + " [" + stop.getId() + "]");
                }
                return label;
            }
        });

        JScrollPane scrollPane = new JScrollPane(suggestionList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setPreferredSize(new Dimension(280, 150));

        suggestionPopup.setFocusable(false);
        suggestionPopup.add(scrollPane);
    }

    private void initListeners() {
        // Obsługa placeholder-a (tekstu zastępczego)
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (getText().equals(PLACEHOLDER)) {
                    setText("");
                    setForeground(Color.BLACK);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (getText().isBlank()) {
                    setText(PLACEHOLDER);
                    setForeground(Color.GRAY);
                }
            }
        });

        // Obsługa reakcji na wpisywanie tekstu w czasie rzeczywistym
        getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { updateSuggestions(); }
            @Override
            public void removeUpdate(DocumentEvent e) { updateSuggestions(); }
            @Override
            public void changedUpdate(DocumentEvent e) { updateSuggestions(); }
        });

        // Obsługa wyboru pozycji strzałkami i klawiszem Enter
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    if (suggestionPopup.isVisible() && listModel.getSize() > 0) {
                        int nextIndex = Math.min(suggestionList.getSelectedIndex() + 1, listModel.getSize() - 1);
                        suggestionList.setSelectedIndex(nextIndex);
                        suggestionList.ensureIndexIsVisible(nextIndex);
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    if (suggestionPopup.isVisible() && listModel.getSize() > 0) {
                        int prevIndex = Math.max(suggestionList.getSelectedIndex() - 1, 0);
                        suggestionList.setSelectedIndex(prevIndex);
                        suggestionList.ensureIndexIsVisible(prevIndex);
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    Stop selected = suggestionList.getSelectedValue();
                    if (selected != null) {
                        selectStop(selected);
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    suggestionPopup.setVisible(false);
                }
            }
        });

        // Obsługa kliknięcia myszą na liście podpowiedzi
        suggestionList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 1) {
                    Stop selected = suggestionList.getSelectedValue();
                    if (selected != null) {
                        selectStop(selected);
                    }
                }
            }
        });
    }

    /**
     * Wyszukuje pasujące przystanki w repozytorium i odświeża listę podpowiedzi.
     */
    private void updateSuggestions() {
        String query = getText().trim();
        if (query.isBlank() || query.equals(PLACEHOLDER) || query.length() < 2) {
            suggestionPopup.setVisible(false);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            List<Stop> matchingStops = scheduleRepository.findStopsByName(query);
            listModel.clear();

            if (!matchingStops.isEmpty()) {
                // Wyświetlamy maksymalnie 10 pasujących sugestii
                matchingStops.stream().limit(10).forEach(listModel::addElement);
                suggestionList.setSelectedIndex(0);

                if (!suggestionPopup.isVisible() && isShowing()) {
                    suggestionPopup.show(StopSearchTextField.this, 0, getHeight());
                    requestFocusInWindow();
                }
            } else {
                suggestionPopup.setVisible(false);
            }
        });
    }

    /**
     * Wybiera dany przystanek, uzupełnia pole tekstowe oraz wywołuje zarejestrowaną akcję.
     */
    private void selectStop(Stop stop) {
        setText(stop.getName());
        suggestionPopup.setVisible(false);
        logger.info("Wybrano przystanek z listy podpowiedzi: {} [{}]", stop.getName(), stop.getId());

        if (onStopSelectedCallback != null) {
            onStopSelectedCallback.accept(stop);
        }
    }

    /**
     * Rejestruje funkcję zwrotną (callback) po zatwierdzeniu wyboru przystanku.
     */
    public void setOnStopSelectedListener(Consumer<Stop> callback) {
        this.onStopSelectedCallback = callback;
    }
}