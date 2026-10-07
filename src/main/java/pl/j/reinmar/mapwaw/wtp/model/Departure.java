package pl.j.reinmar.mapwaw.wtp.model;

import java.time.LocalTime;

/**
 * Klasa domenowa reprezentująca rozkładowy odjazd pojazdu z przystanku[cite: 11, 12].
 */
public class Departure {
    private Line line;               // Linia komunikacyjna[cite: 11, 12]
    private Stop stop;               // Przystanek[cite: 11, 12]
    private LocalTime departureTime; // Godzina odjazdu[cite: 11, 12]
    private DayType dayType;         // Typ dnia (WEEKDAY, SATURDAY, SUNDAY)[cite: 11, 12]
    private String brigade;          // Numer brygady (np. 04)[cite: 11, 12]

    public Departure(Line line, Stop stop, LocalTime departureTime, DayType dayType, String brigade) {
        this.line = line;
        this.stop = stop;
        this.departureTime = departureTime;
        this.dayType = dayType;
        this.brigade = brigade;
    }

    public Line getLine() {
        return line;
    }

    public void setLine(Line line) {
        this.line = line;
    }

    public Stop getStop() {
        return stop;
    }

    public void setStop(Stop stop) {
        this.stop = stop;
    }

    public LocalTime getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(LocalTime departureTime) {
        this.departureTime = departureTime;
    }

    public DayType getDayType() {
        return dayType;
    }

    public void setDayType(DayType dayType) {
        this.dayType = dayType;
    }

    public String getBrigade() {
        return brigade;
    }

    public void setBrigade(String brigade) {
        this.brigade = brigade;
    }

    @Override
    public String toString() {
        return "Departure{" +
                "line=" + (line != null ? line.getLineNumber() : "null") +
                ", stop=" + (stop != null ? stop.getName() : "null") +
                ", time=" + departureTime +
                ", dayType=" + dayType +
                ", brigade='" + brigade + '\'' +
                '}';
    }
}