package fapi.service;

import config.Settings;
import data.feipData.ServiceOpData;
import data.feipData.ServiceType;
import fapi.LightService;
import fapi.client.FapiClient;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** What a light server writes into the Service it publishes or updates. */
class LightServicePublishTest {

    static final String DOCK = "DOCK@No1_NrC7", ROAD = "ROAD@No1_NrC7", MAP = "MAP@No1_NrC7",
            CALL = "CALL@No1_NrC7", BASE = "BASE@No1_NrC7";

    @Test
    void componentsAreWrittenInFullWithoutBaseAndRoadBringsMap() {
        List<String> notes = new ArrayList<>();
        assertEquals(List.of(DOCK, ROAD, "FUDP@No1_NrC7", MAP),
                LightService.components(List.of("dock", BASE, " ROAD ", "FUDP@No1_NrC7", "DOCK@No1_NrC7"), notes));
        assertEquals(4, notes.size(), String.join("\n", notes));
        assertTrue(notes.stream().noneMatch(n -> n.contains("won't start")));
    }

    @Test
    void aServiceWithNothingRunnableIsFlagged() {
        List<String> notes = new ArrayList<>();
        assertEquals(List.of(), LightService.components(List.of(BASE), notes));
        assertTrue(notes.get(notes.size() - 1).contains("won't start"));
        notes.clear();
        LightService.components(null, notes);
        assertTrue(notes.get(0).contains("won't start"));
    }

    @Test
    void theUpstreamIsAddedOnce() {
        assertEquals(List.of("UP"), LightService.withUpstream(null, "UP"));
        assertEquals(List.of("A", "UP"), LightService.withUpstream(List.of("A", "UP"), "UP"));
        assertEquals(List.of("A"), LightService.withUpstream(List.of("A"), null));
    }

    static Settings settings(boolean light, String upstreamSid) {
        Settings s = mock(Settings.class);
        when(s.getClient(any(ServiceType.class))).thenReturn(null);
        if (!light) when(s.getClient(ServiceType.ES)).thenReturn(new Object());
        FapiClient up = mock(FapiClient.class);
        when(up.getServiceSid()).thenReturn(upstreamSid);
        when(s.getUpstreamFapiClient()).thenReturn(upstreamSid == null ? null : up);
        return s;
    }

    static ServiceOpData typed(String... components) {
        ServiceOpData d = new ServiceOpData();
        d.setComponents(new ArrayList<>(List.of(components)));
        return d;
    }

    @Test
    void aLightServerFixesItsDeclarationAndMayListItsUpstream() {
        FapiServer yes = new FapiServer(null, new BufferedReader(new StringReader("y\n")), null, settings(true, "UPSID"));
        ServiceOpData d = typed("road", "call");
        yes.applyLightServiceRules(d);
        assertEquals(List.of(ROAD, CALL, MAP), d.getComponents());
        assertEquals(List.of("UPSID"), d.getServices());

        FapiServer no = new FapiServer(null, new BufferedReader(new StringReader("\n")), null, settings(true, "UPSID"));
        ServiceOpData d2 = typed(DOCK);
        no.applyLightServiceRules(d2);
        assertNull(d2.getServices(), "listing the upstream is not forced");
    }

    @Test
    void aFullServerIsLeftAlone() {
        FapiServer full = new FapiServer(null, new BufferedReader(new StringReader("y\n")), null, settings(false, "UPSID"));
        ServiceOpData d = typed(BASE, "road");
        full.applyLightServiceRules(d);
        assertEquals(List.of(BASE, "road"), d.getComponents());
        assertNull(d.getServices());
    }
}
