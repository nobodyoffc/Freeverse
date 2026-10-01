package fapi;

import com.google.gson.Gson;
import config.Settings;
import core.crypto.KeyTools;
import data.apipData.Fcdsl;
import data.fchData.Block;
import data.fchData.Cash;
import data.fchData.OpReturn;
import data.feipData.Service;
import data.feipData.ServiceType;
import db.fcdsl.FcdslResult;
import db.fcdsl.FieldSchema;
import db.fcdsl.FieldType;
import db.fcdsl.IndexDef;
import db.fcdsl.IndexedCollection;
import db.fcdsl.LevelDbKv;
import fapi.chain.ChainSource;
import fapi.chain.ChainSources;
import fapi.client.FapiClient;
import fapi.message.FapiRequest;
import fapi.message.FapiResponse;
import fapi.service.FapiServer;
import fudp.node.FudpNode;
import fudp.node.NodeConfig;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.impl.Iq80DBFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** A light FAPI server: which components it runs, and top-ups learned from an upstream BASE. */
class LightServerTest {

    static final SecureRandom RNG = new SecureRandom();
    static final Gson GSON = new Gson();

    @TempDir
    Path dir;
    final List<FudpNode> nodes = new ArrayList<>();
    final List<AutoCloseable> closers = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (AutoCloseable c : closers) {
            try { c.close(); } catch (Exception ignored) { }
        }
        for (FudpNode n : nodes) {
            try { n.stop(); } catch (Exception ignored) { }
        }
    }

    // ==================== which components ====================

    static final String BASE = "BASE@NO1_NRC7", DISK = "DISK@NO1_NRC7", DOCK = "DOCK@NO1_NRC7",
            CALL = "CALL@NO1_NRC7", ROAD = "ROAD@NO1_NRC7", MAP = "MAP@NO1_NRC7";

    @Test
    void aFullServerMergesAsBefore() {
        assertArrayEquals(new String[]{BASE, DISK},
                ServiceBootstrap.resolveComponentTypes(new String[]{"BASE@No1_NrC7"}, List.of("DISK@No1_NrC7"), false));
        // with nothing configured or declared, a full server falls back to BASE, spelled as declared
        assertArrayEquals(new String[]{data.feipData.ApiGroupType.BASE_NO1_NRC7},
                ServiceBootstrap.resolveComponentTypes(null, null, false));
    }

    @Test
    void aLightServerDropsBaseAndKeepsWhatItCanRun() {
        assertArrayEquals(new String[]{DOCK, CALL}, ServiceBootstrap.resolveComponentTypes(new String[0],
                List.of("BASE@No1_NrC7", "DOCK@No1_NrC7", "CALL@No1_NrC7"), true));
    }

    @Test
    void aLightRoadAlwaysComesWithMap() {
        assertArrayEquals(new String[]{ROAD, MAP},
                ServiceBootstrap.resolveComponentTypes(new String[0], List.of("ROAD@No1_NrC7"), true));
    }

    @Test
    void aLightServerWithNothingToRunRefusesToStart() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> ServiceBootstrap.resolveComponentTypes(new String[0], List.of("BASE@No1_NrC7"), true));
        assertTrue(e.getMessage().contains("CALL, DISK, DOCK, ROAD or MAP"));
        assertThrows(IllegalStateException.class, () -> ServiceBootstrap.resolveComponentTypes(null, null, true));
    }

    // ==================== top-ups through the upstream ====================

    /** An upstream BASE answering from db.fcdsl collections, as the real one answers from ES. */
    static final class StandInBase implements FapiComponent {
        final IndexedCollection<Cash> cashes;
        final Map<String, OpReturn> opReturns = new HashMap<>();
        final Block best = new Block();

        StandInBase(IndexedCollection<Cash> cashes) {
            this.cashes = cashes;
        }

        @Override
        public String getName() { return "BASE"; }

        @Override
        public List<String> getApiList() { return List.of("base.search", "base.getByIds"); }

        @Override
        public void initialize(FapiServer server) {}

        @Override
        public FapiResponse handleRequest(FapiRequest request, String peerId) {
            Fcdsl f = request.getFcdsl();
            Object data = null;
            List<String> last = null;
            switch (request.getApi() + ":" + f.getEntity()) {
                case "base.search:block" -> data = List.of(best);
                case "base.search:cash" -> {
                    FcdslResult<Cash> page = cashes.query(f, null, null);
                    if (!page.getItems().isEmpty()) data = page.getItems();
                    last = page.getLast();
                }
                case "base.getByIds:opreturn" -> {
                    Map<String, OpReturn> got = new HashMap<>();
                    for (String id : f.getIds()) if (opReturns.containsKey(id)) got.put(id, opReturns.get(id));
                    if (!got.isEmpty()) data = got;
                }
                default -> { }
            }
            if (data == null) {
                FapiResponse r = new FapiResponse();
                r.setId(request.getId());
                r.setCode(FapiCode.NOT_FOUND);
                r.setMessage("No data found");
                return r;
            }
            FapiResponse r = FapiResponse.success(request.getId(), data);
            r.setLast(last);
            return r;
        }
    }

    FudpNode node(byte[] priv, int port, String name) throws Exception {
        NodeConfig c = new NodeConfig();
        c.setPort(port);
        c.setMaxPacketSize(1400);
        c.setDataDir(dir.resolve(name).toString());
        FudpNode n = new FudpNode(priv, c);
        nodes.add(n);
        return n;
    }

    static byte[] key() {
        byte[] k = new byte[32];
        RNG.nextBytes(k);
        return k;
    }

    @Test
    void aLightServerCreditsATopUpItLearnsFromTheUpstream() throws Exception {
        int port = 31000 + RNG.nextInt(2000);
        String dealer = KeyTools.pubkeyToFchAddr(KeyTools.prikeyToPubkey(key()));
        String payer = KeyTools.pubkeyToFchAddr(KeyTools.prikeyToPubkey(key()));
        String via = KeyTools.pubkeyToFchAddr(KeyTools.prikeyToPubkey(key()));

        // ---- upstream: a real FAPI server whose BASE knows one top-up to the dealer
        DB db = Iq80DBFactory.factory.open(dir.resolve("upstream-chain").toFile(), new Options().createIfMissing(true));
        closers.add(db);
        FieldSchema<Cash> schema = FieldSchema.builder(Cash.class, "id", Cash::getId)
                .keyword("owner", Cash::getOwner)
                .field("valid", FieldType.BOOLEAN, Cash::isValid)
                .longField("birthHeight", Cash::getBirthHeight)
                .longField("cd", Cash::getCd)
                .build();
        IndexedCollection<Cash> cashes = new IndexedCollection<>(new LevelDbKv(db), "cash", schema,
                List.of(IndexDef.named("owner").eq("owner").asc("birthHeight").build()), null);
        StandInBase base = new StandInBase(cashes);
        base.best.setHeight(200L);
        base.best.setId("best-block");
        Cash topUp = new Cash();
        topUp.setId("cash-1");
        topUp.setOwner(dealer);
        topUp.setIssuer(payer);
        topUp.setValue(50_000_000L);
        topUp.setValid(true);
        topUp.setBirthHeight(150L);
        topUp.setBirthTxId("tx-1");
        topUp.setBirthBlockId("block-150");
        cashes.put(topUp);
        OpReturn op = new OpReturn();
        op.setId("tx-1");
        op.setOpReturn("{\"via\":\"" + via + "\"}");
        base.opReturns.put("tx-1", op);

        byte[] upPriv = key();
        byte[] upPub = KeyTools.prikeyToPubkey(upPriv);
        String upFid = KeyTools.pubkeyToFchAddr(upPub);
        FudpNode upNode = node(upPriv, port, "up-node");
        Settings upSettings = mock(Settings.class);
        when(upSettings.getMainFid()).thenReturn(upFid);
        FapiServer upstream = new FapiServer(upSettings);
        upstream.setFudpNode(upNode);
        upstream.initialize();
        upstream.registerComponent(base);
        upNode.setEventListener(upstream);
        upNode.start();

        // ---- the light server: no ES, one upstream client, its own SID and dealer
        FudpNode clientNode = node(key(), port + 1, "light-client-node");
        clientNode.start();
        clientNode.addPeer(upFid, upPub, "127.0.0.1", port);
        FapiClient toUpstream = new FapiClient(clientNode, upFid, "upstream-sid");

        Service service = new Service();
        service.setId("light-sid");
        service.setDealer(dealer);
        service.setComponents(List.of("DOCK@No1_NrC7"));
        service.setPricePerKB("0.00001");
        service.setOrderViaShare("0.1");
        Settings settings = mock(Settings.class);
        when(settings.getDbDir()).thenReturn(dir.resolve("light-db").toString());
        when(settings.getMainFid()).thenReturn(dealer);
        when(settings.getSid()).thenReturn("light-sid");
        when(settings.getService()).thenReturn(service);
        when(settings.getSettingMap()).thenReturn(new HashMap<>());
        when(settings.getClient(any(ServiceType.class))).thenReturn(null);
        when(settings.getUpstreamFapiClient()).thenReturn(toUpstream);

        ChainSource chain = ChainSources.fromSettings(settings);
        assertNotNull(chain);
        assertTrue(chain.describe().startsWith("upstream"), chain.describe());
        assertEquals(200L, chain.bestBlock().getHeight());

        FapiServer light = new FapiServer(service, null, null, settings);
        light.initialize();   // first start: scans for top-ups right away
        closers.add(light::closeBalanceManager);
        closers.add(() -> light.getBlockEventDispatcher().close());

        FapiBalanceManager balances = light.getBalanceManager();
        assertNotNull(balances);
        assertEquals(50_000_000L, balances.getBalance(payer).getBalance(),
                "the payer's top-up, seen only by the upstream, is credited");
        assertEquals(150L, balances.getLastOrderScanHeight());
        assertTrue(balances.getAllChannelBalances().containsKey(via),
                "the via named in the OpReturn, read through the upstream, gets its share");
    }
}
