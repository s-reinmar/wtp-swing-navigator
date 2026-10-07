package pl.j.reinmar.mapwaw.wtp.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteVariantTest {

    @Test
    @DisplayName("Przechowuje i aktualizuje dane wariantu trasy")
    void shouldStoreAndUpdateRouteVariantData() {
        RouteStop firstStop = new RouteStop(null, 1, 0);
        List<RouteStop> stops = List.of(firstStop);
        RouteVariant variant = new RouteVariant("507-A", "507", "Centrum", stops);

        assertEquals("507-A", variant.getId());
        assertEquals("507", variant.getLineNumber());
        assertEquals("Centrum", variant.getDirectionName());
        assertSame(stops, variant.getRouteStops());
        assertTrue(variant.toString().contains("stopsCount=1"));

        List<RouteStop> updatedStops = List.of(firstStop, new RouteStop(null, 2, 120));
        variant.setId("507-B");
        variant.setLineNumber("509");
        variant.setDirectionName("Dworzec");
        variant.setRouteStops(updatedStops);

        assertEquals("507-B", variant.getId());
        assertEquals("509", variant.getLineNumber());
        assertEquals("Dworzec", variant.getDirectionName());
        assertSame(updatedStops, variant.getRouteStops());
        assertTrue(new RouteVariant("x", "1", "A", null).toString().contains("stopsCount=0"));
    }
}
