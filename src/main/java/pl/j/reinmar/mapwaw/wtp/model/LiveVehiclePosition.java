package pl.j.reinmar.mapwaw.wtp.model;

import java.time.Instant;

/**
 * Klasa domenowa reprezentująca aktualną pozycję GPS pojazdu pobraną z API ZTM w czasie rzeczywistym.
 */
public class LiveVehiclePosition {
    private String vehicleId;     // Numer boczny pojazdu
    private String lineNumber;    // Linia
    private String brigade;       // Brygada
    private double latitude;      // Aktualna szerokość geograficzna
    private double longitude;     // Aktualna długość geograficzna
    private float bearing;        // Kąt / kierunek jazdy w stopniach (0-360)
    private Instant lastUpdate;   // Czas odebrania ostatniej ramki GPS
    private int delaySeconds;     // Oszacowane opóźnienie względem rozkładu w sekundach

    public LiveVehiclePosition(String vehicleId, String lineNumber, String brigade,
                               double latitude, double longitude, float bearing,
                               Instant lastUpdate, int delaySeconds) {
        this.vehicleId = vehicleId;
        this.lineNumber = lineNumber;
        this.brigade = brigade;
        this.latitude = latitude;
        this.longitude = longitude;
        this.bearing = bearing;
        this.lastUpdate = lastUpdate;
        this.delaySeconds = delaySeconds;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    public String getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(String lineNumber) {
        this.lineNumber = lineNumber;
    }

    public String getBrigade() {
        return brigade;
    }

    public void setBrigade(String brigade) {
        this.brigade = brigade;
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

    public float getBearing() {
        return bearing;
    }

    public void setBearing(float bearing) {
        this.bearing = bearing;
    }

    public Instant getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(Instant lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public int getDelaySeconds() {
        return delaySeconds;
    }

    public void setDelaySeconds(int delaySeconds) {
        this.delaySeconds = delaySeconds;
    }

    @Override
    public String toString() {
        return "LiveVehiclePosition{" +
                "vehicleId='" + vehicleId + '\'' +
                ", lineNumber='" + lineNumber + '\'' +
                ", brigade='" + brigade + '\'' +
                ", lat=" + latitude +
                ", lon=" + longitude +
                ", delaySec=" + delaySeconds +
                '}';
    }
}
