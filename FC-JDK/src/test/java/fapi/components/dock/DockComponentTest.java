package fapi.components.dock;

import config.Settings;
import data.apipData.Fcdsl;
import data.feipData.ServiceType;
import fapi.FapiBalanceManager;
import fapi.FapiCode;
import fapi.components.DockComponent;
import fapi.message.FapiRequest;
import fapi.message.FapiResponse;
import fapi.message.UnifiedCodec.UnifiedResponse;
import fapi.service.FapiServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** DockComponent on its LevelDB store, with no Elasticsearch anywhere: a light DOCK server. */
class DockComponentTest {

    @TempDir
    Path dir;
    FapiServer server;
    Settings settings;
    FapiBalanceManager balance;
    DockComponent dock;

    @BeforeEach
    void setUp() {
        server = mock(FapiServer.class);
        settings = mock(Settings.class);
        balance = mock(FapiBalanceManager.class);
        when(server.getSettings()).thenReturn(settings);
        when(server.getBalanceManager()).thenReturn(balance);
        when(settings.getDbDir()).thenReturn(dir.toString());
        when(settings.getSid()).thenReturn("sid1");
        when(settings.getMainFid()).thenReturn("FMain");
        when(settings.getClient(any(ServiceType.class))).thenReturn(null);
        when(balance.canAfford(anyString(), anyLong())).thenReturn(true);
        when(balance.getBestHeight()).thenReturn(1_000L);
        dock = new DockComponent();
        dock.initialize(server);
    }

    @AfterEach
    void tearDown() throws Exception {
        dock.close(1000);
    }

    String put(String sender, List<String> recipients, String text) {
        FapiRequest req = FapiRequest.operation("dock.put", Map.of("recipients", recipients, "maxDays", 1));
        req.setId("r");
        UnifiedResponse r = dock.handleUnifiedRequest(req, text.getBytes(StandardCharsets.UTF_8), sender);
        assertEquals(FapiCode.SUCCESS, r.response().getCode(), r.response().getMessage());
        return (String) ((Map<?, ?>) r.response().getData()).get("id");
    }

    FapiResponse fetch(String peer, List<String> recipientIds, Fcdsl fcdsl) {
        FapiRequest req = FapiRequest.query("dock.fetch", fcdsl);
        req.setId("f");
        req.setParams(Map.of("recipientIds", recipientIds));
        return dock.handleRequest(req, peer);
    }

    @Test
    void aServerWithoutEsHasNothingToMigrate() {
        assertTrue(dock.getStore().isMigrated());
        assertEquals(0, dock.getStore().count());
    }

    @Test
    void putsInOneMillisecondKeepDistinctIds() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 200; i++) ids.add(put("FAlice", List.of("FBob"), "m" + i));
        assertEquals(200, ids.size());
        assertEquals(200, dock.getStore().count());
    }

    @Test
    void fetchPagesWithCursorsAndReturnsData() {
        for (int i = 0; i < 7; i++) put("FAlice", List.of("FBob"), "hello " + i);
        Fcdsl f = new Fcdsl();
        f.addSort("createTime", "asc");
        f.addSort("id", "asc");
        f.setSize("5");
        FapiResponse first = fetch("FBob", List.of("FBob"), f);
        assertEquals(FapiCode.SUCCESS, first.getCode());
        List<?> items = (List<?>) first.getData();
        assertEquals(5, items.size());
        assertNotNull(((Map<?, ?>) items.get(0)).get("dataBase64"));
        assertEquals(3, first.getLast().size());

        f.setAfter(first.getLast());
        FapiResponse second = fetch("FBob", List.of("FBob"), f);
        assertEquals(2, ((List<?>) second.getData()).size());
        verify(balance, atLeastOnce()).charge(anyString(), eq("FBob"), anyLong(), eq("dock.fetch:out"));
    }

    @Test
    void wrongQueryIs400AndTooBroadIs501() {
        Fcdsl bad = new Fcdsl();
        bad.addSort("nope", "asc");
        assertEquals(FapiCode.BAD_REQUEST, fetch("FBob", List.of("FBob"), bad).getCode());

        Fcdsl bySize = new Fcdsl();
        bySize.addSort("size", "desc");
        assertEquals(FapiCode.SUCCESS, fetch("FBob", List.of("FBob"), bySize).getCode(),
                "a small inbox sorts in memory");
    }

    @Test
    void getCheckExtendDeleteUseTheStore() {
        String id = put("FAlice", List.of("FBob"), "payload");

        FapiRequest get = FapiRequest.operation("dock.get", Map.of("id", id));
        get.setId("g");
        UnifiedResponse got = dock.handleUnifiedRequest(get, null, "FBob");
        assertEquals("payload", new String(got.binaryData(), StandardCharsets.UTF_8));

        FapiRequest extend = FapiRequest.operation("dock.extend", Map.of("id", id, "extraDays", 2));
        extend.setId("e");
        assertEquals(FapiCode.SUCCESS, dock.handleRequest(extend, "FAlice").getCode());
        assertEquals(3, dock.getStore().get(id).getMaxDays());

        FapiRequest del = FapiRequest.operation("dock.delete", Map.of("id", id));
        del.setId("d");
        assertEquals(FapiCode.FORBIDDEN, dock.handleRequest(del, "FBob").getCode());
        assertEquals(FapiCode.SUCCESS, dock.handleRequest(del, "FAlice").getCode());
        assertNull(dock.getStore().get(id));
    }

    @Test
    void cleanupSweepsExpiredItems() {
        put("FAlice", List.of("FBob"), "x");
        assertEquals(0, dock.cleanupExpiredItems(1_000));
        assertEquals(1, dock.cleanupExpiredItems(1_000 + 1440));
        assertEquals(0, dock.getStore().count());
    }
}
