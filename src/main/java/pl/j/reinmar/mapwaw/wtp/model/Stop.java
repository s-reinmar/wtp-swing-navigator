package pl.j.reinmar.mapwaw.wtp.model;

/**
 * Klasa domenowa reprezentująca fizyczny słupek przystankowy w sieci WTP (ZTM Warszawa).
 * Przechowuje informacje o identyfikatorze, nazwie zespołu, numerze słupka,
 * współrzędnych geograficznych oraz dostępności dla osób niepełnosprawnych.
 */
public class Stop {
    private String id;                  // Unikalny identyfikator (np. zespół + słup: 700901)
    private String name;                // Nazwa zespołu przystankowego (np. Centrum)
    private String code;                // Numer słupka (np. 01)
    private double latitude;            // Szerokość geograficzna (lat)
    private double longitude;           // Długość geograficzna (lon)
    private boolean wheelchairAccessible; // Dostępność dla niepełnosprawnych i wózków

    public Stop(String id, String name, String code, double latitude, double longitude, boolean wheelchairAccessible) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.latitude = latitude;
        this.longitude = longitude;
        this.wheelchairAccessible = wheelchairAccessible;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public boolean isWheelchairAccessible() {
        return wheelchairAccessible;
    }

    public void setWheelchairAccessible(boolean wheelchairAccessible) {
        this.wheelchairAccessible = wheelchairAccessible;
    }

    @Override
    public String toString() {
        return "Stop{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", code='" + code + '\'' +
                ", lat=" + latitude +
                ", lon=" + longitude +
                '}';
    }
}