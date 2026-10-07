package pl.j.reinmar.mapwaw.wtp.test;

import pl.j.reinmar.mapwaw.wtp.config.AppConfig;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ZtmTestApp extends JFrame {

    // Pobieranie parametrów konfiguracyjnych z AppConfig (config.properties)
    private static final String API_ENDPOINT = AppConfig.getApiEndpoint();
    private static final String RESOURCE_ID = AppConfig.getResourceId();

    private JTextField apiKeyField;
    private JComboBox<String> typeComboBox;
    private JTextField lineField;
    private JButton fetchButton;
    private JTable resultTable;
    private DefaultTableModel tableModel;
    private JTextArea rawJsonArea;
    private JLabel statusLabel;

    private final HttpClient httpClient;

    public ZtmTestApp() {
        super("ZTM Warszawa - Minimalny Tester API (Swing / AWT)");

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        initUI();
        loadApiKeyFromConfig();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 650);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // --- Panel górny: Parametry zapytania ---
        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setBorder(BorderFactory.createTitledBorder("Parametry Zapytania API ZTM (z config.properties)"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // API Key
        gbc.gridx = 0; gbc.gridy = 0;
        topPanel.add(new JLabel("API Key:"), gbc);

        apiKeyField = new JTextField(30);
        gbc.gridx = 1; gbc.gridy = 0; gbc.gridwidth = 3;
        topPanel.add(apiKeyField, gbc);

        // Typ pojazdu (1 = Autobusy, 2 = Tramwaje)
        gbc.gridwidth = 1;
        gbc.gridx = 0; gbc.gridy = 1;
        topPanel.add(new JLabel("Typ pojazdu:"), gbc);

        typeComboBox = new JComboBox<>(new String[]{"1 - Autobusy", "2 - Tramwaje"});
        gbc.gridx = 1; gbc.gridy = 1;
        topPanel.add(typeComboBox, gbc);

        // Numer linii (opcjonalny)
        gbc.gridx = 2; gbc.gridy = 1;
        topPanel.add(new JLabel("Linia (opcjonalnie):"), gbc);

        lineField = new JTextField(8);
        gbc.gridx = 3; gbc.gridy = 1;
        topPanel.add(lineField, gbc);

        // Przycisk Pobierz
        fetchButton = new JButton("Pobierz dane GPS");
        fetchButton.setFont(fetchButton.getFont().deriveFont(Font.BOLD));
        gbc.gridx = 4; gbc.gridy = 0; gbc.gridheight = 2; gbc.fill = GridBagConstraints.BOTH;
        topPanel.add(fetchButton, gbc);

        add(topPanel, BorderLayout.NORTH);

        // --- Panel centralny: Tablica i Surowy JSON ---
        JTabbedPane tabbedPane = new JTabbedPane();

        // Zakładka 1: Tabela pojazdów
        String[] columnNames = {"Linia", "Nr pojazdu", "Szerokość (Lat)", "Długość (Lon)", "Brygada", "Czas GPS"};
        tableModel = new DefaultTableModel(columnNames, 0);
        resultTable = new JTable(tableModel);
        resultTable.setAutoCreateRowSorter(true);
        tabbedPane.addTab("Sparsowane pozycje pojazdów", new JScrollPane(resultTable));

        // Zakładka 2: Surowa odpowiedź JSON
        rawJsonArea = new JTextArea();
        rawJsonArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        rawJsonArea.setEditable(false);
        tabbedPane.addTab("Surowa odpowiedź JSON", new JScrollPane(rawJsonArea));

        add(tabbedPane, BorderLayout.CENTER);

        // --- Panel dolny: Pasek statusu ---
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusLabel = new JLabel("Gotowy do pobrania danych.");
        statusPanel.add(statusLabel);
        add(statusPanel, BorderLayout.SOUTH);

        // Reakcja na przycisk
        fetchButton.addActionListener(e -> fetchZtmData());
    }

    /**
     * Wczytuje klucz API za pomocą klasy AppConfig (która sprawdza config.properties oraz apiKey.txt).
     */
    private void loadApiKeyFromConfig() {
        String key = AppConfig.getApiKey();
        if (key != null && !key.isBlank()) {
            apiKeyField.setText(key);
            statusLabel.setText("Wczytano klucz API z konfiguracji (AppConfig)");
        } else {
            statusLabel.setText("Brak klucza API w konfiguracji. Wprowadź go ręcznie.");
        }
    }

    /**
     * Wysyła zapytanie HTTP w wątku tła (SwingWorker).
     */
    private void fetchZtmData() {
        String apiKey = apiKeyField.getText().trim();
        if (apiKey.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Wprowadź klucz API!", "Błąd", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int type = typeComboBox.getSelectedIndex() == 0 ? 1 : 2;
        String line = lineField.getText().trim();

        // Budowanie adresu URL zapytania przy użyciu parametrów z AppConfig
        StringBuilder urlBuilder = new StringBuilder(API_ENDPOINT);
        urlBuilder.append("?apikey=").append(apiKey);
        urlBuilder.append("&resource_id=").append(RESOURCE_ID);
        urlBuilder.append("&type=").append(type);
        if (!line.isEmpty()) {
            urlBuilder.append("&line=").append(line);
        }

        fetchButton.setEnabled(false);
        statusLabel.setText("Pobieranie danych z API ZTM...");
        tableModel.setRowCount(0);
        rawJsonArea.setText("");

        // Wykonanie HTTP na osobnym wątku
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(urlBuilder.toString()))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new RuntimeException("Serwer zwrócił kod HTTP " + response.statusCode());
                }
                return response.body();
            }

            @Override
            protected void done() {
                try {
                    String jsonResponse = get();
                    rawJsonArea.setText(jsonResponse);
                    parseAndDisplayJson(jsonResponse);
                } catch (Exception e) {
                    statusLabel.setText("Błąd zapytania.");
                    JOptionPane.showMessageDialog(ZtmTestApp.this,
                            "Błąd podczas pobierania danych:\n" + e.getMessage(),
                            "Błąd API", JOptionPane.ERROR_MESSAGE);
                } finally {
                    fetchButton.setEnabled(true);
                }
            }
        };

        worker.execute();
    }

    /**
     * Szybkie parsowanie wartości JSON wyrażeniami regularnymi.
     */
    private void parseAndDisplayJson(String json) {
        if (json.contains("\"result\":\"") || !json.contains("\"result\":[")) {
            statusLabel.setText("API zwróciło komunikat błędu (zobacz zakładkę JSON).");
            return;
        }

        Pattern objectPattern = Pattern.compile("\\{([^\\}]+)\\}");
        Matcher objectMatcher = objectPattern.matcher(json);

        int count = 0;
        while (objectMatcher.find()) {
            String objectContent = objectMatcher.group(1);

            String lines = extractValue(objectContent, "Lines");
            String vehicleNumber = extractValue(objectContent, "VehicleNumber");
            String lat = extractValue(objectContent, "Lat");
            String lon = extractValue(objectContent, "Lon");
            String brigade = extractValue(objectContent, "Brigade");
            String time = extractValue(objectContent, "Time");

            if (!lines.isEmpty() && !vehicleNumber.isEmpty()) {
                tableModel.addRow(new Object[]{lines, vehicleNumber, lat, lon, brigade, time});
                count++;
            }
        }

        statusLabel.setText("Pobrano pomyślnie. Znaleziono pojazdów: " + count);
    }

    private String extractValue(String jsonObject, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"?([^\",\\}]+)\"?");
        Matcher matcher = pattern.matcher(jsonObject);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            ZtmTestApp app = new ZtmTestApp();
            app.setVisible(true);
        });
    }
}