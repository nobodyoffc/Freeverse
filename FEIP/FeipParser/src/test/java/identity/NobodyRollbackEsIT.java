package identity;

import clients.EsClientMaker;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.google.gson.Gson;
import constants.IndicesNames;
import core.crypto.KeyTools;
import data.fchData.Freer;
import data.fchData.Nobody;
import data.fchData.OpReturn;
import data.feipData.Feip;
import data.feipData.FreerHist;
import data.feipData.NobodyOpData;
import data.feipData.RepuHist;
import org.junit.jupiter.api.*;
import startFEIP.FileParser;
import startFEIP.IndicesFEIP;
import utils.EsUtils;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FEIP4 announcements by third parties, applied and rolled back against a real Elasticsearch.
 * Like PersonalRollbackEsIT it recreates every FEIP index and the freer index, so it only runs
 * against the throwaway cluster {@value #SCRATCH_CLUSTER} (-Dfeip.it.host / -Dfeip.it.port,
 * default 127.0.0.1:9291).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class NobodyRollbackEsIT {

    static final String SCRATCH_CLUSTER = "feip-it-scratch";
    private static final String LEAKED_KEY = "a048f6c843f92bfe036057f7fc2bf2c27353c624cf7ad97e98ed41432f700575";
    private static final String LEAKED = fidOf(LEAKED_KEY);
    private static final String ANNOUNCER = fidOf("1".repeat(64));
    private static final String SECOND = fidOf("2".repeat(64));

    private ElasticsearchClient es;
    private final IdentityParser parser = new IdentityParser();
    private int txIndex;

    private static String fidOf(String prikey) {
        return KeyTools.pubkeyToFchAddr(KeyTools.prikeyToPubkey(prikey));
    }

    @BeforeAll
    void connect() {
        String host = System.getProperty("feip.it.host", "127.0.0.1");
        int port = Integer.parseInt(System.getProperty("feip.it.port", "9291"));
        try {
            es = new EsClientMaker().getClientHttp(host, port);
            String cluster = es.info().clusterName();
            Assumptions.assumeTrue(SCRATCH_CLUSTER.equals(cluster),
                    "Refusing to run: cluster is '" + cluster + "', not the scratch cluster " + SCRATCH_CLUSTER);
        } catch (Exception e) {
            Assumptions.abort("No scratch Elasticsearch at " + host + ":" + port + ": " + e.getMessage());
        }
    }

    @BeforeEach
    void freshIndices() throws Exception {
        IndicesFEIP.deleteAllIndices(es);
        IndicesFEIP.createAllIndices(es);
        EsUtils.deleteIndex(es, IndicesNames.FREER);
        es.indices().create(c -> c.index(IndicesNames.FREER).withJson(new StringReader(
                "{\"mappings\":{\"properties\":{\"id\":{\"type\":\"wildcard\"},\"prikey\":{\"type\":\"keyword\"},"
                        + "\"noticeFee\":{\"type\":\"keyword\"},\"reputation\":{\"type\":\"long\"},\"hot\":{\"type\":\"long\"},"
                        + "\"cd\":{\"type\":\"long\"},\"cdd\":{\"type\":\"long\"},\"weight\":{\"type\":\"long\"},"
                        + "\"balance\":{\"type\":\"long\"},\"lastHeight\":{\"type\":\"long\"}}}}")));
        for (String fid : new String[]{LEAKED, ANNOUNCER, SECOND}) {
            Freer f = new Freer();
            f.setId(fid);
            f.setBalance(5000L);
            es.index(i -> i.index(IndicesNames.FREER).id(fid).document(f));
        }
        txIndex = 0;
    }

    private OpReturn opre(String txid, long height, String signer, String sn, Object data) {
        Feip feip = new Feip();
        feip.setType("FEIP");
        feip.setSn(sn);
        feip.setVer("1");
        feip.setData(data);
        OpReturn opre = new OpReturn();
        opre.setOpReturn(new Gson().toJson(feip));
        opre.setId(txid);
        opre.setHeight(height);
        opre.setTxIndex(txIndex++);
        opre.setTime(1_700_000_000L + height);
        opre.setSigner(signer);
        return opre;
    }

    private boolean apply(FreerHist hist) throws Exception {
        assertNotNull(hist, "op was malformed");
        boolean valid = parser.parseCidInfo(es, hist);
        if (valid) es.index(i -> i.index(IndicesNames.FREER_HISTORY).id(hist.getId()).document(hist));
        return valid;
    }

    private boolean announce(String txid, long height, String signer, String key) throws Exception {
        OpReturn o = opre(txid, height, signer, "4", NobodyOpData.makeNobody(key));
        return apply(parser.makeNobody(o, FileParser.parseFeip(o)));
    }

    private boolean noticeFee(String txid, long height, String signer, String fee) throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("noticeFee", fee);
        OpReturn o = opre(txid, height, signer, "10", data);
        return apply(parser.makeNoticeFee(o, FileParser.parseFeip(o)));
    }

    private void rollbackTo(long height) throws Exception {
        es.indices().refresh();
        assertFalse(new IdentityRollbacker().rollback(es, height), "rollback reported an error");
        es.indices().refresh();
    }

    private Freer freer(String id) throws Exception {
        return EsUtils.getById(es, IndicesNames.FREER, id, Freer.class);
    }

    private Nobody nobody(String id) throws Exception {
        return EsUtils.getById(es, IndicesNames.NOBODY, id, Nobody.class);
    }

    @Test
    void anyoneCanAnnounceALeakedKey() throws Exception {
        assertTrue(announce("t1", 100, ANNOUNCER, LEAKED_KEY));

        assertEquals(LEAKED_KEY, freer(LEAKED).getPrikey());
        assertNull(freer(ANNOUNCER).getPrikey());
        Nobody n = nobody(LEAKED);
        assertNotNull(n);
        assertEquals(ANNOUNCER, n.getAnnouncer());
        assertEquals(100L, n.getLeakHeight());
        assertNull(nobody(ANNOUNCER));
    }

    @Test
    void theFirstAnnouncementWins() throws Exception {
        assertTrue(announce("t1", 100, ANNOUNCER, LEAKED_KEY));
        assertFalse(announce("t2", 110, SECOND, LEAKED_KEY));
        assertFalse(announce("t3", 120, LEAKED, LEAKED_KEY));
        assertEquals(ANNOUNCER, nobody(LEAKED).getAnnouncer());
        assertEquals("t1", nobody(LEAKED).getLeakTxId());
    }

    @Test
    void rolledBackAnnouncementIsUndone() throws Exception {
        assertTrue(announce("t1", 200, ANNOUNCER, LEAKED_KEY));

        rollbackTo(150);

        assertNull(freer(LEAKED).getPrikey());
        assertNull(nobody(LEAKED));
        assertEquals(5000L, freer(LEAKED).getBalance());
        assertEquals(0, es.count(c -> c.index(IndicesNames.FREER_HISTORY)).count());
        // and it can be announced again on the new chain
        assertTrue(announce("t2", 160, SECOND, LEAKED_KEY));
        assertEquals(SECOND, nobody(LEAKED).getAnnouncer());
    }

    @Test
    void announcementBelowTheRollbackSurvivesIt() throws Exception {
        assertTrue(announce("t1", 100, ANNOUNCER, LEAKED_KEY));
        // whoever holds the key now acts as the leaked FID
        assertTrue(noticeFee("n1", 200, LEAKED, "0.5"));
        // and the announcer does something of its own
        assertTrue(noticeFee("n2", 210, ANNOUNCER, "0.1"));

        rollbackTo(150);

        assertEquals(LEAKED_KEY, freer(LEAKED).getPrikey());
        assertNull(freer(LEAKED).getNoticeFee());
        assertNotNull(nobody(LEAKED));
        assertEquals(ANNOUNCER, nobody(LEAKED).getAnnouncer());
        assertNull(freer(ANNOUNCER).getNoticeFee());
        assertNull(freer(ANNOUNCER).getPrikey());
        assertNull(nobody(ANNOUNCER));
    }

    @Test
    void manualReparseOfTheLeakedFidRebuildsItsNobody() throws Exception {
        assertTrue(announce("t1", 100, ANNOUNCER, LEAKED_KEY));
        es.indices().refresh();

        new FileParser().reparseIdList(es, IndicesNames.FREER, new java.util.ArrayList<>(java.util.List.of(LEAKED)));

        assertEquals(LEAKED_KEY, freer(LEAKED).getPrikey());
        assertEquals(ANNOUNCER, nobody(LEAKED).getAnnouncer());
    }

    @Test
    void rollbackOfAFreerOpKeepsReputationFromBelowIt() throws Exception {
        RepuHist r = new RepuHist();
        r.setId("r1");
        r.setHeight(50L);
        r.setRater(SECOND);
        r.setRatee(LEAKED);
        r.setReputation(7L);
        r.setHot(7L);
        es.index(i -> i.index(IndicesNames.REPUTATION_HISTORY).id("r1").document(r));
        Map<String, Object> doc = new HashMap<>();
        doc.put("reputation", 7L);
        doc.put("hot", 7L);
        es.update(u -> u.index(IndicesNames.FREER).id(LEAKED).doc(doc), Freer.class);

        assertTrue(noticeFee("n1", 200, LEAKED, "0.5"));
        rollbackTo(150);

        assertEquals(7L, freer(LEAKED).getReputation());
        assertEquals(7L, freer(LEAKED).getHot());
    }

    @Test
    void nullNoticeFeeClearsTheOldOneAndKeepsChainFields() throws Exception {
        assertTrue(noticeFee("n1", 100, ANNOUNCER, "0.5"));
        assertEquals("0.5", freer(ANNOUNCER).getNoticeFee());

        assertTrue(noticeFee("n2", 110, ANNOUNCER, null));

        assertNull(freer(ANNOUNCER).getNoticeFee());
        assertEquals(110L, freer(ANNOUNCER).getLastHeight());
        assertEquals(5000L, freer(ANNOUNCER).getBalance());
    }

    @Test
    void nullFieldOnAFidWithNoFreerUpsertsTheRest() throws Exception {
        String fresh = fidOf("3".repeat(64));
        assertTrue(noticeFee("n1", 100, fresh, null));
        assertEquals(fresh, freer(fresh).getId());
        assertEquals(100L, freer(fresh).getLastHeight());
    }

    @Test
    void anAnnouncedFidNoLongerSignsAndARollbackRestoresIt() throws Exception {
        assertFalse(FileParser.signerIsNobody(es, LEAKED));
        assertTrue(announce("t1", 200, ANNOUNCER, LEAKED_KEY));
        es.indices().refresh();

        assertTrue(FileParser.signerIsNobody(es, LEAKED));
        assertFalse(FileParser.signerIsNobody(es, ANNOUNCER), "announcing a key does not make the announcer a Nobody");

        rollbackTo(150);
        assertFalse(FileParser.signerIsNobody(es, LEAKED));
    }

    @Test
    void signerCheckWorksBeforeTheNobodyIndexHasAnyDocument() throws Exception {
        assertFalse(FileParser.signerIsNobody(es, ANNOUNCER));
        assertFalse(FileParser.signerIsNobody(es, null));
    }

    @Test
    void addNewMappingsMapsFidOnAnOldIndex() throws Exception {
        EsUtils.deleteIndex(es, IndicesNames.FREER_HISTORY);
        es.indices().create(c -> c.index(IndicesNames.FREER_HISTORY).withJson(new StringReader(
                "{\"mappings\":{\"properties\":{\"signer\":{\"type\":\"wildcard\"},\"height\":{\"type\":\"long\"}}}}")));
        IndicesFEIP.addNewMappings(es);
        var mapping = es.indices().getMapping(m -> m.index(IndicesNames.FREER_HISTORY))
                .get(IndicesNames.FREER_HISTORY).mappings().properties().get("fid");
        assertNotNull(mapping);
        assertTrue(mapping.isKeyword());
    }
}
