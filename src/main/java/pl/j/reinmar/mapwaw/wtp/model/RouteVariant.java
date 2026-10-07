package pl.j.reinmar.mapwaw.wtp.model;

import java.util.List;

/**
 * Klasa domenowa reprezentująca wariant trasy (kierunek oraz uporządkowaną listę przystanków)[cite: 12].
 */
public class RouteVariant {
    private String id;                  // Unikalny identyfikator wariantu[cite: 12]
    private String lineNumber;          // Powiązany numer linii[cite: 12]
    private String directionName;       // Nazwa przystanku docelowego (np. Gocław)[cite: 12]
    private List<RouteStop> routeStops; // Uporządkowana lista przystanków na trasie[cite: 12]

    public RouteVariant(String id, String lineNumber, String directionName, List<RouteStop> routeStops) {
        this.id = id;
        this.lineNumber = lineNumber;
        this.directionName = directionName;
        this.routeStops = routeStops;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(String lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getDirectionName() {
        return directionName;
    }

    public void setDirectionName(String directionName) {
        this.directionName = directionName;
    }

    public List<RouteStop> getRouteStops() {
        return routeStops;
    }

    public void setRouteStops(List<RouteStop> routeStops) {
        this.routeStops = routeStops;
    }

    @Override
    public String toString() {
        return "RouteVariant{" +
                "id='" + id + '\'' +
                ", lineNumber='" + lineNumber + '\'' +
                ", directionName='" + directionName + '\'' +
                ", stopsCount=" + (routeStops != null ? routeStops.size() : 0) +
                '}';
    }
}