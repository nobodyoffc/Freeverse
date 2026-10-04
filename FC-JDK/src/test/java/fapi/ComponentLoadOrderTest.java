package fapi;

import config.Settings;
import data.feipData.Service;
import fapi.components.MapComponent;
import fapi.components.RoadComponent;
import fapi.service.FapiServer;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** ROAD looks MAP up when it initializes, so MAP must be loaded first whatever the declared order. */
class ComponentLoadOrderTest {

    static FapiServer server() throws Exception {
        Service service = new Service();
        service.setId("component-order-test");
        service.setType("FAPI@No1_NrC7");
        service.setPricePerKB("0");
        Settings settings = mock(Settings.class);
        when(settings.getService()).thenReturn(service);
        when(settings.getMainFid()).thenReturn("FTpL6L2X9owmwKnCgPDQapL7MzfPfLkQmh");
        when(settings.getDbDir()).thenReturn(Files.createTempDirectory("component_order_db").toString());
        FapiServer server = new FapiServer(settings);
        server.initialize();
        return server;
    }

    /** The HK light server: its Service declares ROAD alone, and MAP is added for it. */
    @Test
    void aLightServerDeclaringOnlyRoadStarts() throws Exception {
        String[] types = ServiceBootstrap.resolveComponentTypes(new String[0], List.of("ROAD@No1_NrC7"), true);
        FapiServer server = server();
        server.loadComponentsByTypes(types);
        assertNotNull(server.getComponent(MapComponent.class));
        assertNotNull(server.getComponent(RoadComponent.class));
    }

    @Test
    void roadDeclaredBeforeMapStarts() throws Exception {
        FapiServer server = server();
        server.loadComponentsByTypes(new String[]{"ROAD@No1_NrC7", "MAP@No1_NrC7"});
        assertNotNull(server.getComponent(RoadComponent.class));
    }

    @Test
    void mapIsLoadedFirst() {
        List<FapiComponent> loaded = new ComponentRegistry()
                .loadComponents(new String[]{"DOCK@No1_NrC7", "ROAD@No1_NrC7", "MAP@No1_NrC7"});
        assertInstanceOf(MapComponent.class, loaded.get(0));
        assertEquals(3, loaded.size());
    }
}
