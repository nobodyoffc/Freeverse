package db.fcdsl;

import com.google.gson.Gson;
import data.apipData.FcQuery;
import data.apipData.Fcdsl;
import data.apipData.Sort;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Items of one class stored in a {@link SortedKv} under a namespace, with secondary indexes,
 * queried with FCDSL.
 * <p>
 * Layout, all under {@code <namespace> 0x00}:
 * <ul>
 *   <li>{@code d <id>} → the item, encoded by the codec (JSON by default)</li>
 *   <li>{@code x <index> 0x00 <components> <id>} → empty, one per index entry</li>
 *   <li>{@code m <name>} → metadata: item count, and each index's signature</li>
 * </ul>
 * Writes are serialized and each is one atomic batch. A query reads one snapshot.
 * <p>
 * A query takes one of four paths, best first:
 * <ol>
 *   <li><b>ids</b>: the listed items are loaded, filtered and sorted.</li>
 *   <li><b>ordered index</b>: an index whose order is the query's sort is read in order, from the
 *       cursor or the range start, and stops as soon as the page is full.</li>
 *   <li><b>narrowing index</b>: an index pinned by equality or bounded by a range gives the
 *       candidates, which are then filtered and sorted in memory.</li>
 *   <li><b>primary</b>: every item, read in id order (streamed when the sort is by id alone).</li>
 * </ol>
 * Every entry read counts toward {@link Options#maxExamined}. Past it the query fails with
 * {@link FcdslException.Reason#TOO_BROAD}; it never returns a short page that is not the end.
 */
public final class IndexedCollection<T> {

    public static final class Options<T> {
        int defaultSize = 20;
        int maxSize = 3000;
        long maxExamined = 100_000;
        int maxPartitions = 1000;
        Function<T, byte[]> encoder;
        Function<byte[], T> decoder;

        public Options<T> defaultSize(int v) { defaultSize = v; return this; }

        public Options<T> maxSize(int v) { maxSize = v; return this; }

        /** Stored entries one query may read before it fails as TOO_BROAD. */
        public Options<T> maxExamined(long v) { maxExamined = v; return this; }

        /** Most index partitions (combinations of pinned equality values) one query may merge. */
        public Options<T> maxPartitions(int v) { maxPartitions = v; return this; }

        public Options<T> codec(Function<T, byte[]> encoder, Function<byte[], T> decoder) {
            this.encoder = encoder;
            this.decoder = decoder;
            return this;
        }
    }

    private static final byte[] EMPTY = new byte[0];
    private static final int FLUSH_OPS = 5000;
    private static final String META_COUNT = "count";
    private static final String META_INDEX = "idx:";

    /** An index resolved against the schema. */
    private final class Idx {
        final IndexDef def;
        final byte[] prefix;
        final List<FieldSchema.Field<T>> eqFields = new ArrayList<>();
        final List<FieldSchema.Field<T>> sortFields = new ArrayList<>();
        final List<Boolean> sortDesc = new ArrayList<>();

        Idx(IndexDef def) {
            this.def = def;
            this.prefix = indexPrefix(def.name);
            for (String f : def.eq) eqFields.add(requireForIndex(def, f));
            for (IndexDef.Comp c : def.sort) {
                FieldSchema.Field<T> field = requireForIndex(def, c.field);
                if (field.multi()) {
                    throw new IllegalArgumentException("Index " + def.name + ": sort field " + c.field + " is multi-valued");
                }
                sortFields.add(field);
                sortDesc.add(c.desc);
            }
        }
    }

    /** How a query will read an index. */
    private final class Plan {
        final Idx idx;
        final List<byte[]> partitions;
        final FcdslQuery.Bounds bounds;
        final boolean ordered;

        Plan(Idx idx, List<byte[]> partitions, FcdslQuery.Bounds bounds, boolean ordered) {
            this.idx = idx;
            this.partitions = partitions;
            this.bounds = bounds;
            this.ordered = ordered;
        }
    }

    private static final class Budget {
        final long max;
        final String hint;
        long used;

        Budget(long max, String hint) {
            this.max = max;
            this.hint = hint;
        }

        void charge() {
            if (++used > max) {
                throw new FcdslException(FcdslException.Reason.TOO_BROAD,
                        "Query reads more than " + max + " stored items; narrow it" + hint);
            }
        }
    }

    private final SortedKv kv;
    private final FieldSchema<T> schema;
    private final Options<T> options;
    private final Function<T, byte[]> encoder;
    private final Function<byte[], T> decoder;
    private final byte[] ns;
    private final byte[] dataPrefix;
    private final List<Idx> indexes = new ArrayList<>();
    private long count;

    /**
     * Open a collection. Indexes that are new or changed since the last open are (re)built, and
     * stored indexes no longer declared are dropped.
     *
     * @param namespace distinguishes this collection from other data in the same store; no NUL
     */
    public IndexedCollection(SortedKv kv, String namespace, FieldSchema<T> schema,
                             List<IndexDef> indexDefs, Options<T> options) {
        if (namespace == null || namespace.isEmpty() || namespace.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Bad namespace: " + namespace);
        }
        this.kv = kv;
        this.schema = schema;
        this.options = options != null ? options : new Options<>();
        if (this.options.encoder != null) {
            this.encoder = this.options.encoder;
            this.decoder = this.options.decoder;
        } else {
            Gson gson = new Gson();
            Class<T> cls = schema.itemClass();
            this.encoder = item -> gson.toJson(item).getBytes(StandardCharsets.UTF_8);
            this.decoder = bytes -> gson.fromJson(new String(bytes, StandardCharsets.UTF_8), cls);
        }
        this.ns = KeyCodec.concat(namespace.getBytes(StandardCharsets.UTF_8), new byte[]{0});
        this.dataPrefix = KeyCodec.concat(ns, new byte[]{'d'});
        Set<String> names = new HashSet<>();
        if (indexDefs != null) {
            for (IndexDef def : indexDefs) {
                if (!names.add(def.name)) throw new IllegalArgumentException("Index declared twice: " + def.name);
                indexes.add(new Idx(def));
            }
        }
        open();
    }

    private FieldSchema.Field<T> requireForIndex(IndexDef def, String name) {
        FieldSchema.Field<T> f = schema.field(name);
        if (f == null) throw new IllegalArgumentException("Index " + def.name + ": unknown field " + name);
        return f;
    }

    // ==================== keys ====================

    private byte[] dataKey(String id) {
        return KeyCodec.concat(dataPrefix, id.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] metaKey(String name) {
        return KeyCodec.concat(ns, new byte[]{'m'}, name.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] indexPrefix(String name) {
        return KeyCodec.concat(ns, new byte[]{'x'}, name.getBytes(StandardCharsets.UTF_8), new byte[]{0});
    }

    /** Every entry the item has in the index: one per combination of equality values. */
    private List<byte[]> indexKeys(Idx idx, T item) {
        List<byte[]> heads = new ArrayList<>();
        heads.add(idx.prefix);
        for (FieldSchema.Field<T> f : idx.eqFields) {
            List<Object> vals = schema.values(f, item);
            if (vals.isEmpty()) {
                vals = new ArrayList<>();
                vals.add(null);
            }
            Set<Object> distinct = new java.util.LinkedHashSet<>(vals);
            List<byte[]> next = new ArrayList<>(heads.size() * distinct.size());
            for (byte[] h : heads) {
                for (Object v : distinct) next.add(KeyCodec.concat(h, KeyCodec.encode(f.type(), v, false)));
            }
            heads = next;
        }
        ByteArrayOutputStream tail = new ByteArrayOutputStream();
        for (int i = 0; i < idx.sortFields.size(); i++) {
            FieldSchema.Field<T> f = idx.sortFields.get(i);
            KeyCodec.encode(tail, f.type(), schema.value(f, item), idx.sortDesc.get(i));
        }
        KeyCodec.encode(tail, FieldType.KEYWORD, schema.idOf(item), false);
        byte[] t = tail.toByteArray();
        List<byte[]> out = new ArrayList<>(heads.size());
        for (byte[] h : heads) out.add(KeyCodec.concat(h, t));
        return out;
    }

    private Set<ByteBuffer> allIndexKeys(T item) {
        Set<ByteBuffer> out = new HashSet<>();
        if (item == null) return out;
        for (Idx idx : indexes) for (byte[] k : indexKeys(idx, item)) out.add(ByteBuffer.wrap(k));
        return out;
    }

    private static long readLong(byte[] b) {
        return ByteBuffer.wrap(b).getLong();
    }

    private static byte[] longBytes(long v) {
        return ByteBuffer.allocate(8).putLong(v).array();
    }

    // ==================== open and index maintenance ====================

    private synchronized void open() {
        List<Idx> toBuild = new ArrayList<>();
        for (Idx idx : indexes) {
            byte[] stored = kv.get(metaKey(META_INDEX + idx.def.name));
            String sig = idx.def.signature();
            if (stored == null || !sig.equals(new String(stored, StandardCharsets.UTF_8))) toBuild.add(idx);
        }
        dropUndeclaredIndexes();
        byte[] storedCount = kv.get(metaKey(META_COUNT));
        if (storedCount != null && toBuild.isEmpty()) {
            count = readLong(storedCount);
            return;
        }
        for (Idx idx : toBuild) deletePrefix(idx.prefix);

        List<SortedKv.Op> ops = new ArrayList<>();
        long n = 0;
        try (SortedKv.Reader r = kv.openReader(); SortedKv.Cursor c = r.seek(dataPrefix)) {
            for (; c.valid() && KeyCodec.startsWith(c.key(), dataPrefix); c.next()) {
                n++;
                if (toBuild.isEmpty()) continue;
                T item = decoder.apply(c.value());
                for (Idx idx : toBuild) for (byte[] k : indexKeys(idx, item)) ops.add(SortedKv.Op.put(k, EMPTY));
                if (ops.size() >= FLUSH_OPS) {
                    kv.write(ops);
                    ops = new ArrayList<>();
                }
            }
        }
        for (Idx idx : toBuild) {
            ops.add(SortedKv.Op.put(metaKey(META_INDEX + idx.def.name),
                    idx.def.signature().getBytes(StandardCharsets.UTF_8)));
        }
        ops.add(SortedKv.Op.put(metaKey(META_COUNT), longBytes(n)));
        kv.write(ops);
        count = n;
    }

    private void dropUndeclaredIndexes() {
        byte[] metaIdx = metaKey(META_INDEX);
        Set<String> declared = new HashSet<>();
        for (Idx idx : indexes) declared.add(idx.def.name);
        List<String> stale = new ArrayList<>();
        try (SortedKv.Reader r = kv.openReader(); SortedKv.Cursor c = r.seek(metaIdx)) {
            for (; c.valid() && KeyCodec.startsWith(c.key(), metaIdx); c.next()) {
                String name = new String(c.key(), metaIdx.length, c.key().length - metaIdx.length, StandardCharsets.UTF_8);
                if (!declared.contains(name)) stale.add(name);
            }
        }
        for (String name : stale) {
            deletePrefix(indexPrefix(name));
            kv.write(List.of(SortedKv.Op.delete(metaKey(META_INDEX + name))));
        }
    }

    private void deletePrefix(byte[] prefix) {
        List<SortedKv.Op> ops = new ArrayList<>();
        try (SortedKv.Reader r = kv.openReader(); SortedKv.Cursor c = r.seek(prefix)) {
            for (; c.valid() && KeyCodec.startsWith(c.key(), prefix); c.next()) {
                ops.add(SortedKv.Op.delete(c.key()));
                if (ops.size() >= FLUSH_OPS) {
                    kv.write(ops);
                    ops = new ArrayList<>();
                }
            }
        }
        kv.write(ops);
    }

    // ==================== writes ====================

    public void put(T item) {
        putAll(List.of(item));
    }

    /** Store items, replacing any with the same id, in one atomic batch. */
    public synchronized void putAll(Collection<? extends T> items) {
        List<SortedKv.Op> ops = new ArrayList<>();
        Map<String, T> written = new HashMap<>();
        long added = 0;
        for (T item : items) {
            String id = schema.idOf(item);
            if (id == null || id.isEmpty() || id.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("Bad item id: " + id);
            }
            T old;
            if (written.containsKey(id)) {
                old = written.get(id);
            } else {
                byte[] stored = kv.get(dataKey(id));
                old = stored == null ? null : decoder.apply(stored);
                if (old == null) added++;
            }
            Set<ByteBuffer> oldKeys = allIndexKeys(old);
            Set<ByteBuffer> newKeys = allIndexKeys(item);
            for (ByteBuffer k : oldKeys) if (!newKeys.contains(k)) ops.add(SortedKv.Op.delete(k.array()));
            for (ByteBuffer k : newKeys) if (!oldKeys.contains(k)) ops.add(SortedKv.Op.put(k.array(), EMPTY));
            ops.add(SortedKv.Op.put(dataKey(id), encoder.apply(item)));
            written.put(id, item);
        }
        if (added != 0) ops.add(SortedKv.Op.put(metaKey(META_COUNT), longBytes(count + added)));
        kv.write(ops);
        count += added;
    }

    public boolean delete(String id) {
        return deleteAll(List.of(id)) > 0;
    }

    /** Remove items by id in one atomic batch. Returns how many existed. */
    public synchronized int deleteAll(Collection<String> ids) {
        List<SortedKv.Op> ops = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int removed = 0;
        for (String id : ids) {
            if (id == null || !seen.add(id)) continue;
            byte[] stored = kv.get(dataKey(id));
            if (stored == null) continue;
            for (ByteBuffer k : allIndexKeys(decoder.apply(stored))) ops.add(SortedKv.Op.delete(k.array()));
            ops.add(SortedKv.Op.delete(dataKey(id)));
            removed++;
        }
        if (removed == 0) return 0;
        ops.add(SortedKv.Op.put(metaKey(META_COUNT), longBytes(count - removed)));
        kv.write(ops);
        count -= removed;
        return removed;
    }

    // ==================== reads ====================

    public T get(String id) {
        if (id == null) return null;
        byte[] b = kv.get(dataKey(id));
        return b == null ? null : decoder.apply(b);
    }

    /** The stored items among the ids, in the given order. */
    public Map<String, T> getAll(Collection<String> ids) {
        Map<String, T> out = new LinkedHashMap<>();
        try (SortedKv.Reader r = kv.openReader()) {
            for (String id : ids) {
                if (id == null || out.containsKey(id)) continue;
                T item = load(r, id);
                if (item != null) out.put(id, item);
            }
        }
        return out;
    }

    public long count() {
        return count;
    }

    public FieldSchema<T> schema() {
        return schema;
    }

    /** Check a request against this collection's schema and size limits. */
    public FcdslQuery<T> compile(Fcdsl fcdsl, List<? extends FcQuery> extra, List<Sort> defaultSort) {
        return FcdslQuery.compile(schema, fcdsl, extra, defaultSort, options.defaultSize, options.maxSize);
    }

    /** Compile and run. See {@link FcdslQuery#compile} for the parameters. */
    public FcdslResult<T> query(Fcdsl fcdsl, List<? extends FcQuery> extra, List<Sort> defaultSort) {
        return query(compile(fcdsl, extra, defaultSort));
    }

    public FcdslResult<T> query(FcdslQuery<T> q) {
        String hint = ", e.g. with a range on " + q.sort.get(0).field() + " or an equality on an indexed field";
        Budget budget = new Budget(options.maxExamined, hint);
        try (SortedKv.Reader r = kv.openReader()) {
            if (q.ids != null) return idsPath(r, q, budget);
            Plan plan = choosePlan(q);
            if (plan != null && plan.ordered) return orderedPath(r, q, plan, budget);
            if (plan == null && isIdOrder(q)) return primaryOrderedPath(r, q, budget);
            return memoryPath(r, q, plan, budget);
        }
    }

    private T load(SortedKv.Reader r, String id) {
        byte[] b = r.get(dataKey(id));
        return b == null ? null : decoder.apply(b);
    }

    // ==================== planning ====================

    private boolean isIdOrder(FcdslQuery<T> q) {
        return q.sort.size() == 1 && !q.sort.get(0).desc && q.sort.get(0).field().equals(schema.idField());
    }

    /** The values the query pins a field to (terms/equals on that field alone), or null. */
    private Set<Object> pinned(FcdslQuery<T> q, FieldSchema.Field<T> field) {
        Set<Object> out = null;
        for (FcdslQuery.Cond<T> c : q.must) {
            if (c instanceof FcdslQuery.TermsCond<T> t && t.singleField() == field) {
                Set<Object> vals = t.values.get(field);
                if (out == null) {
                    out = new java.util.LinkedHashSet<>(vals);
                } else {
                    out.retainAll(vals);
                }
            }
        }
        return out;
    }

    private FcdslQuery.Bounds rangeOn(FcdslQuery<T> q, FieldSchema.Field<T> field) {
        for (FcdslQuery.Cond<T> c : q.must) {
            if (c instanceof FcdslQuery.RangeCond<T> rc && rc.singleField() == field) return rc.bounds.get(field);
        }
        return null;
    }

    private boolean sortMatches(Idx idx, FcdslQuery<T> q) {
        if (q.sort.size() != idx.sortFields.size() + 1) return false;
        for (int i = 0; i < idx.sortFields.size(); i++) {
            FcdslQuery.SortKey<T> k = q.sort.get(i);
            if (k.field != idx.sortFields.get(i) || k.desc != idx.sortDesc.get(i)) return false;
        }
        FcdslQuery.SortKey<T> last = q.sort.get(q.sort.size() - 1);
        return !last.desc && last.field().equals(schema.idField());
    }

    private Plan choosePlan(FcdslQuery<T> q) {
        Plan best = null;
        int bestScore = 0;
        for (Idx idx : indexes) {
            List<byte[]> partitions = new ArrayList<>();
            partitions.add(idx.prefix);
            boolean usable = true;
            for (FieldSchema.Field<T> f : idx.eqFields) {
                Set<Object> vals = pinned(q, f);
                if (vals == null || (long) partitions.size() * vals.size() > options.maxPartitions) {
                    usable = false;
                    break;
                }
                List<byte[]> next = new ArrayList<>();
                for (byte[] p : partitions) {
                    for (Object v : vals) next.add(KeyCodec.concat(p, KeyCodec.encode(f.type(), v, false)));
                }
                partitions = next;
            }
            if (!usable) continue;
            boolean ordered = sortMatches(idx, q);
            FcdslQuery.Bounds bounds = idx.sortFields.isEmpty() ? null : rangeOn(q, idx.sortFields.get(0));
            // equality narrows most; reading in order lets the page stop early; a range narrows too
            int score = (idx.eqFields.isEmpty() ? 0 : 4) + (ordered ? 2 : 0) + (bounds != null ? 1 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = new Plan(idx, partitions, bounds, ordered);
            }
        }
        return best;
    }

    // ==================== reading an index ====================

    /** Where a read of the plan's partitions starts, relative to the partition prefix. */
    private byte[] rangeStart(Plan p) {
        if (p.bounds == null) return EMPTY;
        FieldSchema.Field<T> f = p.idx.sortFields.get(0);
        boolean desc = p.idx.sortDesc.get(0);
        Object from = desc ? p.bounds.upper : p.bounds.lower;
        return from == null ? EMPTY : KeyCodec.encode(f.type(), from, desc);
    }

    /** The cursor as an index suffix: sort components, then the id. */
    private byte[] cursorSuffix(Plan p, FcdslQuery<T> q) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < p.idx.sortFields.size(); i++) {
            KeyCodec.encode(out, p.idx.sortFields.get(i).type(), q.after.get(i), p.idx.sortDesc.get(i));
        }
        KeyCodec.encode(out, FieldType.KEYWORD, q.after.get(q.after.size() - 1), false);
        return out.toByteArray();
    }

    /** Whether the entry is past the plan's range on the first sort component. */
    private boolean pastRange(Plan p, byte[] key, int prefixLen) {
        if (p.bounds == null) return false;
        boolean desc = p.idx.sortDesc.get(0);
        Object v = new KeyCodec.Reader(key, prefixLen).read(p.idx.sortFields.get(0).type(), desc);
        if (v == null) return true; // missing values sort last and never satisfy a range
        return desc ? p.bounds.belowLower(v) : p.bounds.aboveUpper(v);
    }

    private String idOfEntry(Plan p, byte[] key, int prefixLen) {
        KeyCodec.Reader reader = new KeyCodec.Reader(key, prefixLen);
        for (int i = 0; i < p.idx.sortFields.size(); i++) {
            reader.read(p.idx.sortFields.get(i).type(), p.idx.sortDesc.get(i));
        }
        return (String) reader.read(FieldType.KEYWORD, false);
    }

    private static final class Head {
        final SortedKv.Cursor cursor;
        final byte[] prefix;
        byte[] suffix;

        Head(SortedKv.Cursor cursor, byte[] prefix) {
            this.cursor = cursor;
            this.prefix = prefix;
        }
    }

    /**
     * Read the plan's partitions merged in index order, from {@code start}, handing each item id
     * to {@code onId} until it returns false or the entries run out. An item listed under several
     * pinned values comes once. Entries at or before {@code afterSuffix} are skipped.
     */
    private void scanIndex(SortedKv.Reader r, Plan p, byte[] start, byte[] afterSuffix, Budget budget,
                           Predicate<String> onId) {
        PriorityQueue<Head> queue = new PriorityQueue<>((a, b) -> KeyCodec.compareUnsigned(a.suffix, b.suffix));
        List<SortedKv.Cursor> opened = new ArrayList<>();
        try {
            for (byte[] prefix : p.partitions) {
                SortedKv.Cursor c = r.seek(KeyCodec.concat(prefix, start));
                opened.add(c);
                Head h = new Head(c, prefix);
                if (load(h, p)) queue.add(h);
            }
            byte[] previous = null;
            while (!queue.isEmpty()) {
                Head h = queue.poll();
                byte[] suffix = h.suffix;
                budget.charge();
                boolean duplicate = previous != null && KeyCodec.compareUnsigned(previous, suffix) == 0;
                boolean beforeCursor = afterSuffix != null && KeyCodec.compareUnsigned(suffix, afterSuffix) <= 0;
                previous = suffix;
                if (!duplicate && !beforeCursor) {
                    if (!onId.test(idOfEntry(p, h.cursor.key(), h.prefix.length))) return;
                }
                h.cursor.next();
                if (load(h, p)) queue.add(h);
            }
        } finally {
            for (SortedKv.Cursor c : opened) c.close();
        }
    }

    /** Point the head at its cursor's entry; false when the partition or the range is done. */
    private boolean load(Head h, Plan p) {
        SortedKv.Cursor c = h.cursor;
        if (!c.valid() || !KeyCodec.startsWith(c.key(), h.prefix)) return false;
        if (pastRange(p, c.key(), h.prefix.length)) return false;
        byte[] key = c.key();
        h.suffix = java.util.Arrays.copyOfRange(key, h.prefix.length, key.length);
        return true;
    }

    // ==================== paths ====================

    private FcdslResult<T> orderedPath(SortedKv.Reader r, FcdslQuery<T> q, Plan p, Budget budget) {
        byte[] start = rangeStart(p);
        byte[] afterSuffix = null;
        if (q.after != null) {
            afterSuffix = cursorSuffix(p, q);
            if (KeyCodec.compareUnsigned(afterSuffix, start) > 0) start = afterSuffix;
        }
        List<T> page = new ArrayList<>();
        scanIndex(r, p, start, afterSuffix, budget, id -> {
            T item = load(r, id);
            if (item != null && q.matches(item)) page.add(item);
            return page.size() < q.size;
        });
        return pageResult(q, page, q.matchesAll() ? count : null, budget);
    }

    private FcdslResult<T> primaryOrderedPath(SortedKv.Reader r, FcdslQuery<T> q, Budget budget) {
        if (q.after != null && q.after.get(0) == null) throw FcdslException.bad("Cursor id is missing");
        byte[] afterKey = q.after == null ? null : dataKey((String) q.after.get(0));
        List<T> page = new ArrayList<>();
        try (SortedKv.Cursor c = r.seek(afterKey != null ? afterKey : dataPrefix)) {
            for (; c.valid() && KeyCodec.startsWith(c.key(), dataPrefix) && page.size() < q.size; c.next()) {
                budget.charge();
                if (afterKey != null && KeyCodec.compareUnsigned(c.key(), afterKey) <= 0) continue;
                T item = decoder.apply(c.value());
                if (q.matches(item)) page.add(item);
            }
        }
        return pageResult(q, page, q.matchesAll() ? count : null, budget);
    }

    private FcdslResult<T> idsPath(SortedKv.Reader r, FcdslQuery<T> q, Budget budget) {
        List<T> matches = new ArrayList<>();
        for (String id : q.ids) {
            budget.charge();
            T item = load(r, id);
            if (item != null && q.matches(item)) matches.add(item);
        }
        return sortedPage(q, matches, budget);
    }

    private FcdslResult<T> memoryPath(SortedKv.Reader r, FcdslQuery<T> q, Plan p, Budget budget) {
        List<T> matches = new ArrayList<>();
        if (p != null) {
            scanIndex(r, p, rangeStart(p), null, budget, id -> {
                T item = load(r, id);
                if (item != null && q.matches(item)) matches.add(item);
                return true;
            });
        } else {
            try (SortedKv.Cursor c = r.seek(dataPrefix)) {
                for (; c.valid() && KeyCodec.startsWith(c.key(), dataPrefix); c.next()) {
                    budget.charge();
                    T item = decoder.apply(c.value());
                    if (q.matches(item)) matches.add(item);
                }
            }
        }
        return sortedPage(q, matches, budget);
    }

    /** Sort all matches, then take the page after the cursor. The total is known here. */
    private FcdslResult<T> sortedPage(FcdslQuery<T> q, List<T> matches, Budget budget) {
        matches.sort(q.itemComparator());
        List<T> page = new ArrayList<>();
        for (T item : matches) {
            if (page.size() >= q.size) break;
            if (q.afterCursor(item)) page.add(item);
        }
        return pageResult(q, page, (long) matches.size(), budget);
    }

    private FcdslResult<T> pageResult(FcdslQuery<T> q, List<T> page, Long total, Budget budget) {
        List<String> last = page.isEmpty() ? null : q.cursorOf(page.get(page.size() - 1));
        return new FcdslResult<>(page, last, total, budget.used);
    }
}
