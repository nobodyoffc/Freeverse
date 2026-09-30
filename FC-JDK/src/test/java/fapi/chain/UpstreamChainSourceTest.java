package fapi.chain;

import com.google.gson.Gson;
import data.apipData.Fcdsl;
import data.fchData.Block;
import data.fchData.Cash;
import data.fchData.OpReturn;
import db.fcdsl.FcdslException;
import db.fcdsl.FcdslResult;
import db.fcdsl.FieldSchema;
import db.fcdsl.FieldType;
import db.fcdsl.IndexDef;
import db.fcdsl.IndexedCollection;
import db.fcdsl.LevelDbKv;
import fapi.FapiCode;
import fapi.client.FapiClient;
import fapi.message.FapiResponse;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.impl.Iq80DBFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UpstreamChainSource against a fake BASE that answers FCDSL like the real one: pages with a
 * cursor, NOT_FOUND for an empty search, errors as other codes.
 */
class UpstreamChainSourceTest {

    static final String DEALER = "FDealer1111111111111111111111111";
    static final Gson GSON = new Gson();

    /** A BASE stand-in. Data goes through JSON, as it does over the wire. */
    static class FakeBase extends FapiClient {
        final IndexedCollection<Cash> cashes;
        final Map<String, OpReturn> opReturns = new HashMap<>();
        Block best;
        Integer failWith;
        boolean silent;
        final List<String> calls = new ArrayList<>();

        FakeBase(IndexedCollection<Cash> cashes) {
            super(null, "peer", "sid");
            this.cashes = cashes;
        }

        @Override
        public FapiResponse query(String api, Fcdsl fcdsl) {
            calls.add(api + ":" + fcdsl.getEntity());
            if (silent) return null;
            FapiResponse r = new FapiResponse();
            if (failWith != null) {
                r.setCode(failWith);
                r.setMessage("broken");
                return r;
            }
            Object data;
            List<String> last = null;
            switch (api + ":" + fcdsl.getEntity()) {
                case "base.search:block" -> data = best == null ? null : List.of(best);
                case "base.search:cash" -> {
                    FcdslResult<Cash> page = cashes.query(fcdsl, null, null);
                    data = page.getItems().isEmpty() ? null : page.getItems();
                    last = page.getLast();
                }
                case "base.getByIds:opreturn" -> {
                    Map<String, OpReturn> got = new HashMap<>();
                    for (String id : fcdsl.getIds()) if (opReturns.containsKey(id)) got.put(id, opReturns.get(id));
                    data = got.isEmpty() ? null : got;
                }
                default -> throw new AssertionError("unexpected " + api + ":" + fcdsl.getEntity());
            }
            if (data == null) {
                r.setCode(FapiCode.NOT_FOUND);
                r.setMessage("No data found");
                return r;
            }
            r.setCode(FapiCode.SUCCESS);
            r.setData(GSON.fromJson(GSON.toJson(data), Object.class));
            r.setLast(last);
            return r;
        }
    }

    @TempDir
    File dir;
    DB db;
    FakeBase base;
    UpstreamChainSource source;

    @BeforeEach
    void setUp() throws Exception {
        db = Iq80DBFactory.factory.open(dir, new Options().createIfMissing(true));
        FieldSchema<Cash> schema = FieldSchema.builder(Cash.class, "id", Cash::getId)
                .keyword("owner", Cash::getOwner)
                .field("valid", FieldType.BOOLEAN, Cash::isValid)
                .longField("birthHeight", Cash::getBirthHeight)
                .longField("cd", Cash::getCd)
                .build();
        IndexedCollection<Cash> cashes = new IndexedCollection<>(new LevelDbKv(db), "cash", schema,
                List.of(IndexDef.named("owner").eq("owner").asc("birthHeight").build()),
                new IndexedCollection.Options<Cash>().maxSize(3000).maxExamined(1_000_000));
        base = new FakeBase(cashes);
        source = new UpstreamChainSource(() -> base, "test");
    }

    @AfterEach
    void tearDown() throws Exception {
        db.close();
    }

    static Cash cash(String id, String owner, long height, boolean valid, long cd) {
        Cash c = new Cash();
        c.setId(id);
        c.setOwner(owner);
        c.setBirthHeight(height);
        c.setValid(valid);
        c.setCd(cd);
        c.setIssuer("FPayer");
        c.setValue(1000L);
        c.setBirthTxId("tx-" + id);
        return c;
    }

