package db.fcdsl;

import data.apipData.Except;
import data.apipData.FcQuery;
import data.apipData.Fcdsl;
import data.apipData.Filter;
import data.apipData.Sort;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.impl.Iq80DBFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class IndexedCollectionTest {

    /** A stand-in item with every field type. */
    static class Item {
        String id;
        String sender;
        List<String> recipients;
        Long createTime;
        Long size;
        Double score;
        Boolean pinned;
        String title;

        Item() {}

        Item(String id, String sender, List<String> recipients, Long createTime, Long size,
             Double score, Boolean pinned, String title) {
            this.id = id;
            this.sender = sender;
            this.recipients = recipients;
            this.createTime = createTime;
            this.size = size;
            this.score = score;
            this.pinned = pinned;
            this.title = title;
        }

        @Override
        public String toString() { return id; }
    }

    static final FieldSchema<Item> SCHEMA = FieldSchema.builder(Item.class, "id", (Item i) -> i.id)
            .keyword("sender", i -> i.sender)
            .keywords("recipients", i -> i.recipients)
            .longField("createTime", i -> i.createTime)
            .longField("size", i -> i.size)
            .field("score", FieldType.DOUBLE, i -> i.score)
            .field("pinned", FieldType.BOOLEAN, i -> i.pinned)
            .field("title", FieldType.TEXT, i -> i.title)
            .build();

    static final List<IndexDef> INDEXES = List.of(
            IndexDef.named("rcptTimeAsc").eq("recipients").asc("createTime").build(),
            IndexDef.named("rcptTimeDesc").eq("recipients").desc("createTime").build(),
            IndexDef.named("size").asc("size").build(),
            IndexDef.named("sender").eq("sender").build());

    @TempDir
    File dir;
    DB db;
    SortedKv kv;

    @BeforeEach
    void openDb() throws Exception {
        db = Iq80DBFactory.factory.open(new File(dir, "db"), new Options().createIfMissing(true));
        kv = new LevelDbKv(db);
    }

    @AfterEach
    void closeDb() throws Exception {
        db.close();
    }

    IndexedCollection<Item> open(List<IndexDef> indexes, long maxExamined) {
        return new IndexedCollection<>(kv, "items", SCHEMA, indexes,
                new IndexedCollection.Options<Item>().maxExamined(maxExamined).maxSize(3000));
    }

    IndexedCollection<Item> open() {
        return open(INDEXES, 100_000);
    }

    // ==================== FCDSL builders ====================

    static Fcdsl fcdsl(Consumer<Fcdsl> c) {
        Fcdsl f = new Fcdsl();
        c.accept(f);
        return f;
    }

    static FcQuery q(Consumer<FcQuery> c) {
        FcQuery q = new FcQuery();
        c.accept(q);
        return q;
    }

    static List<String> ids(FcdslResult<Item> r) {
        List<String> out = new ArrayList<>();
        for (Item i : r.getItems()) out.add(i.id);
        return out;
    }

    List<String> run(IndexedCollection<Item> c, Fcdsl f) {
        return ids(c.query(f, null, null));
    }

    // ==================== operator semantics ====================

    IndexedCollection<Item> small() {
        IndexedCollection<Item> c = open();
        c.putAll(List.of(
                new Item("a", "alice", List.of("r1", "r2"), 100L, 10L, 1.5, true, "Hello World"),
                new Item("b", "bob", List.of("r2"), 200L, 20L, -2.0, false, "hello there"),
                new Item("c", "carol", List.of(), 300L, null, null, null, "你好 世界"),
                new Item("d", "dave", null, null, 40L, 0.0, true, null)));
        return c;
    }

    @Test
    void termsAndEqualsMatchAnyFieldAnyValueAndArrayElements() {
        IndexedCollection<Item> c = small();
        assertEquals(List.of("a", "b"), run(c, fcdsl(f -> f.setQuery(q(x -> x.addNewTerms()
                .addNewFields("recipients").addNewValues("r2"))))));
        assertEquals(List.of("a", "c"), run(c, fcdsl(f -> f.setQuery(q(x -> x.addNewTerms()
                .addNewFields("sender", "recipients").addNewValues("carol", "r1"))))));
        assertEquals(List.of("b"), run(c, fcdsl(f -> f.setQuery(q(x -> x.addNewEquals()
                .addNewFields("size").addNewValues("20"))))));
        // unequals: none of the fields has any of the values; a missing value is not equal
        assertEquals(List.of("b", "c", "d"), run(c, fcdsl(f -> f.setQuery(q(x -> x.addNewUnequals()
                .addNewFields("recipients").addNewValues("r1"))))));
        // values are URL-decoded, as they were for ES
        assertEquals(List.of("b"), run(c, fcdsl(f -> f.setQuery(q(x -> x.addNewTerms()
                .addNewFields("title").addNewValues("hello%20there"))))));
    }

    @Test
    void partIsAWildcardSubstringWithOptionalCase() {
        IndexedCollection<Item> c = small();
        assertEquals(List.of("b"), run(c, fcdsl(f -> f.setQuery(q(x -> x.addNewPart()
                .addNewFields("title").addNewValue("lo th"))))));
        assertEquals(List.of("a", "b"), run(c, fcdsl(f -> f.setQuery(q(x -> {
            x.addNewPart().addNewFields("title").addNewValue("HELLO");
            x.getPart().setIsCaseInsensitive("true");
        })))));
        assertEquals(List.of(), run(c, fcdsl(f -> f.setQuery(q(x -> x.addNewPart()
                .addNewFields("title").addNewValue("HELLO"))))));
        assertEquals(List.of("a"), run(c, fcdsl(f -> f.setQuery(q(x -> x.addNewPart()
                .addNewFields("title").addNewValue("W?rld"))))));
    }

    @Test
    void matchSharesATokenCaseInsensitivelyAndSplitsCjk() {
        IndexedCollection<Item> c = small();
        assertEquals(List.of("a", "b"), run(c, fcdsl(f -> f.setQuery(q(x -> x.addNewMatch()
                .addNewFields("title").addNewValue("HELLO nobody"))))));
        assertEquals(List.of("c"), run(c, fcdsl(f -> f.setQuery(q(x -> x.addNewMatch()
                .addNewFields("title").addNewValue("世"))))));
        assertEquals(List.of("d"), run(c, fcdsl(f -> f.setQuery(q(x -> x.addNewMatch()
                .addNewFields("size").addNewValue("40"))))));
    }

    @Test
    void rangeComparesNumbersAsNumbers() {
        IndexedCollection<Item> c = small();
        assertEquals(List.of("b", "d"), run(c, fcdsl(f -> f.setQuery(q(x -> x.addNewRange()
                .addNewFields("size").addGt("10").addLte("40"))))));
        assertEquals(List.of("a", "b", "d"), run(c, fcdsl(f -> f.setQuery(q(x -> x.addNewRange()
                .addNewFields("size").addGte("10"))))));
        // 9 < 10 numerically; a string comparison would say "9" > "10"
        assertEquals(List.of(), run(c, fcdsl(f -> f.setQuery(q(x -> x.addNewRange()
                .addNewFields("size").addLt("9"))))));
        assertEquals(List.of("b", "d"), run(c, fcdsl(f -> f.setQuery(q(x -> x.addNewRange()
                .addNewFields("score").addLte("0"))))));
    }

    @Test
    void existsAndUnexists() {
        IndexedCollection<Item> c = small();
        // an empty array has no value
        assertEquals(List.of("a", "b"), run(c, fcdsl(f -> f.setQuery(q(x -> x.setExists(new String[]{"recipients"}))))));
        assertEquals(List.of("a", "b"), run(c, fcdsl(f -> f.setQuery(q(x -> x.setExists(new String[]{"recipients", "size"}))))));
        assertEquals(List.of("c"), run(c, fcdsl(f -> f.setQuery(q(x -> x.setUnexists(new String[]{"size", "score"}))))));
    }

    @Test
    void filterAndsWithQueryAndExceptRemoves() {
        IndexedCollection<Item> c = small();
        Fcdsl f = new Fcdsl();
        f.setQuery(q(x -> x.addNewRange().addNewFields("createTime").addGte("100")));
        Filter filter = new Filter();
        filter.addNewTerms().addNewFields("recipients").addNewValues("r2");
        f.setFilter(filter);
        Except except = new Except();
        except.addNewTerms().addNewFields("sender").addNewValues("bob");
        f.setExcept(except);
        assertEquals(List.of("a"), run(c, f));
    }

    @Test
    void extraServerConditionsMustAllHold() {
        IndexedCollection<Item> c = small();
        FcQuery notOld = q(x -> x.addNewRange().addNewFields("createTime").addGt("150"));
        assertEquals(List.of("b", "c"), ids(c.query(null, List.of(notOld), null)));
    }

    @Test
    void missingValuesSortLastInBothDirections() {
        IndexedCollection<Item> c = small();
        assertEquals(List.of("a", "b", "d", "c"), run(c, fcdsl(f -> f.addSort("size", "asc"))));
        assertEquals(List.of("d", "b", "a", "c"), run(c, fcdsl(f -> f.addSort("size", "desc"))));
    }

    @Test
    void cursorLooksLikeEsSortValues() {
        IndexedCollection<Item> c = small();
        FcdslResult<Item> r = c.query(fcdsl(f -> {
            f.addSort("createTime", "asc");
            f.addSize(1);
        }), null, null);
        assertEquals(List.of("100", "a"), r.getLast());
        FcdslResult<Item> next = c.query(fcdsl(f -> {
            f.addSort("createTime", "asc");
            f.addSize(1);
            f.setAfter(List.of("100", "a"));
        }), null, null);
        assertEquals(List.of("b"), ids(next));
        assertEquals(4L, c.query(null, null, null).getTotal());
    }

    // ==================== errors ====================

    @Test
    void badQueriesAreRejectedAsBadQuery() {
        IndexedCollection<Item> c = small();
        assertBad(c, fcdsl(f -> f.setQuery(q(x -> x.addNewTerms().addNewFields("nope").addNewValues("x")))));
        assertBad(c, fcdsl(f -> f.addSort("nope", "asc")));
        assertBad(c, fcdsl(f -> f.addSort("recipients", "asc")));
        assertBad(c, fcdsl(f -> f.setQuery(q(x -> x.addNewRange().addNewFields("size").addGt("big")))));
        assertBad(c, fcdsl(f -> {
            f.addSort("createTime", "asc");
            f.setAfter(List.of("100"));
        }));
        assertBad(c, fcdsl(f -> f.setAfter(List.of("x", "y"))));
    }

    static void assertBad(IndexedCollection<Item> c, Fcdsl f) {
        FcdslException e = assertThrows(FcdslException.class, () -> c.query(f, null, null));
        assertEquals(FcdslException.Reason.BAD_QUERY, e.getReason());
    }

    @Test
    void tooBroadFailsInsteadOfReturningAShortPage() {
        IndexedCollection<Item> c = open(INDEXES, 50);
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < 200; i++) items.add(new Item(String.format("i%03d", i), "s", List.of("r"), (long) i, (long) i, 0.0, i == 199, "t"));
        c.putAll(items);
        // a rare match with no usable index: reading everything would exceed the limit
        FcdslException e = assertThrows(FcdslException.class, () -> c.query(fcdsl(f -> {
            f.setQuery(q(x -> x.addNewTerms().addNewFields("pinned").addNewValues("true")));
            f.addSort("createTime", "desc");
        }), null, null));
        assertEquals(FcdslException.Reason.TOO_BROAD, e.getReason());
        // an ordered index read stops as soon as the page is full, well inside the limit
        FcdslResult<Item> r = c.query(fcdsl(f -> {
            f.setQuery(q(x -> x.addNewTerms().addNewFields("recipients").addNewValues("r")));
            f.addSort("createTime", "desc");
            f.addSize(10);
        }), null, null);
        assertEquals(10, r.getItems().size());
        assertEquals("i199", r.getItems().get(0).id);
        assertTrue(r.getExamined() <= 11, "examined " + r.getExamined());

        // a deep page seeks straight to its cursor: 100 entries precede it, twice the limit
        FcdslResult<Item> deep = c.query(fcdsl(f -> {
            f.setQuery(q(x -> x.addNewTerms().addNewFields("recipients").addNewValues("r")));
            f.addSort("createTime", "desc");
            f.addSize(10);
            f.setAfter(List.of("100", "i100"));
        }), null, null);
        assertEquals("i099", deep.getItems().get(0).id);
        assertTrue(deep.getExamined() <= 11, "examined " + deep.getExamined());
    }

    // ==================== planner ====================

    @Test
    void newcomerBoardQueryReadsOnlyTheNewestEntries() {
        IndexedCollection<Item> c = open();
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            items.add(new Item("m" + i, "s" + (i % 7), List.of("board", "u" + (i % 50)), 1_000L + i, (long) i, 0.0, false, "x"));
        }
        c.putAll(items);
        FcdslResult<Item> r = c.query(fcdsl(f -> {
            f.addSort("createTime", "desc");
            f.addSize(20);
            f.addNewQuery().addNewRange().addNewFields("createTime").addGt(String.valueOf(1_000L + 4990));
        }), List.of(q(x -> x.addNewTerms().addNewFields("recipients").addNewValues("board"))), null);
        assertEquals(9, r.getItems().size());
        assertEquals("m4999", r.getItems().get(0).id);
        assertEquals("m4991", r.getItems().get(8).id);
        assertTrue(r.getExamined() <= 10, "examined " + r.getExamined());
    }

    @Test
    void severalRecipientsMergeInOrderAndListAnItemOnce() {
        IndexedCollection<Item> c = open();
        c.putAll(List.of(
                new Item("x1", "s", List.of("A", "B"), 5L, 1L, 0.0, false, "t"),
                new Item("x2", "s", List.of("A"), 3L, 1L, 0.0, false, "t"),
                new Item("x3", "s", List.of("B"), 4L, 1L, 0.0, false, "t"),
                new Item("x4", "s", List.of("C"), 1L, 1L, 0.0, false, "t")));
        FcdslResult<Item> r = c.query(fcdsl(f -> {
            f.addSort("createTime", "asc");
            f.addSort("id", "asc");
        }), List.of(q(x -> x.addNewTerms().addNewFields("recipients").addNewValues("A", "B"))), null);
        assertEquals(List.of("x2", "x3", "x1"), ids(r));
    }

    @Test
    void updatesMoveIndexEntriesAndDeletesRemoveThem() {
        IndexedCollection<Item> c = open();
        c.put(new Item("u", "s", List.of("A", "B"), 5L, 1L, 0.0, false, "t"));
        c.put(new Item("u", "s", List.of("B", "C"), 9L, 1L, 0.0, false, "t"));
        FcQuery toA = q(x -> x.addNewTerms().addNewFields("recipients").addNewValues("A"));
        FcQuery toC = q(x -> x.addNewTerms().addNewFields("recipients").addNewValues("C"));
        Fcdsl byTime = fcdsl(f -> f.addSort("createTime", "asc"));
        assertEquals(List.of(), ids(c.query(byTime, List.of(toA), null)));
        assertEquals(List.of("u"), ids(c.query(byTime, List.of(toC), null)));
        assertEquals(1, c.count());
        assertTrue(c.delete("u"));
        assertEquals(List.of(), ids(c.query(byTime, List.of(toC), null)));
        assertEquals(0, c.count());
        assertEquals(0, countKeys(), "no index entries left behind");
    }

    int countKeys() {
        int n = 0;
        try (SortedKv.Reader r = kv.openReader(); SortedKv.Cursor cur = r.seek("items\0x".getBytes(StandardCharsets.UTF_8))) {
            for (; cur.valid() && KeyCodec.startsWith(cur.key(), "items\0x".getBytes(StandardCharsets.UTF_8)); cur.next()) n++;
        }
        return n;
    }

    @Test
    void changedIndexesAreRebuiltOnOpenAndDroppedOnesRemoved() {
        IndexedCollection<Item> c = open(List.of(), 100_000);
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < 300; i++) items.add(new Item("k" + i, "s", List.of("A"), (long) i, (long) (i % 10), 0.0, false, "t"));
        c.putAll(items);
        assertEquals(0, countKeys());

        IndexedCollection<Item> reopened = open();
        assertEquals(300, reopened.count());
        FcdslResult<Item> r = reopened.query(fcdsl(f -> {
            f.addSort("createTime", "desc");
            f.addSize(5);
        }), List.of(q(x -> x.addNewTerms().addNewFields("recipients").addNewValues("A"))), null);
        assertEquals(List.of("k299", "k298", "k297", "k296", "k295"), ids(r));
        assertTrue(r.getExamined() <= 6);

        open(List.of(), 100_000);
        assertEquals(0, countKeys());
    }

    // ==================== randomized check of every path and full paging ====================

    @Test
    void everyPathPagesExactlyLikeABruteForceSort() {
        IndexedCollection<Item> c = open();
        Random rnd = new Random(42);
        List<Item> all = new ArrayList<>();
        String[] people = {"A", "B", "C", "D", "E"};
        for (int i = 0; i < 400; i++) {
            List<String> rc = new ArrayList<>();
            for (String p : people) if (rnd.nextInt(3) == 0) rc.add(p);
            Long t = rnd.nextInt(10) == 0 ? null : (long) rnd.nextInt(60);
            Long size = rnd.nextInt(10) == 0 ? null : (long) rnd.nextInt(30) - 5;
            all.add(new Item("id" + rnd.nextInt(100_000) + "_" + i, people[rnd.nextInt(people.length)],
                    rc.isEmpty() && rnd.nextBoolean() ? null : rc, t, size,
                    rnd.nextInt(5) == 0 ? null : rnd.nextDouble() * 10 - 5, rnd.nextBoolean(), "w" + rnd.nextInt(4)));
        }
        c.putAll(all);

        String[] sortFields = {"createTime", "size", "score", "sender", "pinned", "id"};
        for (int round = 0; round < 400; round++) {
            Fcdsl f = new Fcdsl();
            List<FcQuery> extra = new ArrayList<>();
            int shape = rnd.nextInt(6);
            if (shape <= 1) {
                String[] who = rnd.nextBoolean() ? new String[]{people[rnd.nextInt(5)]} : new String[]{people[rnd.nextInt(5)], people[rnd.nextInt(5)]};
                extra.add(q(x -> x.addNewTerms().addNewFields("recipients").addNewValues(who)));
            }
            if (shape == 2) {
                f.setQuery(q(x -> x.addNewTerms().addNewFields("sender").addNewValues(people[rnd.nextInt(5)])));
            }
            if (rnd.nextBoolean()) {
                String field = rnd.nextBoolean() ? "createTime" : "size";
                long a = rnd.nextInt(60) - 5;
                FcQuery rq = q(x -> {
                    x.addNewRange().addNewFields(field);
                    if (rnd.nextBoolean()) x.getRange().addGt(String.valueOf(a)); else x.getRange().addGte(String.valueOf(a));
                    if (rnd.nextBoolean()) x.getRange().addLt(String.valueOf(a + rnd.nextInt(30)));
                });
                extra.add(rq);
            }
            if (rnd.nextInt(4) == 0) {
                Except ex = new Except();
                ex.addNewTerms().addNewFields("pinned").addNewValues("true");
                f.setExcept(ex);
            }
            if (rnd.nextInt(5) == 0) {
                List<String> someIds = new ArrayList<>();
                for (int k = 0; k < 30; k++) someIds.add(all.get(rnd.nextInt(all.size())).id);
                f.setIds(someIds);
            }
            int nSort = rnd.nextInt(3);
            for (int k = 0; k < nSort; k++) f.addSort(sortFields[rnd.nextInt(sortFields.length)], rnd.nextBoolean() ? "asc" : "desc");
            if (nSort == 1 && rnd.nextBoolean()) f.addSort("id", "asc");
            int size = 1 + rnd.nextInt(25);
            f.addSize(size);

            FcdslQuery<Item> compiled = c.compile(f, extra, null);
            List<Item> expected = new ArrayList<>();
            for (Item it : all) if (compiled.matches(it)) expected.add(it);
            expected.sort(compiled.itemComparator());

            List<String> got = new ArrayList<>();
            List<String> cursor = null;
            for (int page = 0; page < 1000; page++) {
                if (cursor != null) f.setAfter(cursor);
                FcdslResult<Item> r = c.query(f, extra, null);
                got.addAll(ids(r));
                if (r.getItems().size() < size) break;
                cursor = r.getLast();
            }
            List<String> exp = new ArrayList<>();
            for (Item it : expected) exp.add(it.id);
            assertEquals(exp, got, "round " + round + ": sort=" + describe(f) + " extra=" + extra.size());
        }
    }

    static String describe(Fcdsl f) {
        StringBuilder b = new StringBuilder();
        if (f.getSort() != null) for (Sort s : f.getSort()) b.append(s.getField()).append(' ').append(s.getOrder()).append(',');
        return b.toString();
    }

    // ==================== key codec ====================

    @Test
    void keyOrderMatchesValueOrderInBothDirections() {
        Random rnd = new Random(7);
        List<Object[]> cases = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            cases.add(new Object[]{FieldType.LONG, pickLong(rnd), pickLong(rnd)});
            cases.add(new Object[]{FieldType.DOUBLE, pickDouble(rnd), pickDouble(rnd)});
            cases.add(new Object[]{FieldType.KEYWORD, pickString(rnd), pickString(rnd)});
        }
        cases.add(new Object[]{FieldType.BOOLEAN, false, true});
        for (Object[] cs : cases) {
            FieldType t = (FieldType) cs[0];
            int want = Integer.signum(FieldSchema.compare(cs[1], cs[2]));
            for (boolean desc : new boolean[]{false, true}) {
                byte[] a = KeyCodec.encode(t, cs[1], desc), b = KeyCodec.encode(t, cs[2], desc);
                int got = Integer.signum(KeyCodec.compareUnsigned(a, b));
                assertEquals(desc ? -want : want, got, t + " " + cs[1] + " vs " + cs[2] + " desc=" + desc);
                assertEquals(cs[1], new KeyCodec.Reader(a, 0).read(t, desc), "round trip");
            }
            // missing sorts after any value, either direction
            assertTrue(KeyCodec.compareUnsigned(KeyCodec.encode(t, cs[1], true), KeyCodec.encode(t, null, true)) < 0);
        }
    }

    static long pickLong(Random r) {
        long[] edge = {Long.MIN_VALUE, -1, 0, 1, Long.MAX_VALUE};
        return r.nextInt(5) == 0 ? edge[r.nextInt(edge.length)] : r.nextLong() >> r.nextInt(60);
    }

    static double pickDouble(Random r) {
        double[] edge = {Double.NEGATIVE_INFINITY, -1.5, 0.0, 1e-300, 2.5, Double.MAX_VALUE, Double.POSITIVE_INFINITY};
        return r.nextInt(5) == 0 ? edge[r.nextInt(edge.length)] : (r.nextDouble() - 0.5) * Math.pow(10, r.nextInt(20) - 10);
    }

    static String pickString(Random r) {
        String alphabet = "ab\0zé中😀";
        StringBuilder b = new StringBuilder();
        int n = r.nextInt(5);
        for (int i = 0; i < n; ) {
            int cp = alphabet.codePointAt(r.nextInt(alphabet.length() - 1));
            if (Character.isLowSurrogate((char) cp)) continue;
            b.appendCodePoint(cp);
            i++;
        }
        return b.toString();
    }

    @Test
    void projectionKeepsAndDropsTopLevelFields() {
        IndexedCollection<Item> c = small();
        FcdslQuery<Item> q = c.compile(fcdsl(f -> {
            f.setFields(Arrays.asList("id", "title", "sender"));
            f.setNoFields(List.of("sender"));
        }), null, null);
        var out = FcdslProjection.project(q, c.query(q).getItems());
        assertEquals(2, out.get(0).size());
        assertEquals("a", out.get(0).get("id").getAsString());
        assertEquals("Hello World", out.get(0).get("title").getAsString());
    }
}
