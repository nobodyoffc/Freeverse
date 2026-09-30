package fapi.components.dock;

import data.apipData.Fcdsl;
import data.fcData.DockItem;
import db.fcdsl.FcdslException;
import db.fcdsl.FcdslResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The DOCK store, driven the way the Freer app drives dock.fetch. */
class DockStoreTest {

    static final long HEIGHT = 1_000;

    @TempDir
    Path dir;
    DockStore store;

    @BeforeEach
    void open() throws Exception {
        store = DockStore.open(dir.resolve("dock"), 100_000);
    }

    @AfterEach
    void close() throws Exception {
        store.close();
    }

    static DockItem item(String id, List<String> recipients, long createTime, long expireHeight) {
        DockItem d = new DockItem(id, "FSender", recipients, 10L, HEIGHT - 10, expireHeight);
        d.setCreateTime(createTime);
        d.setDataBase64("AAAA");
        return d;
    }

    static List<String> ids(FcdslResult<DockItem> r) {
        List<String> out = new ArrayList<>();
        for (DockItem d : r.getItems()) out.add(d.getId());
        return out;
    }

    /** DockFetchScheduler: createTime asc, id asc, a page size, and the cursor it saved. */
    static Fcdsl scheduler(List<String> cursor, int size) {
        Fcdsl f = new Fcdsl();
        f.addSort("createTime", "asc");
        f.addSort("id", "asc");
        f.setSize(String.valueOf(size));
        if (cursor != null) f.setAfter(cursor);
        return f;
    }

    @Test
    void schedulerPagesThroughEverythingWithEsShapedCursors() {
        for (int i = 0; i < 25; i++) store.put(item(String.format("m%02d", i), List.of("FMe"), 100 + i / 2, HEIGHT + 50));
        List<String> got = new ArrayList<>();
        List<String> cursor = null;
        while (true) {
            FcdslResult<DockItem> r = store.find(scheduler(cursor, 10), List.of("FMe"), HEIGHT, 20, 50);
            got.addAll(ids(r));
            if (r.getItems().size() < 10) break;
            cursor = r.getLast();
            // ES sorted by createTime, id, and then the id DOCK appended: three values
            assertEquals(3, cursor.size(), "cursor " + cursor);
            assertEquals(cursor.get(1), cursor.get(2));
        }
        assertEquals(25, got.size());
        assertEquals("m00", got.get(0));
        assertEquals("m24", got.get(24));
    }

    @Test
    void aCursorSavedBeforeTheMigrationResumesInPlace() {
        for (int i = 0; i < 6; i++) store.put(item("x" + i, List.of("FMe"), 200 + i, HEIGHT + 50));
        // what ES handed out for item x2: [createTime, id, id] as strings
        List<String> saved = List.of("202", "x2", "x2");
        assertEquals(List.of("x3", "x4", "x5"), ids(store.find(scheduler(saved, 10), List.of("FMe"), HEIGHT, 20, 50)));
    }

    @Test
    void newcomerBoardGetsOnlyNewerItemsNewestFirstAndReadsLittle() {
        List<DockItem> all = new ArrayList<>();
        for (int i = 0; i < 3000; i++) all.add(item("b" + i, List.of("nobody", "FUser" + (i % 40)), 10_000 + i, HEIGHT + 50));
        for (DockItem d : all) store.put(d);
        Fcdsl f = new Fcdsl();
        f.addSort("createTime", "desc");
        f.setSize("30");
        f.addNewQuery().addNewRange().addNewFields("createTime").addGt(String.valueOf(10_000 + 2990));
        FcdslResult<DockItem> r = store.find(f, List.of("nobody"), HEIGHT, 20, 50);
        assertEquals(9, r.getItems().size(), "the since-filter now reaches the server");
        assertEquals("b2999", r.getItems().get(0).getId());
        assertTrue(r.getExamined() <= 10, "examined " + r.getExamined());
        assertEquals(2, r.getLast().size(), "[createTime, id]");
    }

    @Test
    void teamRoomAndSquareDefaultToOldestFirstAndCapTheSize() {
        for (int i = 0; i < 80; i++) store.put(item("t" + i, List.of("TeamA"), 500 + i, HEIGHT + 50));
        Fcdsl f = new Fcdsl();
        f.setSize("50");
        FcdslResult<DockItem> r = store.find(f, List.of("TeamA"), HEIGHT, 20, 50);
        assertEquals(50, r.getItems().size());
        assertEquals("t0", r.getItems().get(0).getId());
        f.setSize("500");
        assertEquals(50, store.find(f, List.of("TeamA"), HEIGHT, 20, 50).getItems().size(), "fetch caps at 50");
        assertEquals(20, store.find(new Fcdsl(), List.of("TeamA"), HEIGHT, 20, 50).getItems().size());
        assertEquals(20, store.find(null, List.of("TeamA"), HEIGHT, 20, 50).getItems().size());
    }

    @Test
    void severalRecipientsAndExpiry() {
        store.put(item("both", List.of("A", "B"), 3, HEIGHT + 5));
        store.put(item("a", List.of("A"), 1, HEIGHT + 5));
        store.put(item("b", List.of("B"), 2, HEIGHT + 5));
        store.put(item("gone", List.of("A"), 0, HEIGHT));        // expires at HEIGHT
        store.put(item("other", List.of("C"), 4, HEIGHT + 6));
        assertEquals(List.of("a", "b", "both"), ids(store.find(null, List.of("A", "B"), HEIGHT, 20, 50)));

        assertEquals(1, store.deleteExpired(HEIGHT));
        assertNull(store.get("gone"));
        assertEquals(4, store.count());
        assertEquals(3, store.deleteExpired(HEIGHT + 5));
        assertEquals(List.of("other"), ids(store.find(null, List.of("C"), HEIGHT, 20, 50)));
    }

    @Test
    void wrongQueriesAreRejected() {
        Fcdsl f = new Fcdsl();
        f.addSort("nope", "asc");
        FcdslException e = assertThrows(FcdslException.class, () -> store.find(f, List.of("A"), HEIGHT, 20, 50));
        assertEquals(FcdslException.Reason.BAD_QUERY, e.getReason());
        Fcdsl twoValues = scheduler(List.of("1", "x"), 10);
        assertThrows(FcdslException.class, () -> store.find(twoValues, List.of("A"), HEIGHT, 20, 50),
                "the scheduler's sort takes three cursor values");
    }

    @Test
    void migrationCopiesPagesCanRunAgainAndSurvivesReopen() throws Exception {
        assertFalse(store.isMigrated());
        List<List<DockItem>> pages = List.of(
                List.of(item("p1", List.of("A"), 1, HEIGHT + 9), item("p2", List.of("A"), 2, HEIGHT + 9)),
                List.of(item("p3", List.of("B"), 3, HEIGHT + 9)));
        assertEquals(3, store.migrateFrom(pages.iterator()));
        assertEquals(3, store.migrateFrom(pages.iterator()), "a rerun overwrites by id");
        assertEquals(3, store.count());
        assertTrue(store.isMigrated());

        store.close();
        store = DockStore.open(dir.resolve("dock"), 100_000);
        assertTrue(store.isMigrated());
        assertEquals(3, store.count());
        assertEquals(List.of("p1", "p2"), ids(store.find(null, List.of("A"), HEIGHT, 20, 50)));
        assertEquals("AAAA", store.get("p3").getDataBase64());
    }
}
