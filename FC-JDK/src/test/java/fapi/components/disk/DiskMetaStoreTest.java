package fapi.components.disk;

import config.Settings;
import data.apipData.Fcdsl;
import data.fcData.DiskItem;
import data.feipData.ServiceType;
import db.fcdsl.FcdslException;
import db.fcdsl.FcdslResult;
import fapi.FapiBalanceManager;
import fapi.FapiCode;
import fapi.components.DiskComponent;
import fapi.message.FapiRequest;
import fapi.message.FapiResponse;
import fapi.service.FapiServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** DISK metadata in LevelDB, as DiskSyncManager and disk.list use it. */
class DiskMetaStoreTest {

    @TempDir
    Path dir;
    DiskMetaStore store;

    @BeforeEach
    void open() throws Exception {
        store = DiskMetaStore.open(dir.resolve("meta"), 100_000);
    }

    @AfterEach
    void close() throws Exception {
        store.close();
    }

    static String did(int i) {
        return String.format("%064x", i);
    }

    static DiskItem item(int i, long since, Long expire, long size) {
        return new DiskItem(did(i), since, expire, size);
    }

    static List<String> ids(FcdslResult<DiskItem> r) {
        List<String> out = new ArrayList<>();
        for (DiskItem d : r.getItems()) out.add(d.getId());
        return out;
    }

    @Test
    void syncPagesBySinceAndIdWithTwoValueCursors() {
        for (int i = 0; i < 250; i++) store.put(item(i, 1_000 + i / 3, null, 10));
        List<String> got = new ArrayList<>();
        List<String> cursor = null;
        while (true) {
            // exactly what DiskSyncManager sends
            Fcdsl f = new Fcdsl();
            f.addSort("since", "asc");
            f.addSort("id", "asc");
            f.addSize(100);
            if (cursor != null) f.setAfter(cursor);
            FcdslResult<DiskItem> r = store.query(store.compile(f));
            got.addAll(ids(r));
            if (r.getItems().size() < 100) break;
            cursor = r.getLast();
            assertEquals(2, cursor.size(), "DiskSyncState stores [since, id]");
            assertTrue(r.getExamined() <= 101, "read in index order, examined " + r.getExamined());
        }
        assertEquals(250, got.size());
        assertEquals(did(0), got.get(0));
        assertEquals(did(249), got.get(249));
    }

    @Test
    void defaultListIsNewestFirst() {
        store.put(item(1, 100, null, 1));
        store.put(item(2, 300, null, 1));
        store.put(item(3, 200, null, 1));
        assertEquals(List.of(did(2), did(3), did(1)), ids(store.query(store.compile(null))));
    }

    @Test
    void rangesAndOtherSortsWork() {
        for (int i = 0; i < 20; i++) store.put(item(i, i, i % 2 == 0 ? null : (long) (5_000 + i), 100 - i));
        Fcdsl f = new Fcdsl();
        f.addSort("size", "asc");
        f.addNewQuery().addNewRange().addNewFields("since").addGte("10");
        f.getQuery().setUnexists(new String[]{"expire"});
        assertEquals(List.of(did(18), did(16), did(14), did(12), did(10)), ids(store.query(store.compile(f))));

        Fcdsl bad = new Fcdsl();
        bad.addSort("owner", "asc");
        assertEquals(FcdslException.Reason.BAD_QUERY,
                assertThrows(FcdslException.class, () -> store.compile(bad)).getReason());
    }

    @Test
    void totalSizeFollowsEveryWriteAndSurvivesReopen() throws Exception {
        store.put(item(1, 1, null, 100));
        store.put(item(2, 1, null, 50));
        assertEquals(150, store.totalSize());
        store.put(item(1, 2, 999L, 100));          // re-put the same file: no double count
        assertEquals(150, store.totalSize());
        assertTrue(store.delete(did(2)));
        assertFalse(store.delete(did(2)));
        assertEquals(100, store.totalSize());

        store.close();
        store = DiskMetaStore.open(dir.resolve("meta"), 100_000);
        assertEquals(100, store.totalSize());
        assertEquals(1, store.count());
    }

    @Test
    void migrationCopiesAndMarks() {
        assertFalse(store.isMigrated());
        List<List<DiskItem>> pages = List.of(List.of(item(1, 5, null, 7), item(2, 6, 9L, 8)), List.of(item(3, 7, null, 9)));
        assertEquals(3, store.migrateFrom(pages.iterator()));
        assertEquals(3, store.migrateFrom(pages.iterator()));
        assertEquals(24, store.totalSize());
        assertTrue(store.isMigrated());
    }

    // ==================== through the component ====================

    @Test
    void diskComponentRecordsFilesAndListsThemWithoutEs() throws Exception {
        FapiServer server = mock(FapiServer.class);
        Settings settings = mock(Settings.class);
        when(server.getSettings()).thenReturn(settings);
        when(server.getBalanceManager()).thenReturn(mock(FapiBalanceManager.class));
        when(settings.getDbDir()).thenReturn(dir.resolve("db").toString());
        when(settings.getSid()).thenReturn("sid1");
        when(settings.getMainFid()).thenReturn("FMain");
        when(settings.getClient(any(ServiceType.class))).thenReturn(null);

        DiskComponent disk = new DiskComponent();
        disk.initialize(server);
        try {
            FapiDiskHandler handler = disk.getDiskHandler();
            DiskItem a = handler.storeFromBytes("alpha".getBytes(StandardCharsets.UTF_8), false, 30);
            handler.storeFromBytes("beta!".getBytes(StandardCharsets.UTF_8), true, 30);
            assertEquals(10, handler.getTotalStorageSize());
            assertNotNull(handler.getMetadata(a.getId()));

            FapiRequest list = FapiRequest.query("disk.list", new Fcdsl());
            list.setId("l");
            FapiResponse r = disk.handleRequest(list, "FPeer");
            assertEquals(FapiCode.SUCCESS, r.getCode(), r.getMessage());
            assertEquals(2, ((List<?>) r.getData()).size());

            Fcdsl bad = new Fcdsl();
            bad.addSort("nope", "asc");
            FapiRequest badList = FapiRequest.query("disk.list", bad);
            badList.setId("b");
            assertEquals(FapiCode.BAD_REQUEST, disk.handleRequest(badList, "FPeer").getCode());

            Fcdsl projected = new Fcdsl();
            projected.setFields(List.of("id"));
            FapiRequest proj = FapiRequest.query("disk.list", projected);
            proj.setId("p");
            List<?> rows = (List<?>) disk.handleRequest(proj, "FPeer").getData();
            assertEquals(1, ((com.google.gson.JsonObject) rows.get(0)).size());
        } finally {
            disk.close(1000);
        }
    }
}