    @Test
    void readsEveryPageWithoutCuttingAHeight() {
        List<Cash> all = new ArrayList<>();
        // 1200 at one height straddles the 1000-cash page boundary
        for (int i = 0; i < 1200; i++) all.add(cash(String.format("a%05d", i), DEALER, 50, true, i));
        for (int i = 0; i < 1300; i++) all.add(cash(String.format("b%05d", i), DEALER, 51 + i % 3, true, i));
        all.add(cash("old", DEALER, 10, true, 1));
        all.add(cash("spent", DEALER, 60, false, 1));
        all.add(cash("other", "FSomeoneElse111111111111111111111", 60, true, 1));
        base.cashes.putAll(all);

        List<Cash> got = source.cashesOwnedSince(DEALER, 10);

        assertEquals(2500, got.size());
        assertEquals(2500, got.stream().map(Cash::getId).distinct().count());
        for (int i = 1; i < got.size(); i++) {
            Cash a = got.get(i - 1), b = got.get(i);
            int c = Long.compare(a.getBirthHeight(), b.getBirthHeight());
            assertTrue(c < 0 || (c == 0 && a.getId().compareTo(b.getId()) < 0), "order at " + i);
        }
        assertEquals(3, base.calls.size(), "three pages of up to 1000");
    }

    @Test
    void notFoundIsEmptyButFailuresThrow() {
        assertEquals(List.of(), source.cashesOwnedSince(DEALER, 0));
        assertEquals(List.of(), source.validCashes(DEALER, 10));
        assertEquals(Map.of(), source.opReturns(List.of("tx1")));

        base.failWith = FapiCode.INTERNAL_ERROR;
        assertThrows(ChainUnavailableException.class, () -> source.cashesOwnedSince(DEALER, 0));
        base.failWith = FapiCode.PAYMENT_REQUIRED;
        assertThrows(ChainUnavailableException.class, () -> source.opReturns(List.of("tx1")));
        base.failWith = null;
        base.silent = true;
        assertThrows(ChainUnavailableException.class, () -> source.bestBlock());

        UpstreamChainSource disconnected = new UpstreamChainSource(() -> null, "gone");
        assertThrows(ChainUnavailableException.class, disconnected::bestBlock);
    }

    @Test
    void bestBlockAndValidCashes() {
        assertThrows(ChainUnavailableException.class, () -> source.bestBlock(), "no block is not a height");
        Block b = new Block();
        b.setHeight(123L);
        b.setId("blockid");
        base.best = b;
        assertEquals(123L, source.bestBlock().getHeight());

        base.cashes.putAll(List.of(cash("c1", DEALER, 5, true, 30), cash("c2", DEALER, 6, true, 10),
                cash("c3", DEALER, 7, false, 1)));
        assertEquals(List.of("c2", "c1"), source.validCashes(DEALER, 10).stream().map(Cash::getId).toList());
    }

    @Test
    void opReturnsAreFetchedInBatchesAndViaIsParsed() {
        List<String> txIds = IntStream.range(0, 250).mapToObj(i -> "tx" + i).collect(Collectors.toList());
        for (int i = 0; i < 250; i += 2) {
            OpReturn o = new OpReturn();
            o.setId("tx" + i);
            o.setOpReturn("{\"via\":\"FVia1111111111111111111111111111\"}");
            base.opReturns.put("tx" + i, o);
        }
        Map<String, String> got = source.opReturns(txIds);
        assertEquals(125, got.size());
        assertEquals(3, base.calls.size(), "batches of 100");
        assertEquals("FVia1111111111111111111111111111", Via.parse(got.get("tx0")));
        assertNull(Via.parse("plain text"));
        assertNull(Via.parse("{\"via\":\"short\"}"));
    }

    @Test
    void anUnusableCursorFailsRatherThanLooping() {
        FakeBase stuck = new FakeBase(base.cashes) {
            @Override
            public FapiResponse query(String api, Fcdsl fcdsl) {
                FapiResponse r = super.query(api, fcdsl);
                r.setLast(null);
                return r;
            }
        };
        List<Cash> all = new ArrayList<>();
        for (int i = 0; i < 1000; i++) all.add(cash(String.format("s%05d", i), DEALER, 5, true, 1));
        base.cashes.putAll(all);
        UpstreamChainSource s = new UpstreamChainSource(() -> stuck, "stuck");
        assertThrows(ChainUnavailableException.class, () -> s.cashesOwnedSince(DEALER, 0));
    }

    @Test
    void theFakeBaseRejectsWhatRealEsWouldToo() {
        Fcdsl bad = new Fcdsl();
        bad.addSort("nope", "asc");
        assertThrows(FcdslException.class, () -> base.cashes.query(bad, null, null));
    }
}
