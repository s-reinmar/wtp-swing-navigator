package pl.j.reinmar.mapwaw.wtp;

import com.formdev.flatlaf.FlatLightLaf;
import org.jxmapviewer.viewer.GeoPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.j.reinmar.mapwaw.wtp.model.RouteVariant;
import pl.j.reinmar.mapwaw.wtp.model.Stop;
import pl.j.reinmar.mapwaw.wtp.parser.GtfsScheduleLoader;
import pl.j.reinmar.mapwaw.wtp.repository.RealtimeVehicleCache;
import pl.j.reinmar.mapwaw.wtp.repository.ScheduleRepository;
import pl.j.reinmar.mapwaw.wtp.service.DelayCalculatorService;
import pl.j.reinmar.mapwaw.wtp.service.RealtimeFetchScheduler;
import pl.j.reinmar.mapwaw.wtp.service.RoutingEngine;
import pl.j.reinmar.mapwaw.wtp.service.WtpRealtimeApiClient;
import pl.j.reinmar.mapwaw.wtp.ui.component.StopSearchTextField;
import pl.j.reinmar.mapwaw.wtp.ui.controller.MapController;
import pl.j.reinmar.mapwaw.wtp.ui.view.DepartureBoardPanel;
import pl.j.reinmar.mapwaw.wtp.ui.view.MainFrame;
import pl.j.reinmar.mapwaw.wtp.ui.view.MapPanel;
import pl.j.reinmar.mapwaw.wtp.ui.view.RoutePlannerPanel;
import pl.j.reinmar.mapwaw.wtp.ui.view.RouteResultsPanel;
import pl.j.reinmar.mapwaw.wtp.ui.view.StopWaypoint;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MainApp {

    private static final Logger logger = LoggerFactory.getLogger(MainApp.class);
    private static final String DEFAULT_DATA_DIR = "src/main/resources/data";
    private static final int STOP_ZOOM_LEVEL = 2;

    /** Dane wczytane w tle, przekazywane do budowy interfejsu. */
    private record LoadedData(ScheduleRepository repository, RoutingEngine routingEngine) {
    }

    public static void main(String[] args) {
        // Konfiguracja agenta HTTP dla OpenStreetMap (wymagana przez bibliotekę mapową)
        System.setProperty("http.agent", "WtpSwingNavigator/1.0 (pl.j.reinmar.mapwaw.wtp)");
        Path dataDir = Path.of(args.length > 0 ? args[0] : DEFAULT_DATA_DIR);

        // Uruchomienie aplikacji w wątku zdarzeń Swing (EDT)
        SwingUtilities.invokeLater(() -> {
            configureLookAndFeel();

            MainFrame frame = new MainFrame();
            frame.setMapPanel(createPlaceholderPanel("Wczytywanie rozkładów jazdy..."));
            frame.setVisible(true);

            new SwingWorker<LoadedData, String>() {
                @Override
                protected LoadedData doInBackground() throws Exception {
                    ScheduleRepository repository = new ScheduleRepository();
                    List<RouteVariant> variants = new GtfsScheduleLoader()
                            .load(dataDir, repository, this::publish);
                    return new LoadedData(repository, new RoutingEngine(variants));
                }

                @Override
                protected void process(List<String> messages) {
                    frame.setStatusText(messages.get(messages.size() - 1));
                }

                @Override
                protected void done() {
                    try {
                        LoadedData data = get();
                        buildUi(frame, data);
                        frame.setStatusText(String.format("Gotowe. Przystanków: %d, linii: %d",
                                data.repository().getStopsCount(), data.repository().getLinesCount()));
                    } catch (Exception e) {
                        logger.error("Nie udało się wczytać rozkładów z {}", dataDir, e);
                        frame.setStatusText("Błąd wczytywania rozkładów: " + e.getMessage());
                        JOptionPane.showMessageDialog(frame,
                                "Nie udało się wczytać rozkładów z katalogu:\n" + dataDir.toAbsolutePath()
                                        + "\n\n" + e.getMessage(),
                                "Błąd danych", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });
    }

    private static void buildUi(MainFrame frame, LoadedData data) {
        ScheduleRepository repository = data.repository();
        RoutingEngine routingEngine = data.routingEngine();

        MapPanel mapPanel = new MapPanel();
        RealtimeVehicleCache vehicleCache = new RealtimeVehicleCache();
        mapPanel.setVehicleCache(vehicleCache);
        mapPanel.setWaypoints(repository.getAllStops().stream().map(StopWaypoint::new).toList());
        frame.setMapPanel(mapPanel);

        DelayCalculatorService delayCalculator = new DelayCalculatorService(repository);
        DepartureBoardPanel departureBoard = new DepartureBoardPanel();
        MapController mapController = new MapController(departureBoard, repository, delayCalculator);
        mapController.setMapPanel(mapPanel);
        departureBoard.setMapController(mapController);

        StopSearchTextField searchField = new StopSearchTextField(repository);
        RoutePlannerPanel routePlanner = new RoutePlannerPanel(repository);
        RouteResultsPanel routeResults = new RouteResultsPanel();
        routeResults.connectMapPanel(mapPanel);

        JTabbedPane tabs = new JTabbedPane();
        JPanel routeTab = new JPanel(new BorderLayout(0, 8));
        routeTab.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        routeTab.add(routePlanner, BorderLayout.NORTH);
        routeTab.add(routeResults, BorderLayout.CENTER);
        tabs.addTab("Połączenia", routeTab);

        JPanel departuresTab = new JPanel(new BorderLayout(0, 8));
        departuresTab.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        departuresTab.add(searchField, BorderLayout.NORTH);
        departuresTab.add(departureBoard, BorderLayout.CENTER);
        tabs.addTab("Odjazdy", departuresTab);

        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(380, 0));
        sidebar.add(tabs, BorderLayout.CENTER);
        frame.setSidebarPanel(sidebar);
        frame.setQuickSearchComponent(searchField);

        Runnable showStopOnMap = () -> {
            Stop stop = mapController.getCurrentlySelectedStop();
            if (stop != null) {
                mapPanel.getMapViewer().setZoom(STOP_ZOOM_LEVEL);
                mapPanel.getMapViewer().setAddressLocation(
                        new GeoPosition(stop.getLatitude(), stop.getLongitude()));
            }
        };
        mapPanel.setOnStopSelectedListener(waypoint -> {
            if (waypoint instanceof StopWaypoint stopWaypoint) {
                tabs.setSelectedComponent(departuresTab);
                mapController.onStopSelectedOnMap(stopWaypoint.getStop());
            }
        });
        searchField.setOnStopSelectedListener(stop -> {
            mapController.onStopSelectedOnMap(stop);
            showStopOnMap.run();
        });

        routePlanner.setOnRouteRequested(request -> searchRoutes(frame, routingEngine, repository,
                routePlanner, routeResults, request));

        RealtimeFetchScheduler realtimeScheduler = new RealtimeFetchScheduler(
                new WtpRealtimeApiClient(), vehicleCache, delayCalculator);
        realtimeScheduler.setConnectionStatusCallback(connected -> {
            frame.setGpsApiConnected(connected);
            mapController.setOfflineMode(!connected);
        });
        realtimeScheduler.setUiRefreshCallback(() -> SwingUtilities.invokeLater(() -> {
            mapPanel.getMapViewer().repaint();
            mapController.refreshDepartureBoard();
        }));
        frame.setOnRefreshRequested(realtimeScheduler::refreshNow);
        realtimeScheduler.start();
    }

    private static void searchRoutes(MainFrame frame, RoutingEngine routingEngine,
                                     ScheduleRepository repository, RoutePlannerPanel routePlanner,
                                     RouteResultsPanel routeResults,
                                     RoutePlannerPanel.RouteRequest request) {
        frame.setStatusText("Wyszukiwanie połączeń...");
        routePlanner.setEnabled(false);
        new SwingWorker<List<RoutingEngine.ScheduledRoute>, Void>() {
            @Override
            protected List<RoutingEngine.ScheduledRoute> doInBackground() {
                List<RoutingEngine.ScheduledRoute> routes = new ArrayList<>(
                        routingEngine.findDirectRoutes(request.origin(), request.destination(),
                                request.departureDateTime(), repository));
                routes.addAll(routingEngine.findRoutesWithOneTransfer(request.origin(),
                        request.destination(), request.departureDateTime(), repository));
                routes.sort(Comparator.comparing(RoutingEngine.ScheduledRoute::arrivalDateTime)
                        .thenComparingInt(RoutingEngine.ScheduledRoute::transfers));
                return routes.size() > 20 ? new ArrayList<>(routes.subList(0, 20)) : routes;
            }

            @Override
            protected void done() {
                routePlanner.setEnabled(true);
                try {
                    List<RoutingEngine.ScheduledRoute> routes = get();
                    routeResults.setRoutes(routes);
                    frame.setStatusText(routes.isEmpty()
                            ? "Nie znaleziono połączeń."
                            : "Znaleziono połączeń: " + routes.size());
                } catch (Exception e) {
                    logger.error("Błąd wyszukiwania połączeń", e);
                    frame.setStatusText("Błąd wyszukiwania połączeń: " + e.getMessage());
                }
            }
        }.execute();
    }

    private static void configureLookAndFeel() {
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
    }

    private static JPanel createPlaceholderPanel(String message) {
        JPanel placeholderPanel = new JPanel(new BorderLayout());
        placeholderPanel.add(new JLabel(message, SwingConstants.CENTER), BorderLayout.CENTER);
        return placeholderPanel;
    }
}
