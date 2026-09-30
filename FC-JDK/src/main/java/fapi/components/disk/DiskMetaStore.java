package fapi.components.disk;

import data.apipData.Fcdsl;
import data.apipData.Sort;
import data.fcData.DiskItem;
import db.fcdsl.FcdslQuery;
import db.fcdsl.FcdslResult;
import db.fcdsl.FieldSchema;
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

import static constants.FieldNames.ID;
import static constants.FieldNames.SINCE;

/**
 * DISK file metadata (did, since, expire, size) in LevelDB, queried with FCDSL. The files
 * themselves stay on the file system.
 * <p>
 * Every sortable field has an index, so every {@code disk.list} sort is read in order. The
 * total size of all files, which DISK sync checks against its limit, is summed once at open
 * and then kept up to date on every write.
 */
public final class DiskMetaStore implements Closeable {

    static final String NAMESPACE = "disk";
    private static final byte[] MIGRATED_KEY = "disk-meta\0migratedFromEs".getBytes(StandardCharsets.UTF_8);

    public static final FieldSchema<DiskItem> SCHEMA = FieldSchema.builder(DiskItem.class, ID, DiskItem::getId)
            .longField(SINCE, DiskItem::getSince)
            .longField("expire", DiskItem::getExpire)
            .longField("size", DiskItem::getSize)
            .build();

    static final List<IndexDef> INDEXES = List.of(
            IndexDef.named("sinceAsc").asc(SINCE).build(),     // DiskSyncManager pages by since asc
            IndexDef.named("sinceDesc").desc(SINCE).build(),   // disk.list's default
            IndexDef.named("expireAsc").asc("expire").build(),
            IndexDef.named("sizeAsc").asc("size").build());

    private final DB ownedDb;
    private final SortedKv kv;
    private final IndexedCollection<DiskItem> items;
    private long totalSize;

    private DiskMetaStore(DB ownedDb, SortedKv kv, long maxExamined) {
        this.ownedDb = ownedDb;
        this.kv = kv;
        this.items = new IndexedCollection<>(kv, NAMESPACE, SCHEMA, INDEXES,
                new IndexedCollection.Options<DiskItem>().maxExamined(maxExamined));
        this.totalSize = sumSizes();
    }

    public static DiskMetaStore open(Path dir, long maxExamined) throws IOException {
        Files.createDirectories(dir);
        DB db = Iq80DBFactory.factory.open(dir.toFile(), new Options().createIfMissing(true));
        return new DiskMetaStore(db, new LevelDbKv(db), maxExamined);
    }

    /** A store on a key-value store the caller owns and closes. */
    public static DiskMetaStore on(SortedKv kv, long maxExamined) {
        return new DiskMetaStore(null, kv, maxExamined);
    }

    private long sumSizes() {
        long sum = 0;
        List<String> after = null;
        while (true) {
            Fcdsl f = new Fcdsl();
            f.addSort(ID, "asc");
            f.addSize(3000);
            if (after != null) f.setAfter(after);
            FcdslResult<DiskItem> page = items.query(f, null, null);
            for (DiskItem d : page.getItems()) if (d.getSize() != null) sum += d.getSize();
            if (page.getItems().size() < 3000) return sum;
            after = page.getLast();
        }
    }

    public DiskItem get(String did) {
        return items.get(did);
    }

    /** Store or replace the metadata of one file. */
    public synchronized void put(DiskItem item) {
        DiskItem old = items.get(item.getId());
        items.put(item);
        totalSize += size(item) - size(old);
    }

    public synchronized boolean delete(String did) {
        DiskItem old = items.get(did);
        boolean removed = items.delete(did);
        if (removed) totalSize -= size(old);
        return removed;
    }

    private static long size(DiskItem d) {
        return d == null || d.getSize() == null ? 0 : d.getSize();
    }

    public long count() {
        return items.count();
    }

    /** Bytes of all stored files, by their recorded sizes. */
    public synchronized long totalSize() {
        return totalSize;
    }

    /**
     * One page of {@code disk.list}. Without a sort, newest first ({@code since desc}), as
     * before; the id is always the last sort key.
     *
     * @throws db.fcdsl.FcdslException for a query the store rejects (400 or 501)
     */
    public FcdslQuery<DiskItem> compile(Fcdsl request) {
        List<Sort> byNewest = new ArrayList<>(List.of(new Sort(SINCE, "desc")));
        return items.compile(request, null, byNewest);
    }

    public FcdslResult<DiskItem> query(FcdslQuery<DiskItem> q) {
        return items.query(q);
    }

    public boolean isMigrated() {
        return kv.get(MIGRATED_KEY) != null;
    }

    /** Copy items in from the old store, then mark the store migrated. Safe to run again. */
    public long migrateFrom(Iterator<List<DiskItem>> pages) {
        long n = 0;
        while (pages.hasNext()) {
            for (DiskItem item : pages.next()) {
                if (item == null || item.getId() == null || item.getId().isEmpty()) continue;
                put(item);
                n++;
            }
        }
        markMigrated();
        return n;
    }

    public void markMigrated() {
        kv.write(List.of(SortedKv.Op.put(MIGRATED_KEY, new byte[]{1})));
    }

    @Override
    public void close() throws IOException {
        if (ownedDb != null) ownedDb.close();
    }
}
