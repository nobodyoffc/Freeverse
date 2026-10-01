package fapi.components.dock;

import com.google.gson.Gson;
import constants.FieldNames;
import data.apipData.FcQuery;
import data.apipData.Fcdsl;
import data.apipData.Sort;
import data.fcData.DockItem;
import db.fcdsl.FcdslQuery;
import db.fcdsl.FcdslResult;
import db.fcdsl.FieldSchema;
import db.fcdsl.FieldType;
import db.fcdsl.IndexDef;
import db.fcdsl.IndexedCollection;
import db.fcdsl.LevelDbKv;
import db.fcdsl.SortedKv;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.impl.Iq80DBFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * DOCK items in LevelDB, queried with FCDSL. Each item keeps its Base64 data with it, as the
 * ES document did.
 * <p>
 * Queries keep ES's contract so apps see no change:
 * <ul>
 *   <li>The sort is the request's, or {@code createTime asc}, and then always {@code id asc}
 *       (even after a sort that already ends in id). Cursors therefore have the same number of
 *       values as before, and cursors the apps saved before the migration resume in place.</li>
 *   <li>Only unexpired items addressed to one of the given recipients are found.</li>
 * </ul>
 */
public final class DockStore implements Closeable {

    static final String NAMESPACE = "dock";
    private static final byte[] MIGRATED_KEY = "dock-meta\0migratedFromEs".getBytes(StandardCharsets.UTF_8);
    private static final Gson GSON = new Gson();
    private static final int SWEEP_PAGE = 1000;

    public static final FieldSchema<DockItem> SCHEMA = FieldSchema.builder(DockItem.class, FieldNames.ID, DockItem::getId)
            .keyword(FieldNames.SENDER, DockItem::getSender)
            .keywords(FieldNames.RECIPIENTS, DockItem::getRecipients)
            .longField(FieldNames.SIZE, DockItem::getSize)
            .longField(FieldNames.CREATE_HEIGHT, DockItem::getCreateHeight)
            .longField(FieldNames.EXPIRE_HEIGHT, DockItem::getExpireHeight)
            .longField(FieldNames.CREATE_TIME, DockItem::getCreateTime)
            .longField(FieldNames.MAX_DAYS, DockItem::getMaxDays)
            .longField(FieldNames.STORAGE_FEE, DockItem::getStorageFee)
            .longField(FieldNames.INGRESS_FEE, DockItem::getIngressFee)
            .field(FieldNames.DATA_TYPE, FieldType.KEYWORD, DockItem::getDataType)
            .build();

    static final List<IndexDef> INDEXES = List.of(
            // DockFetchScheduler pages forward in time; NewcomerBoard reads the newest first
            IndexDef.named("rcptTimeAsc").eq(FieldNames.RECIPIENTS).asc(FieldNames.CREATE_TIME).build(),
            IndexDef.named("rcptTimeDesc").eq(FieldNames.RECIPIENTS).desc(FieldNames.CREATE_TIME).build(),
            IndexDef.named("expire").asc(FieldNames.EXPIRE_HEIGHT).build());

    private final DB ownedDb;
    private final SortedKv kv;
    private final IndexedCollection<DockItem> items;

    private DockStore(DB ownedDb, SortedKv kv, long maxExamined) {
        this.ownedDb = ownedDb;
        this.kv = kv;
        this.items = new IndexedCollection<>(kv, NAMESPACE, SCHEMA, INDEXES,
                new IndexedCollection.Options<DockItem>().maxExamined(maxExamined));
    }

    /** Open (or create) the store in its own LevelDB directory. */
    public static DockStore open(Path dir, long maxExamined) throws IOException {
        Files.createDirectories(dir);
        DB db = Iq80DBFactory.factory.open(dir.toFile(), new Options().createIfMissing(true));
        return new DockStore(db, new LevelDbKv(db), maxExamined);
    }

    /** A store on a key-value store the caller owns and closes. */
    public static DockStore on(SortedKv kv, long maxExamined) {
        return new DockStore(null, kv, maxExamined);
    }

    public void put(DockItem item) {
        items.put(item);
    }

    public DockItem get(String id) {
        return items.get(id);
    }

    public boolean delete(String id) {
        return items.delete(id);
    }

    public long count() {
        return items.count();
    }

    /**
     * Unexpired items addressed to any of {@code recipientIds}, one page of the request.
     *
     * @throws db.fcdsl.FcdslException for a query the store rejects (400 or 501)
     */
    public FcdslResult<DockItem> find(Fcdsl request, List<String> recipientIds, long currentHeight,
                                      int defaultSize, int maxSize) {
        Fcdsl f = request == null ? new Fcdsl() : GSON.fromJson(GSON.toJson(request), Fcdsl.class);
        List<Sort> sort = new ArrayList<>();
        if (f.getSort() != null && !f.getSort().isEmpty()) sort.addAll(f.getSort());
        else sort.add(new Sort(FieldNames.CREATE_TIME, "asc"));
        sort.add(new Sort(FieldNames.ID, "asc"));
        f.setSort(new ArrayList<>(sort));

        FcQuery toRecipients = new FcQuery();
        toRecipients.addNewTerms().addNewFields(FieldNames.RECIPIENTS).addNewValues(recipientIds.toArray(new String[0]));
        FcQuery unexpired = new FcQuery();
        unexpired.addNewRange().addNewFields(FieldNames.EXPIRE_HEIGHT).addGt(String.valueOf(currentHeight));

        FcdslQuery<DockItem> q = items.compile(f, List.of(toRecipients, unexpired), null, defaultSize, maxSize);
        return items.query(q);
    }

    /** Delete every item expired at {@code currentHeight} (expireHeight ≤ it). Returns how many. */
    public long deleteExpired(long currentHeight) {
        long deleted = 0;
        while (true) {
            Fcdsl f = new Fcdsl();
            f.addSort(FieldNames.EXPIRE_HEIGHT, "asc");
            f.addSize(SWEEP_PAGE);
            FcQuery expired = new FcQuery();
            expired.addNewRange().addNewFields(FieldNames.EXPIRE_HEIGHT).addLte(String.valueOf(currentHeight));
            FcdslResult<DockItem> page = items.query(f, List.of(expired), null);
            List<String> ids = new ArrayList<>();
            for (DockItem item : page.getItems()) ids.add(item.getId());
            deleted += items.deleteAll(ids);
            if (page.getItems().size() < SWEEP_PAGE) return deleted;
        }
    }

    public boolean isMigrated() {
        return kv.get(MIGRATED_KEY) != null;
    }

    /**
     * Copy items in from the old store, page by page, then mark the store migrated. Items already
     * here are overwritten by id, so a migration cut short can simply run again.
     *
     * @return how many items were copied
     */
    public long migrateFrom(Iterator<List<DockItem>> pages) {
        long n = 0;
        while (pages.hasNext()) {
            List<DockItem> page = new ArrayList<>();
            for (DockItem item : pages.next()) {
                if (item != null && item.getId() != null && !item.getId().isEmpty()) page.add(item);
            }
            items.putAll(page);
            n += page.size();
        }
        markMigrated();
        return n;
    }

    /** Record that there is nothing (more) to migrate. */
    public void markMigrated() {
        kv.write(List.of(SortedKv.Op.put(MIGRATED_KEY, new byte[]{1})));
    }

    @Override
    public void close() throws IOException {
        if (ownedDb != null) ownedDb.close();
    }
}
