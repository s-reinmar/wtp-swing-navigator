package pl.j.reinmar.mapwaw.wtp.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteStopTest {

    @Test
    @DisplayName("Przechowuje i aktualizuje przystanek oraz dane kolejności trasy")
    void shouldStoreAndUpdateRouteStopData() {
        Stop stop = new Stop("700901", "Centrum", "01", 52.23, 21.01, true);
        RouteStop routeStop = new RouteStop(stop, 1, 0);

        assertSame(stop, routeStop.getStop());
        assertEquals(1, routeStop.getSequenceOrder());
        assertEquals(0, routeStop.getTravelTimeFromStartSec());

        Stop nextStop = new Stop("700902", "Dworzec", "02", 52.24, 21.02, false);
        routeStop.setStop(nextStop);
        routeStop.setSequenceOrder(2);
        routeStop.setTravelTimeFromStartSec(180);

        assertSame(nextStop, routeStop.getStop());
        assertEquals(2, routeStop.getSequenceOrder());
        assertEquals(180, routeStop.getTravelTimeFromStartSec());
        assertTrue(new RouteStop(null, 3, 240).toString().contains("stop=null"));
    }
}
