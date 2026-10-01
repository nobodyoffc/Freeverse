package config;

import data.feipData.ServiceType;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

/** A FAPI provider can be named by URL and SID when no client exists yet to list services. */
class FapiProviderDirectTest {

    static final String SID = "3b701e4efac842bb53ad2552be4dc4f1b7c76c6d68fc5fb632012a57295167c3";

    @Test
    void aLightServersFirstUpstreamIsNamedDirectly() {
        ApiProvider p = new ApiProvider();
        BufferedReader br = new BufferedReader(new StringReader("fudp://127.0.0.1:8500\n" + SID + "\n"));
        assertTrue(p.makeApiProvider(br, ServiceType.FAPI_No1_NrC7, null, null));
        assertEquals("fudp://127.0.0.1:8500", p.getApiUrl());
        assertEquals(SID, p.getId());
    }

    @Test
    void enterTakesTheLocalDefaultAndBadInputIsAskedAgain() {
        ApiProvider p = new ApiProvider();
        BufferedReader br = new BufferedReader(new StringReader("\nnot-a-sid\n" + SID + "\n"));
        assertTrue(p.makeApiProvider(br, ServiceType.FAPI_No1_NrC7, null, null));
        assertEquals("fudp://127.0.0.1:8500", p.getApiUrl());
        assertEquals(SID, p.getId());
    }
}
