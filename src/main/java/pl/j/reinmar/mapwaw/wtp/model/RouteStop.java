package pl.j.reinmar.mapwaw.wtp.model;

/**
 * Klasa domenowa reprezentująca pojedynczy krok trasy (przystanek w ramach wariantu).
 */
public class RouteStop {
    private Stop stop;                    // Referencja do obiektu przystanku
    private int sequenceOrder;            // Kolejność na trasie (1, 2, 3...)
    private int travelTimeFromStartSec;   // Czas dojazdu od pętli początkowej w sekundach

    public RouteStop(Stop stop, int sequenceOrder, int travelTimeFromStartSec) {
        this.stop = stop;
        this.sequenceOrder = sequenceOrder;
        this.travelTimeFromStartSec = travelTimeFromStartSec;
    }

    public Stop getStop() {
        return stop;
    }

    public void setStop(Stop stop) {
        this.stop = stop;
    }

    public int getSequenceOrder() {
        return sequenceOrder;
    }

    public void setSequenceOrder(int sequenceOrder) {
        this.sequenceOrder = sequenceOrder;
    }

    public int getTravelTimeFromStartSec() {
        return travelTimeFromStartSec;
    }

    public void setTravelTimeFromStartSec(int travelTimeFromStartSec) {
        this.travelTimeFromStartSec = travelTimeFromStartSec;
    }

    @Override
    public String toString() {
        return "RouteStop{" +
                "stop=" + (stop != null ? stop.getName() : "null") +
                ", sequenceOrder=" + sequenceOrder +
                ", travelTimeFromStartSec=" + travelTimeFromStartSec +
                '}';
    }
}