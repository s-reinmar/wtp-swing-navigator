package pl.j.reinmar.mapwaw.wtp.ui.view;

import org.jxmapviewer.viewer.DefaultWaypoint;
import org.jxmapviewer.viewer.GeoPosition;
import pl.j.reinmar.mapwaw.wtp.model.Stop;

/**
 * Znacznik mapy powiązany z przystankiem z rozkładu.
 */
public class StopWaypoint extends DefaultWaypoint {

    private final Stop stop;

    public StopWaypoint(Stop stop) {
        super(new GeoPosition(stop.getLatitude(), stop.getLongitude()));
        this.stop = stop;
    }

    public Stop getStop() {
        return stop;
    }
}
